const wppconnect = require('@wppconnect-team/wppconnect');
const path = require('path');
const fs = require('fs');
const { execSync } = require('child_process');
const whatsappBot = require('./whatsappBot');
const configRepo = require('../repositories/configRepository');
const { isMensagemAtendimento, normalizeChatId, getMessageText, extractTelefone, isChatIdValido } = require('../utils/whatsappChat');

let client = null;
let io = null;
let status = 'desconectado';
let pairingCode = null;
let reconnectTimer = null;
let initializing = false;
let initializingSince = 0;
let listenersClient = null;
let pollTimer = null;
let pollInitialized = false;
const processedMsgIds = new Set();
const lastSeenTimeByChat = new Map();

const SESSION_NAME = process.env.SESSION_NAME || 'iona-salgados';
const TOKENS_PATH = path.join(__dirname, '../../tokens');
// Fora da pasta da sessão — sobrevive a removeSessionData() e a restart do serviço.
const RATE_LIMIT_FILE = path.join(TOKENS_PATH, '.pairing-rate-limit.json');
const RATE_LIMIT_MINUTES = 60;

function normalizePhone(phone) {
  let clean = String(phone || '').replace(/\D/g, '');
  if (clean.length >= 10 && clean.length <= 11 && !clean.startsWith('55')) {
    clean = `55${clean}`;
  }
  return clean;
}

function getPhoneNumber() {
  return normalizePhone(
    configRepo.getConfig('whatsapp') ||
    configRepo.getConfig('telefone') ||
    ''
  );
}

function killStaleBrowser(sessionDir) {
  try {
    if (process.platform === 'win32') {
      const escaped = sessionDir.replace(/\\/g, '\\\\');
      execSync(
        `powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*${escaped}*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"`,
        { stdio: 'ignore', timeout: 8000 }
      );
    } else {
      execSync(`pkill -9 -f "${sessionDir}"`, { stdio: 'ignore', timeout: 8000 });
    }
  } catch (_) { /* nenhum processo órfão para encerrar */ }
}

function clearBrowserLock() {
  const sessionDir = path.join(TOKENS_PATH, SESSION_NAME);

  killStaleBrowser(sessionDir);

  ['SingletonLock', 'SingletonCookie', 'SingletonSocket'].forEach((file) => {
    try {
      const filePath = path.join(sessionDir, file);
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    } catch (_) { }
  });
}

// Remove toda a sessão salva (tokens + perfil do navegador) para forçar
// um pareamento novo. Usado ao desconectar ou trocar de número.
function removeSessionData() {
  const sessionDir = path.join(TOKENS_PATH, SESSION_NAME);
  killStaleBrowser(sessionDir);
  try {
    if (fs.existsSync(sessionDir)) {
      fs.rmSync(sessionDir, { recursive: true, force: true });
    }
  } catch (err) {
    console.error('Erro ao limpar sessão:', err.message);
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Impede que qualquer promessa (close/logout/create) trave o fluxo pra sempre.
function withTimeout(promise, ms, label) {
  return Promise.race([
    Promise.resolve(promise),
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`timeout: ${label}`)), ms)
    )
  ]);
}

// Fecha o cliente atual sem nunca travar. clearBrowserLock() em seguida
// garante que o processo do navegador realmente morra.
async function closeClientSafe(useLogout) {
  stopMessagePolling();
  listenersClient = null;
  pollInitialized = false;
  lastSeenTimeByChat.clear();
  processedMsgIds.clear();

  if (!client) return;
  const c = client;
  client = null;
  if (useLogout) {
    try { await withTimeout(c.logout(), 10000, 'logout'); } catch (_) { }
  }
  try { await withTimeout(c.close(), 8000, 'close'); } catch (_) { }
}

function getMessageTime(message) {
  return Number(message?.t || message?.timestamp || 0);
}

function getMessageId(message) {
  const id = typeof message?.id === 'object' ? message.id?.id : message.id;
  if (id) return String(id);
  return `${normalizeChatId(message?.from || message?.chatId)}:${getMessageTime(message)}:${getMessageText(message)}`;
}

function shouldSkipProcessed(message) {
  const msgId = getMessageId(message);
  if (processedMsgIds.has(msgId)) return true;
  processedMsgIds.add(msgId);
  if (processedMsgIds.size > 2000) {
    processedMsgIds.delete(processedMsgIds.values().next().value);
  }
  return false;
}

async function processIncomingMessage(message, source = 'event') {
  if (message.isNewMsg === false && source === 'event') return;
  if (shouldSkipProcessed(message)) return;
  if (!isMensagemAtendimento(message)) return;

  const chatId = normalizeChatId(message.from || message.chatId);
  const telefone = extractTelefone(chatId);
  const conteudo = getMessageText(message);
  if (!chatId) return;

  console.log(`WhatsApp msg (${source}) de ${chatId}: ${conteudo.substring(0, 50)}`);

  whatsappBot.init(client, io);
  if (status !== 'conectado') status = 'conectado';
  await whatsappBot.processarMensagem(telefone, conteudo, chatId);
  if (io) {
    io.emit('novaMensagem', {
      telefone: telefone || chatId,
      conteudo,
      direcao: 'entrada',
      timestamp: new Date().toISOString()
    });
  }
}

async function pollIncomingMessages() {
  if (!client || status !== 'conectado') return;

  let chats = [];
  try {
    chats = await client.listChats({ count: 50, onlyUsers: true });
  } catch (err) {
    console.error('Erro ao listar chats WhatsApp:', err.message);
    return;
  }

  for (const chat of chats || []) {
    const chatId = normalizeChatId(chat.id?._serialized || chat.id);
    if (!isChatIdValido(chatId)) continue;

    let msgs = [];
    try {
      msgs = await client.getAllMessagesInChat(chatId, false, false);
    } catch (_) {
      continue;
    }

    const incoming = (msgs || []).filter((m) => !m.fromMe);
    if (!incoming.length) continue;

    const lastIncoming = incoming[incoming.length - 1];
    const lastTime = getMessageTime(lastIncoming);
    const prevTime = lastSeenTimeByChat.get(chatId) || 0;

    if (!pollInitialized) {
      lastSeenTimeByChat.set(chatId, lastTime);
      continue;
    }

    const novas = incoming.filter((m) => getMessageTime(m) > prevTime);
    if (!novas.length) continue;

    lastSeenTimeByChat.set(chatId, getMessageTime(novas[novas.length - 1]));

    for (const msg of novas) {
      try {
        await processIncomingMessage(msg, 'poll');
      } catch (err) {
        console.error('Erro ao processar mensagem (poll):', err.message, chatId);
      }
    }
  }

  pollInitialized = true;
}

function startMessagePolling() {
  if (pollTimer) return;
  pollTimer = setInterval(() => {
    pollIncomingMessages().catch((err) => {
      console.error('Erro no poll WhatsApp:', err.message);
    });
  }, 4000);
  console.log('Poll de mensagens WhatsApp ativo (4s)');
}

function stopMessagePolling() {
  if (!pollTimer) return;
  clearInterval(pollTimer);
  pollTimer = null;
}

function attachMessageHandler() {
  if (!client) return;
  if (listenersClient === client) return;
  listenersClient = client;

  const handleIncoming = async (message) => {
    try {
      await processIncomingMessage(message, 'event');
    } catch (err) {
      const chatId = normalizeChatId(message?.from || message?.chatId);
      console.error('Erro ao processar mensagem:', err.message, chatId);
    }
  };

  client.onMessage(handleIncoming);
  if (typeof client.onAnyMessage === 'function') {
    client.onAnyMessage(handleIncoming);
  }
  console.log('Listeners WhatsApp registrados (onMessage + onAnyMessage)');
  startMessagePolling();
}

let pairingLock = Promise.resolve();
let pairingBusy = false;
let pairingCodeRequested = false;
let lastError = null;
let rateLimitedUntil = 0;

function formatPairingCode(code) {
  const clean = String(code).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  if (clean.length === 8) return `${clean.slice(0, 4)}-${clean.slice(4)}`;
  return clean;
}

function isWaitingForPairing() {
  return !!pairingCode || status === 'aguardando_codigo' || pairingCodeRequested;
}

function loadRateLimit() {
  try {
    if (!fs.existsSync(RATE_LIMIT_FILE)) return;
    const data = JSON.parse(fs.readFileSync(RATE_LIMIT_FILE, 'utf8'));
    const until = Number(data?.until || 0);
    if (until > Date.now()) {
      rateLimitedUntil = until;
      applyRateLimitedStatus();
      console.warn(
        `Rate limit WhatsApp ativo até ${new Date(until).toLocaleString('pt-BR')} ` +
        `(~${Math.ceil((until - Date.now()) / 60000)} min). Sem nova tentativa de código.`
      );
    } else if (until > 0) {
      clearRateLimitFile();
    }
  } catch (err) {
    console.warn('Não foi possível ler rate limit salva:', err.message);
  }
}

function saveRateLimit() {
  try {
    if (!fs.existsSync(TOKENS_PATH)) fs.mkdirSync(TOKENS_PATH, { recursive: true });
    fs.writeFileSync(
      RATE_LIMIT_FILE,
      JSON.stringify({ until: rateLimitedUntil, savedAt: Date.now() }, null, 2)
    );
  } catch (err) {
    console.warn('Não foi possível salvar rate limit:', err.message);
  }
}

function clearRateLimitFile() {
  rateLimitedUntil = 0;
  try {
    if (fs.existsSync(RATE_LIMIT_FILE)) fs.unlinkSync(RATE_LIMIT_FILE);
  } catch (_) { }
}

function emitPairingCode(code, phoneNumber) {
  if (!code) return;
  if (pairingCode) {
    console.log(`Código extra ignorado (${code}) — ativo: ${pairingCode}`);
    return;
  }
  pairingCode = formatPairingCode(code);
  status = 'aguardando_codigo';
  lastError = null;
  console.log('\n========================================');
  console.log('  CÓDIGO WHATSAPP:', pairingCode);
  console.log('  Digite no celular em até 2 minutos (não gere outro código)');
  console.log('  WhatsApp > Aparelhos conectados > Conectar aparelho');
  console.log('========================================\n');
  if (io) {
    io.emit('statusWhatsApp', getStatus());
    io.emit('codigoWhatsApp', { code: pairingCode, phoneNumber });
  }
}

function isRateLimited() {
  if (rateLimitedUntil > Date.now()) return true;
  if (rateLimitedUntil > 0) clearRateLimitFile();
  return false;
}

function applyRateLimitedStatus() {
  const minutes = Math.max(1, Math.ceil((rateLimitedUntil - Date.now()) / 60000));
  status = 'erro_pareamento';
  lastError =
    `WhatsApp limitou as tentativas de pareamento (CompanionHelloError/429). ` +
    `Aguarde cerca de ${minutes} minutos e tente de novo. ` +
    `Não reinicie o serviço nem aperte Gerar código — isso piora o bloqueio.`;
  console.error(lastError);
  if (io) io.emit('statusWhatsApp', getStatus());
}

function markRateLimited(minutes = RATE_LIMIT_MINUTES) {
  rateLimitedUntil = Date.now() + minutes * 60 * 1000;
  saveRateLimit();
  applyRateLimitedStatus();
  // Para o browser para não gerar mais tráfego de pareamento.
  closeClientSafe(false).catch(() => { });
}

loadRateLimit();

async function waitForAuthReady(timeoutMs = 45000) {
  if (!client?.page) return false;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (!client?.page || client.page.isClosed()) return false;
    try {
      const state = await client.page.evaluate(async () => {
        const ready = !!window.WPP?.isReady;
        const registered = !!(window.WPP?.conn?.isRegistered?.());
        let auth = null;
        try {
          auth = ready ? await window.WPP.conn.getAuthCode() : null;
        } catch (_) { }
        return { ready, registered, hasAuth: !!auth };
      });
      if (state.registered) return false;
      if (state.ready && state.hasAuth) return true;
    } catch (_) { }
    await sleep(1500);
  }
  return false;
}

async function requestPairingCode(phoneNumber) {
  if (!client?.page || !phoneNumber || pairingCode || pairingCodeRequested) return pairingCode;
  if (client.page.isClosed()) return null;
  if (isRateLimited()) {
    applyRateLimitedStatus();
    return null;
  }

  pairingCodeRequested = true;

  try {
    console.log('Aguardando tela de pareamento (QR/auth) ficar pronta...');
    const ready = await waitForAuthReady(60000);
    if (!ready) {
      pairingCodeRequested = false;
      console.warn('Tela de pareamento não ficou pronta — abortando geração do código');
      lastError = 'Tela de pareamento ainda não está pronta. Aguarde 1 minuto e tente de novo.';
      status = 'aguardando_codigo';
      if (io) io.emit('statusWhatsApp', getStatus());
      return null;
    }

    if (!client?.page || client.page.isClosed()) {
      pairingCodeRequested = false;
      return null;
    }

    console.log('Gerando código de pareamento (uma única vez)...');
    const result = await client.page.evaluate(async (phone) => {
      try {
        if (!window.WPP?.conn?.genLinkDeviceCodeForPhoneNumber) {
          return { error: 'API genLinkDeviceCodeForPhoneNumber indisponível' };
        }
        const code = await WPP.conn.genLinkDeviceCodeForPhoneNumber(phone);
        return { code: code ? String(code) : null };
      } catch (e) {
        const msg = (e && (e.message || (e.toString && e.toString()))) || 'erro desconhecido';
        const raw = (() => {
          try { return JSON.stringify(e); } catch (_) { return String(e); }
        })();
        return { error: msg, raw };
      }
    }, phoneNumber);

    if (result?.error) {
      pairingCodeRequested = false;
      console.error('Erro ao gerar código de pareamento:', result.error, result.raw || '');
      const blob = `${result.error} ${result.raw || ''}`.toLowerCase();
      if (blob.includes('companionhello') || blob.includes('rate-overlimit') || blob.includes('429')) {
        markRateLimited(RATE_LIMIT_MINUTES);
      } else {
        lastError = result.error;
        status = 'erro_pareamento';
        if (io) io.emit('statusWhatsApp', getStatus());
      }
      return null;
    }
    if (result?.code) {
      emitPairingCode(result.code, phoneNumber);
      return pairingCode;
    }
    pairingCodeRequested = false;
    console.warn('genLinkDeviceCodeForPhoneNumber retornou vazio');
    return null;
  } catch (err) {
    pairingCodeRequested = false;
    console.error('Erro ao gerar código de pareamento:', err.message || err);
    const blob = String(err.message || err).toLowerCase();
    if (blob.includes('companionhello') || blob.includes('rate-overlimit') || blob.includes('429')) {
      markRateLimited(RATE_LIMIT_MINUTES);
    }
    return null;
  }
}

function ensurePairingCode(phoneNumber) {
  if (!phoneNumber || pairingCode || pairingCodeRequested || status === 'conectado' || pairingBusy) {
    return Promise.resolve(pairingCode);
  }
  if (isRateLimited()) {
    applyRateLimitedStatus();
    return Promise.resolve(null);
  }

  pairingBusy = true;
  const job = pairingLock.then(async () => {
    if (pairingCode || status === 'conectado') return pairingCode;
    console.log('Solicitando código de pareamento...');
    return requestPairingCode(phoneNumber);
  }).finally(() => {
    pairingBusy = false;
  });

  pairingLock = job.catch(() => { });
  return job;
}

async function initWhatsApp(socketIo, options = {}) {
  io = socketIo;
  const forceFresh = options.forceFresh === true;
  const phoneNumber = getPhoneNumber();

  if (!phoneNumber) {
    status = 'sem_telefone';
    console.warn('Configure o número do WhatsApp no app (tela WhatsApp ou Configurações)');
    if (io) io.emit('statusWhatsApp', { status, message: 'Configure o número do WhatsApp' });
    return null;
  }

  // Não abre browser / não pede código enquanto o 429 estiver ativo.
  if (isRateLimited()) {
    applyRateLimitedStatus();
    return null;
  }

  if (forceFresh) {
    removeSessionData();
  } else {
    clearBrowserLock();
  }

  try {
    // NÃO passar phoneNumber no create — bug do WppConnect: loginByCode roda
    // várias vezes e invalida o código antes de digitar no celular.
    // Geramos UMA vez via ensurePairingCode após a tela de auth pronta.
    client = await wppconnect.create({
      session: SESSION_NAME,
      tokenStore: 'file',
      folderNameToken: TOKENS_PATH,
      whatsappVersion: '2.3000.1045290919-alpha',
      headless: true,
      devtools: false,
      useChrome: false,
      debug: false,
      logQR: false,
      waitForLogin: false,
      autoClose: 0,
      deviceSyncTimeout: 0,
      puppeteerOptions: {
        protocolTimeout: 120000
      },
      browserArgs: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-accelerated-2d-canvas',
        '--disable-gpu'
      ],
      catchLinkCode: (code) => {
        // Ignorado de propósito — evita sobrescrever o código único.
        if (!pairingCode) console.log('catchLinkCode ignorado (fluxo manual único):', code);
      },
      statusFind: (statusSession) => {
        console.log('Status WhatsApp:', statusSession);

        if (statusSession === 'browserClose' && isWaitingForPairing()) {
          lastError = 'Sessão de pareamento fechou no servidor. Gere um novo código.';
          status = 'erro_pareamento';
          if (io) io.emit('statusWhatsApp', getStatus());
          return;
        }

        if (['isLogged', 'qrReadSuccess', 'chatsAvailable'].includes(statusSession)) {
          status = 'conectado';
          pairingCode = null;
          pairingCodeRequested = false;
          lastError = null;
          clearRateLimitFile();
          whatsappBot.init(client, io);
          attachMessageHandler();
          if (io) io.emit('statusWhatsApp', getStatus());
          return;
        }

        if (statusSession === 'inChat') {
          if (status === 'conectado') {
            if (io) io.emit('statusWhatsApp', getStatus());
            return;
          }
          // inChat prematuro com sessão unpaired — NÃO marcar como conectado
          if (status !== 'erro_pareamento') {
            status = 'aguardando_codigo';
          }
          if (io) io.emit('statusWhatsApp', getStatus());
          return;
        }

        if (status === 'erro_pareamento') {
          if (io) io.emit('statusWhatsApp', getStatus());
          return;
        }

        if (pairingCode) {
          status = 'aguardando_codigo';
        } else if (statusSession === 'notLogged' || statusSession === 'disconnectedMobile') {
          status = 'aguardando_codigo';
        } else if (statusSession !== 'autocloseCalled' && statusSession !== 'browserClose') {
          status = statusSession;
        }

        if (io) io.emit('statusWhatsApp', getStatus());
      }
    });

    client.onStateChange((state) => {
      console.log('Estado WhatsApp:', state);
      if (state === 'CONNECTED' && status === 'conectado') {
        whatsappBot.init(client, io);
        attachMessageHandler();
      }
      if (state === 'UNPAIRED' || state === 'UNLAUNCHED') {
        if (status === 'conectado') {
          status = 'aguardando_codigo';
          pairingCode = null;
          if (io) io.emit('statusWhatsApp', getStatus());
        }
      }
      if (state === 'CONFLICT' && !isRateLimited() && !isWaitingForPairing()) {
        scheduleReconnect();
      }
    });

    if (status !== 'conectado' && !pairingCode && status !== 'erro_pareamento') {
      await ensurePairingCode(phoneNumber);
    }

    if (status !== 'conectado' && status !== 'erro_pareamento') {
      status = pairingCode ? 'aguardando_codigo' : status;
      if (!pairingCode && !lastError) {
        lastError = 'Código ainda não chegou. Aguarde 1 minuto e tente de novo.';
      }
    }

    console.log('WhatsApp iniciado — aguardando pareamento ou sessão ativa');
    if (io) io.emit('statusWhatsApp', getStatus());
    return client;
  } catch (err) {
    console.error('Erro ao conectar WhatsApp:', err.message);
    if (pairingCode) {
      status = 'aguardando_codigo';
      if (io) io.emit('statusWhatsApp', getStatus());
      return client;
    }
    if (status === 'erro_pareamento') {
      if (io) io.emit('statusWhatsApp', getStatus());
      return client;
    }
    status = 'erro';
    lastError = err.message;
    if (io) io.emit('statusWhatsApp', getStatus());
    if (!options.skipAutoReconnect && !isRateLimited()) scheduleReconnect();
    throw err;
  }
}

function scheduleReconnect() {
  if (reconnectTimer || initializing || isRateLimited() || isWaitingForPairing()) return;
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null;
    if (isRateLimited()) return;
    console.log('Tentando reconectar WhatsApp...');
    try {
      await reconectar();
    } catch (_) {
      if (!isRateLimited()) scheduleReconnect();
    }
  }, 30000);
}

function getStatus() {
  const rateLimitedMs = Math.max(0, rateLimitedUntil - Date.now());
  return {
    status,
    session: SESSION_NAME,
    pairingCode,
    phoneNumber: getPhoneNumber(),
    message: lastError || null,
    rateLimitedUntil: rateLimitedMs > 0 ? rateLimitedUntil : null,
    rateLimitedMinutes: rateLimitedMs > 0 ? Math.ceil(rateLimitedMs / 60000) : null
  };
}

function getClient() {
  return client;
}

async function reconectar(telefone) {
  if (telefone) {
    const clean = normalizePhone(telefone);
    configRepo.setConfig('whatsapp', clean);
  }

  const phoneNumber = getPhoneNumber();
  if (!phoneNumber) {
    status = 'sem_telefone';
    return getStatus();
  }

  if (isRateLimited()) {
    applyRateLimitedStatus();
    return getStatus();
  }

  // Se uma inicialização anterior ficou presa por muito tempo, libera a trava.
  if (initializing && Date.now() - initializingSince > 90000) {
    console.warn('Inicialização anterior travada — forçando nova tentativa');
    initializing = false;
  }

  if (initializing) {
    console.log('Já existe uma inicialização em andamento — aguardando código...');
    await waitForPairingCode(45000);
    return getStatus();
  }

  initializing = true;
  initializingSince = Date.now();
  pairingCode = null;
  pairingCodeRequested = false;
  lastError = null;
  status = 'reconectando';
  if (io) io.emit('statusWhatsApp', getStatus());

  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  // Fecha cliente e APAGA a sessão — necessário para gerar código novo
  // depois de desconectar (mesmo número ou outro).
  await closeClientSafe(false);
  removeSessionData();
  await sleep(3000);

  try {
    await initWhatsApp(io, { forceFresh: true, skipAutoReconnect: true });
    // init já espera a tela de pareamento; aqui só consolida o resultado.
    if (status === 'erro_pareamento' || isRateLimited()) {
      return getStatus();
    }
    const code = await waitForPairingCode(20000);
    if (code) {
      status = 'aguardando_codigo';
      console.log('Código de pareamento pronto:', code);
    } else if (status !== 'conectado' && status !== 'erro_pareamento') {
      console.warn('WhatsApp iniciado, mas código ainda não chegou');
      status = 'aguardando_codigo';
    }
  } catch (err) {
    if (pairingCode) {
      status = 'aguardando_codigo';
    } else if (status !== 'erro_pareamento') {
      console.error('Erro ao reconectar:', err.message);
      status = 'erro';
      lastError = err.message;
      clearBrowserLock();
    }
  } finally {
    initializing = false;
  }

  if (io) io.emit('statusWhatsApp', getStatus());
  return getStatus();
}

async function waitForPairingCode(timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (pairingCode) return pairingCode;
    if (status === 'conectado' || status === 'erro_pareamento' || isRateLimited()) return null;
    await sleep(500);
  }
  return pairingCode;
}

async function shutdown() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  initializing = false;

  await closeClientSafe(false);
  clearBrowserLock();
}

async function desconectar() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  initializing = false;
  pairingCode = null;
  pairingCodeRequested = false;

  // Fecha sem logout longo (logout costuma travar). Apaga a sessão local
  // para o próximo "Gerar código" sempre pedir pareamento novo.
  await closeClientSafe(false);
  removeSessionData();
  await sleep(1500);

  status = 'desconectado';
  if (io) io.emit('statusWhatsApp', { status: 'desconectado', pairingCode: null });
  console.log('WhatsApp desconectado');
  return getStatus();
}

async function enviarMensagem(telefone, texto) {
  if (!client) throw new Error('WhatsApp não conectado');
  return whatsappBot.enviarMensagem(telefone, texto);
}

async function enviarMensagemDireta(telefone, texto, chatId) {
  if (!client) throw new Error('WhatsApp não conectado');
  return whatsappBot.enviarMensagem(telefone, texto, chatId);
}

async function enviarLocalizacao(telefone) {
  if (!client) throw new Error('WhatsApp não conectado');
  return whatsappBot.enviarLocalizacao(telefone);
}

module.exports = {
  initWhatsApp, getStatus, getClient, reconectar, shutdown, desconectar,
  enviarMensagem, enviarMensagemDireta, enviarLocalizacao
};

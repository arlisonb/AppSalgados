const wppconnect = require('@wppconnect-team/wppconnect');
const path = require('path');
const fs = require('fs');
const { execSync } = require('child_process');
const whatsappBot = require('./whatsappBot');
const configRepo = require('../repositories/configRepository');
const { isMensagemAtendimento } = require('../utils/whatsappChat');

let client = null;
let io = null;
let status = 'desconectado';
let pairingCode = null;
let reconnectTimer = null;
let initializing = false;
let initializingSince = 0;

const SESSION_NAME = process.env.SESSION_NAME || 'iona-salgados';
const TOKENS_PATH = path.join(__dirname, '../../tokens');

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
  if (!client) return;
  const c = client;
  client = null;
  if (useLogout) {
    try { await withTimeout(c.logout(), 10000, 'logout'); } catch (_) { }
  }
  try { await withTimeout(c.close(), 8000, 'close'); } catch (_) { }
}

function attachMessageHandler() {
  if (!client) return;
  if (client._ionaMessageHandler) return;
  client._ionaMessageHandler = true;

  client.onMessage(async (message) => {
    if (!isMensagemAtendimento(message)) return;

    const chatId = message.from || message.chatId;
    const telefone = chatId.replace(/@c\.us$|@lid$/i, '').replace(/\D/g, '');
    const conteudo = (message.body || message.caption || '').trim();
    if (!telefone || telefone === '0') return;

    console.log(`WhatsApp msg de ${chatId}: ${conteudo.substring(0, 50)}`);

    try {
      if (status !== 'conectado') {
        whatsappBot.init(client, io);
        status = 'conectado';
      }
      await whatsappBot.processarMensagem(telefone, conteudo, chatId);
      if (io) {
        io.emit('novaMensagem', {
          telefone,
          conteudo,
          direcao: 'entrada',
          timestamp: new Date().toISOString()
        });
      }
    } catch (err) {
      console.error('Erro ao processar mensagem:', err.message);
    }
  });
}

async function initWhatsApp(socketIo) {
  io = socketIo;
  const phoneNumber = getPhoneNumber();

  if (!phoneNumber) {
    status = 'sem_telefone';
    console.warn('Configure o número do WhatsApp no app (tela WhatsApp ou Configurações)');
    if (io) io.emit('statusWhatsApp', { status, message: 'Configure o número do WhatsApp' });
    return null;
  }

  clearBrowserLock();

  try {
    client = await wppconnect.create({
      session: SESSION_NAME,
      tokenStore: 'file',
      folderNameToken: TOKENS_PATH,
      phoneNumber,
      headless: true,
      devtools: false,
      useChrome: false,
      debug: false,
      logQR: false,
      waitForLogin: false,
      autoClose: 0,
      browserArgs: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-accelerated-2d-canvas',
        '--disable-gpu'
      ],
      catchLinkCode: (code) => {
        pairingCode = code;
        status = 'aguardando_codigo';
        console.log('\n========================================');
        console.log('  CÓDIGO WHATSAPP:', code);
        console.log('  WhatsApp > Aparelhos conectados > Conectar aparelho');
        console.log('========================================\n');
        if (io) {
          io.emit('statusWhatsApp', { status, pairingCode: code, phoneNumber });
          io.emit('codigoWhatsApp', { code, phoneNumber });
        }
      },
      statusFind: (statusSession) => {
        console.log('Status WhatsApp:', statusSession);

        if (['isLogged', 'inChat', 'qrReadSuccess', 'chatsAvailable'].includes(statusSession)) {
          status = 'conectado';
          pairingCode = null;
          whatsappBot.init(client, io);
          attachMessageHandler();
          if (io) io.emit('statusWhatsApp', { status: 'conectado' });
          return;
        }

        // Enquanto houver um código pendente, manter o status estável em
        // 'aguardando_codigo'. Estados intermediários (notLogged,
        // desconnectedMobile, etc.) fariam o app piscar "desconectado".
        if (pairingCode) {
          status = 'aguardando_codigo';
        } else if (statusSession === 'notLogged') {
          status = 'notLogged';
        } else if (statusSession !== 'autocloseCalled') {
          status = statusSession;
        }

        if (io) io.emit('statusWhatsApp', { status, pairingCode });
      }
    });

    client.onStateChange((state) => {
      console.log('Estado WhatsApp:', state);
      if (state === 'CONNECTED' || state === 'OPENING') {
        whatsappBot.init(client, io);
        attachMessageHandler();
      }
      if (state === 'CONFLICT') {
        scheduleReconnect();
      }
    });

    whatsappBot.init(client, io);
    attachMessageHandler();

    if (pairingCode) {
      status = 'aguardando_codigo';
    } else if (status !== 'conectado') {
      status = 'aguardando_codigo';
    }

    console.log('WhatsApp iniciado — aguardando pareamento ou sessão ativa');
    if (io) io.emit('statusWhatsApp', getStatus());
    return client;
  } catch (err) {
    console.error('Erro ao conectar WhatsApp:', err.message);
    status = 'erro';
    if (io) io.emit('statusWhatsApp', { status: 'erro', error: err.message });
    if (!pairingCode) scheduleReconnect();
    throw err;
  }
}

function scheduleReconnect() {
  if (reconnectTimer || initializing) return;
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null;
    console.log('Tentando reconectar WhatsApp...');
    try {
      await reconectar();
    } catch (_) {
      scheduleReconnect();
    }
  }, 30000);
}

function getStatus() {
  return {
    status,
    session: SESSION_NAME,
    pairingCode,
    phoneNumber: getPhoneNumber()
  };
}

function getClient() {
  return client;
}

async function reconectar(telefone) {
  const numeroAntigo = getPhoneNumber();
  let numeroMudou = false;

  if (telefone) {
    const clean = normalizePhone(telefone);
    numeroMudou = clean !== numeroAntigo;
    configRepo.setConfig('whatsapp', clean);
  }

  const phoneNumber = getPhoneNumber();
  if (!phoneNumber) {
    status = 'sem_telefone';
    return getStatus();
  }

  // Se uma inicialização anterior ficou presa por muito tempo, libera a trava.
  if (initializing && Date.now() - initializingSince > 90000) {
    console.warn('Inicialização anterior travada — forçando nova tentativa');
    initializing = false;
  }

  if (initializing) {
    await waitForPairingCode(30000);
    return getStatus();
  }

  initializing = true;
  initializingSince = Date.now();
  pairingCode = null;
  status = 'reconectando';

  // Encerra qualquer navegador da sessão atual.
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  await closeClientSafe(false);

  // Trocar de número exige apagar a sessão antiga, senão o WppConnect
  // reconecta no número anterior em vez de pedir código pro novo.
  if (numeroMudou) {
    console.log('Número alterado — limpando sessão para novo pareamento');
    removeSessionData();
  } else {
    clearBrowserLock();
  }

  await sleep(2000);

  try {
    // initWhatsApp não pode travar o ciclo indefinidamente.
    await withTimeout(initWhatsApp(io), 60000, 'initWhatsApp');
    await waitForPairingCode(45000);
  } catch (err) {
    if (pairingCode) {
      status = 'aguardando_codigo';
    } else {
      console.error('Erro ao reconectar:', err.message);
      status = 'erro';
      clearBrowserLock();
    }
  } finally {
    initializing = false;
  }

  return getStatus();
}

async function waitForPairingCode(timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (pairingCode) return pairingCode;
    if (status === 'conectado') return null;
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

  // logout invalida a sessão no WhatsApp; em seguida apagamos os dados locais
  // para que reconectar (mesmo número ou outro) faça sempre um pareamento novo.
  await closeClientSafe(true);
  removeSessionData();

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

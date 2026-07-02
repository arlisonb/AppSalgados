const wppconnect = require('@wppconnect-team/wppconnect');
const path = require('path');
const fs = require('fs');
const whatsappBot = require('./whatsappBot');
const configRepo = require('../repositories/configRepository');
const { isMensagemAtendimento } = require('../utils/whatsappChat');

let client = null;
let io = null;
let status = 'desconectado';
let pairingCode = null;
let reconnectTimer = null;
let initializing = false;

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
    process.env.WHATSAPP_PHONE ||
    configRepo.getConfig('whatsapp') ||
    configRepo.getConfig('telefone') ||
    ''
  );
}

function clearBrowserLock() {
  const sessionDir = path.join(TOKENS_PATH, SESSION_NAME);
  ['SingletonLock', 'SingletonCookie', 'SingletonSocket'].forEach((file) => {
    try {
      const filePath = path.join(sessionDir, file);
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    } catch (_) { }
  });
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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
    console.warn('Configure WHATSAPP_PHONE no .env ou telefone nas configurações');
    if (io) io.emit('statusWhatsApp', { status, message: 'Configure o número do WhatsApp' });
    return null;
  }

  try {
    client = await wppconnect.create({
      session: SESSION_NAME,
      tokenStore: 'file',
      folderNameToken: TOKENS_PATH,
      phoneNumber,
      headless: true,
      devtools: false,
      useChrome: true,
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

        if (statusSession === 'notLogged') {
          status = pairingCode ? 'aguardando_codigo' : 'notLogged';
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
  if (telefone) {
    const clean = normalizePhone(telefone);
    process.env.WHATSAPP_PHONE = clean;
    configRepo.setConfig('whatsapp', clean);
  }

  const phoneNumber = getPhoneNumber();
  if (!phoneNumber) {
    status = 'sem_telefone';
    return getStatus();
  }

  if (initializing) {
    await waitForPairingCode(30000);
    return getStatus();
  }

  initializing = true;
  pairingCode = null;
  status = 'reconectando';

  if (client) {
    try { await client.close(); } catch (_) { }
    client = null;
  }

  clearBrowserLock();
  await sleep(2000);

  try {
    await initWhatsApp(io);
    await waitForPairingCode(45000);
  } catch (err) {
    if (pairingCode) {
      status = 'aguardando_codigo';
    } else {
      console.error('Erro ao reconectar:', err.message);
      status = 'erro';
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

  if (client) {
    try {
      await client.close();
    } catch (_) { }
    client = null;
  }

  clearBrowserLock();
}

async function desconectar() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  initializing = false;
  pairingCode = null;

  if (client) {
    try {
      await client.logout();
    } catch (_) {
      try { await client.close(); } catch (_) { }
    }
    client = null;
  }

  clearBrowserLock();
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

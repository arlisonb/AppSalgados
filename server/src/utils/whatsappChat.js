const TIPOS_IGNORADOS = new Set([
  'notification',
  'notification_template',
  'group_notification',
  'gp2',
  'broadcast_notification',
  'e2e_notification',
  'call_log',
  'protocol',
  'revoked',
  'ciphertext'
]);

function normalizeChatId(raw) {
  if (!raw) return '';
  if (typeof raw === 'string') return raw;
  if (typeof raw === 'object') {
    if (raw._serialized) return String(raw._serialized);
    if (raw.user && raw.server) return `${raw.user}@${raw.server}`;
  }
  return String(raw);
}

function getMessageText(message) {
  return String(message?.body || message?.content || message?.caption || '').trim();
}

function isChatIdValido(chatId) {
  const id = normalizeChatId(chatId).toLowerCase();
  if (!id) return false;
  if (id === '0@c.us') return false;
  if (id.includes('status@broadcast') || id.includes('@broadcast')) return false;
  if (id.includes('newsletter')) return false;
  if (id.endsWith('@g.us')) return false;
  return /@(c\.us|lid|s\.whatsapp\.net)$/i.test(id);
}

function extractTelefone(chatId) {
  const id = normalizeChatId(chatId);
  // @lid não é número de telefone — retorna vazio para não poluir sessão/cadastro.
  if (/@lid$/i.test(id)) return '';
  return id.replace(/@c\.us$|@s\.whatsapp\.net$/i, '').replace(/\D/g, '');
}

function isMensagemAtendimento(message) {
  if (!message || message.isGroupMsg || message.fromMe) return false;
  if (message.isStatus || message.isStatusV3) return false;
  if (message.isNotification || message.isPSA) return false;

  const chatId = normalizeChatId(message.from || message.chatId);
  if (!isChatIdValido(chatId)) return false;

  const conteudo = getMessageText(message);
  if (!conteudo) return false;

  if (message.type && TIPOS_IGNORADOS.has(message.type)) return false;

  // Bloqueia mídia pura; texto (chat) e áudio transcrito seguem.
  const tiposMidia = ['image', 'video', 'sticker', 'document', 'location', 'vcard', 'multi_vcard'];
  if (message.type && tiposMidia.includes(message.type) && !message.body && !message.content) {
    return false;
  }

  if (/^\/9j\//.test(conteudo) || /^data:image\//.test(conteudo)) return false;

  return true;
}

module.exports = {
  normalizeChatId,
  getMessageText,
  extractTelefone,
  isChatIdValido,
  isMensagemAtendimento
};

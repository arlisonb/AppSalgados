function isChatIdValido(chatId) {
  if (!chatId || typeof chatId !== 'string') return false;
  const id = chatId.toLowerCase();
  if (id === '0@c.us') return false;
  if (id.includes('status@broadcast') || id.includes('@broadcast')) return false;
  if (id.includes('newsletter')) return false;
  return /@(c\.us|lid|s\.whatsapp\.net)$/i.test(chatId);
}

function isMensagemAtendimento(message) {
  if (message.isGroupMsg || message.fromMe) return false;
  if (message.isStatus || message.isStatusV3) return false;

  const chatId = message.from || message.chatId || '';
  if (!isChatIdValido(chatId)) return false;

  const conteudo = (message.body || message.caption || '').trim();
  if (!conteudo) return false;

  if (message.type && !['chat', 'ptt'].includes(message.type)) return false;
  if (/^\/9j\//.test(conteudo) || /^data:image\//.test(conteudo)) return false;

  return true;
}

module.exports = { isChatIdValido, isMensagemAtendimento };

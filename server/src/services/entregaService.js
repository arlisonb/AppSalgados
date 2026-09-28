const whatsappService = require('./whatsappService');
const whatsappBot = require('./whatsappBot');
const pedidoRepo = require('../repositories/pedidoRepository');
const configRepo = require('../repositories/configRepository');

function normalizePhone(phone) {
  let clean = String(phone || '').replace(/\D/g, '');
  if (clean.length >= 10 && clean.length <= 11 && !clean.startsWith('55')) {
    clean = `55${clean}`;
  }
  return clean;
}

function getChatId(pedido) {
  if (pedido.whatsapp_chat_id) return pedido.whatsapp_chat_id;
  const historico = pedidoRepo.findWhatsAppChatIdByCliente(pedido.cliente_id);
  if (historico) return historico;
  const tel = normalizePhone(pedido.cliente_telefone);
  return tel ? `${tel}@c.us` : null;
}

function isRetirada(pedido) {
  const obs = String(pedido?.observacoes || '');
  const end = String(pedido?.endereco || '');
  return /retirada/i.test(obs) || /^retirada\b/i.test(end);
}

function enderecoRetirada(pedido) {
  const loja = String(configRepo.getConfig('endereco') || '').trim();
  if (loja) return loja;
  return String(pedido?.endereco || '').replace(/^retirada\s*[—-]\s*/i, '').trim();
}

async function notificarPedidoPronto(pedido, socketIo) {
  const telefone = normalizePhone(pedido.cliente_telefone);
  const chatId = getChatId(pedido);

  if (!telefone || !chatId) {
    throw new Error('Cliente sem telefone cadastrado para aviso no WhatsApp');
  }

  if (!pedido.whatsapp_chat_id) {
    pedidoRepo.updateWhatsAppChatId(pedido.id, chatId);
    pedido.whatsapp_chat_id = chatId;
  }

  const endereco = enderecoRetirada(pedido);
  const texto = [
    `✅ *Pedido #${pedido.numero} está pronto!*`,
    '',
    '🏪 Pode retirar no local:',
    endereco ? `📍 ${endereco}` : '📍 Retirada no local'
  ].join('\n');

  await whatsappService.enviarMensagemDireta(telefone, texto, chatId);
  console.log(`Aviso de retirada enviado — Pedido #${pedido.numero} → ${telefone}`);
}

async function notificarSaidaEntrega(pedido, socketIo) {
  const telefone = normalizePhone(pedido.cliente_telefone);
  const chatId = getChatId(pedido);

  if (!telefone || !chatId) {
    throw new Error('Cliente sem telefone cadastrado para aviso no WhatsApp');
  }

  if (!pedido.whatsapp_chat_id) {
    pedidoRepo.updateWhatsAppChatId(pedido.id, chatId);
    pedido.whatsapp_chat_id = chatId;
  }

  const texto = [
    `🛵 *Pedido #${pedido.numero} saiu para entrega!*`,
    '',
    'Seu pedido está a caminho. Assim que receber, responda:',
    '',
    '1️⃣ Recebi o pedido',
    '2️⃣ Ainda não recebi'
  ].join('\n');

  await whatsappService.enviarMensagemDireta(telefone, texto, chatId);
  whatsappBot.iniciarConfirmacaoEntrega(telefone, pedido, chatId, socketIo);

  console.log(`Aviso de entrega enviado — Pedido #${pedido.numero} → ${telefone}`);
}

module.exports = { notificarSaidaEntrega, notificarPedidoPronto, isRetirada, normalizePhone, getChatId };

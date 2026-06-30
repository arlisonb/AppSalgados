const whatsappService = require('./whatsappService');
const whatsappBot = require('./whatsappBot');
const pedidoRepo = require('../repositories/pedidoRepository');

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

module.exports = { notificarSaidaEntrega, normalizePhone, getChatId };

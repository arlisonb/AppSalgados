const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const fcmRepo = require('../repositories/fcmRepository');

let ready = false;

function initFirebase() {
  if (ready) return true;
  if (admin.apps.length) {
    ready = true;
    return true;
  }

  const credPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH
    || path.join(__dirname, '../../firebase-service-account.json');

  if (!fs.existsSync(credPath)) {
    console.warn(`FCM: conta de serviço não encontrada em ${credPath}`);
    console.warn('FCM: baixe em Firebase → Configurações → Contas de serviço → Gerar nova chave privada');
    return false;
  }

  try {
    const serviceAccount = JSON.parse(fs.readFileSync(credPath, 'utf8'));
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    ready = true;
    console.log('FCM: Firebase Admin inicializado');
    return true;
  } catch (err) {
    console.error('FCM: erro ao inicializar Firebase Admin:', err.message);
    return false;
  }
}

function pedidoPayload(type, pedido) {
  return {
    type,
    pedido_id: String(pedido.id),
    numero: String(pedido.numero),
    cliente_nome: pedido.cliente_nome || '',
    valor_total: String(pedido.valor_total ?? 0)
  };
}

function notificationFor(type, pedido) {
  const cliente = pedido.cliente_nome || 'Cliente';
  const valor = `R$ ${Number(pedido.valor_total || 0).toFixed(2)}`;

  switch (type) {
    case 'novo_pedido':
      return {
        title: `Novo pedido #${pedido.numero}`,
        body: `${cliente} — ${valor}`
      };
    case 'pedido_recebido':
      return {
        title: `Pedido #${pedido.numero} recebido`,
        body: `${cliente} confirmou a entrega`
      };
    case 'pedido_cancelado':
      return {
        title: `Pedido #${pedido.numero} cancelado`,
        body: cliente
      };
    default:
      return { title: 'Iona Salgados', body: `Pedido #${pedido.numero}` };
  }
}

async function sendToAll(type, pedido) {
  if (!initFirebase()) return;

  const tokens = fcmRepo.findAllTokens();
  if (!tokens.length) return;

  const data = pedidoPayload(type, pedido);
  const notification = notificationFor(type, pedido);

  const message = {
    tokens,
    data,
    notification,
    android: {
      priority: 'high',
      notification: {
        channelId: type === 'pedido_recebido' ? 'entrega' : 'pedidos',
        clickAction: 'OPEN_PEDIDO'
      }
    }
  };

  try {
    const result = await admin.messaging().sendEachForMulticast(message);
    if (result.failureCount > 0) {
      result.responses.forEach((resp, i) => {
        if (!resp.success) {
          const code = resp.error?.code;
          if (code === 'messaging/registration-token-not-registered'
            || code === 'messaging/invalid-registration-token') {
            fcmRepo.removeToken(tokens[i]);
          }
        }
      });
    }
    if (result.successCount > 0) {
      console.log(`FCM: ${result.successCount} notificação(ões) enviada(s) — ${type} #${pedido.numero}`);
    }
  } catch (err) {
    console.error('FCM: erro ao enviar push:', err.message);
  }
}

function notifyNovoPedido(pedido) {
  return sendToAll('novo_pedido', pedido);
}

function notifyPedidoRecebido(pedido) {
  return sendToAll('pedido_recebido', pedido);
}

function notifyPedidoCancelado(pedido) {
  return sendToAll('pedido_cancelado', pedido);
}

module.exports = {
  initFirebase,
  notifyNovoPedido,
  notifyPedidoRecebido,
  notifyPedidoCancelado
};

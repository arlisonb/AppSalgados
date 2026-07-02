const { getDb } = require('../database/db');
const configRepo = require('./configRepository');

const STATUS_VALIDOS = [
  'novo', 'confirmado', 'preparando', 'pronto',
  'saiu_entrega', 'finalizado', 'cancelado'
];

function getProximoNumero() {
  const hoje = new Date().toISOString().split('T')[0];
  const row = getDb().prepare(`
    SELECT MAX(numero) as max_num FROM pedidos
    WHERE date(created_at) = date(?)
  `).get(hoje);
  return (row.max_num || 0) + 1;
}

function findAll(filters = {}) {
  let sql = `
    SELECT p.*, c.nome as cliente_nome, c.telefone as cliente_telefone
    FROM pedidos p
    JOIN clientes c ON c.id = p.cliente_id
    WHERE 1=1
  `;
  const params = [];

  if (filters.status) {
    sql += ' AND p.status = ?';
    params.push(filters.status);
  }
  if (filters.data) {
    sql += ' AND date(p.created_at) = date(?)';
    params.push(filters.data);
  }
  if (filters.cliente_id) {
    sql += ' AND p.cliente_id = ?';
    params.push(filters.cliente_id);
  }

  sql += ' ORDER BY p.created_at DESC';
  return getDb().prepare(sql).all(...params);
}

function findById(id) {
  const pedido = getDb().prepare(`
    SELECT p.*, c.nome as cliente_nome, c.telefone as cliente_telefone
    FROM pedidos p
    JOIN clientes c ON c.id = p.cliente_id
    WHERE p.id = ?
  `).get(id);

  if (!pedido) return null;

  pedido.itens = getDb().prepare(`
    SELECT * FROM itens_pedido WHERE pedido_id = ?
  `).all(id);

  return pedido;
}

function create(data) {
  const db = getDb();
  const valorItens = data.valor_itens || 0;
  const taxaEntrega = (data.taxa_entrega != null && data.taxa_entrega !== undefined)
    ? Number(data.taxa_entrega)
    : parseFloat(configRepo.getConfig('taxa_entrega') || '0');
  const valorTotal = (data.valor_total != null && data.valor_total !== undefined)
    ? data.valor_total
    : valorItens + taxaEntrega;

  const transaction = db.transaction(() => {
    const numero = getProximoNumero();
    const result = db.prepare(`
      INSERT INTO pedidos (
        numero, cliente_id, status, valor_itens, taxa_entrega,
        valor_total, forma_pagamento, troco, observacoes,
        endereco, origem, tempo_estimado, whatsapp_chat_id
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      numero, data.cliente_id, data.status || 'novo',
      valorItens, taxaEntrega,
      valorTotal, data.forma_pagamento || null,
      data.troco || 0, data.observacoes || null,
      data.endereco || null, data.origem || 'app',
      data.tempo_estimado || null,
      data.whatsapp_chat_id || null
    );

    const pedidoId = result.lastInsertRowid;

    if (data.itens && data.itens.length > 0) {
      const insertItem = db.prepare(`
        INSERT INTO itens_pedido (
          pedido_id, produto_id, nome_produto, quantidade,
          preco_unitario, subtotal, observacao
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
      `);

      data.itens.forEach((item) => {
        insertItem.run(
          pedidoId, item.produto_id, item.nome_produto,
          item.quantidade, item.preco_unitario, item.subtotal,
          item.observacao || null
        );
      });
    }

    return pedidoId;
  });

  const pedidoId = transaction();
  return findById(pedidoId);
}

function findWhatsAppChatIdByCliente(clienteId) {
  const row = getDb().prepare(`
    SELECT whatsapp_chat_id FROM pedidos
    WHERE cliente_id = ? AND whatsapp_chat_id IS NOT NULL AND whatsapp_chat_id != ''
    ORDER BY id DESC LIMIT 1
  `).get(clienteId);
  return row?.whatsapp_chat_id || null;
}

function updateWhatsAppChatId(id, chatId) {
  if (!chatId) return;
  getDb().prepare(`
    UPDATE pedidos SET whatsapp_chat_id = ?, updated_at = datetime('now', 'localtime')
    WHERE id = ? AND (whatsapp_chat_id IS NULL OR whatsapp_chat_id = '')
  `).run(chatId, id);
}

function updateStatus(id, status, usuario = 'sistema') {
  if (!STATUS_VALIDOS.includes(status)) {
    throw new Error(`Status inválido: ${status}`);
  }

  const pedido = findById(id);
  if (!pedido) return null;

  getDb().prepare(`
    UPDATE pedidos SET status = ?, updated_at = datetime('now', 'localtime')
    WHERE id = ?
  `).run(status, id);

  getDb().prepare(`
    INSERT INTO historico_pedidos (pedido_id, status_anterior, status_novo, usuario)
    VALUES (?, ?, ?, ?)
  `).run(id, pedido.status, status, usuario);

  return findById(id);
}

function getDashboard() {
  const hoje = new Date().toISOString().split('T')[0];
  const mesAtual = hoje.substring(0, 7);

  const pedidosHoje = getDb().prepare(`
    SELECT status, COUNT(*) as qtd, COALESCE(SUM(valor_total), 0) as valor
    FROM pedidos WHERE date(created_at) = date(?)
    GROUP BY status
  `).all(hoje);

  const resumo = {
    pedidos_hoje: 0,
    pendentes: 0,
    finalizados: 0,
    cancelados: 0,
    em_producao: 0,
    saiu_entrega: 0,
    valor_hoje: 0,
    valor_mes: 0,
    clientes_atendidos: 0,
    produto_mais_vendido: null,
    caixa_atual: 0
  };

  pedidosHoje.forEach((row) => {
    resumo.pedidos_hoje += row.qtd;
    if (['novo', 'confirmado'].includes(row.status)) resumo.pendentes += row.qtd;
    if (['preparando', 'pronto'].includes(row.status)) resumo.em_producao += row.qtd;
    if (row.status === 'saiu_entrega') resumo.saiu_entrega += row.qtd;
    if (row.status === 'finalizado') {
      resumo.finalizados += row.qtd;
      resumo.valor_hoje += row.valor;
    }
    if (row.status === 'cancelado') resumo.cancelados += row.qtd;
  });

  const mes = getDb().prepare(`
    SELECT COALESCE(SUM(valor_total), 0) as valor
    FROM pedidos
    WHERE status = 'finalizado' AND strftime('%Y-%m', created_at) = ?
  `).get(mesAtual);
  resumo.valor_mes = mes.valor;

  const clientes = getDb().prepare(`
    SELECT COUNT(DISTINCT cliente_id) as qtd
    FROM pedidos WHERE date(created_at) = date(?)
  `).get(hoje);
  resumo.clientes_atendidos = clientes.qtd;

  const produto = getDb().prepare(`
    SELECT ip.nome_produto, SUM(ip.quantidade) as total
    FROM itens_pedido ip
    JOIN pedidos p ON p.id = ip.pedido_id
    WHERE date(p.created_at) = date(?) AND p.status != 'cancelado'
    GROUP BY ip.nome_produto
    ORDER BY total DESC LIMIT 1
  `).get(hoje);
  resumo.produto_mais_vendido = produto ? produto.nome_produto : null;

  const caixa = getDb().prepare(`
    SELECT valor_inicial + total_entradas - total_saidas as saldo
    FROM caixa WHERE status = 'aberto' ORDER BY id DESC LIMIT 1
  `).get();
  resumo.caixa_atual = caixa ? caixa.saldo : 0;

  return resumo;
}

function getProducao(data) {
  const dataFiltro = data || new Date().toISOString().split('T')[0];

  const porProduto = getDb().prepare(`
    SELECT ip.nome_produto, SUM(ip.quantidade) as quantidade_total
    FROM itens_pedido ip
    JOIN pedidos p ON p.id = ip.pedido_id
    WHERE date(p.created_at) = date(?)
      AND p.status NOT IN ('cancelado', 'finalizado')
    GROUP BY ip.nome_produto
    ORDER BY quantidade_total DESC
  `).all(dataFiltro);

  const porHorario = getDb().prepare(`
    SELECT strftime('%H:00', p.created_at) as horario,
           ip.nome_produto, SUM(ip.quantidade) as quantidade
    FROM itens_pedido ip
    JOIN pedidos p ON p.id = ip.pedido_id
    WHERE date(p.created_at) = date(?)
      AND p.status NOT IN ('cancelado', 'finalizado')
    GROUP BY horario, ip.nome_produto
    ORDER BY horario, ip.nome_produto
  `).all(dataFiltro);

  const porCliente = getDb().prepare(`
    SELECT c.nome as cliente_nome, p.numero, p.status,
           ip.nome_produto, ip.quantidade
    FROM itens_pedido ip
    JOIN pedidos p ON p.id = ip.pedido_id
    JOIN clientes c ON c.id = p.cliente_id
    WHERE date(p.created_at) = date(?)
      AND p.status NOT IN ('cancelado', 'finalizado')
    ORDER BY c.nome, p.numero
  `).all(dataFiltro);

  return { porProduto, porHorario, porCliente };
}

module.exports = {
  findAll, findById, create, updateStatus,
  findWhatsAppChatIdByCliente, updateWhatsAppChatId,
  getDashboard, getProducao, STATUS_VALIDOS
};

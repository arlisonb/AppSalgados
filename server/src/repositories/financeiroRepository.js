const { getDb } = require('../database/db');

function getCaixaAberto() {
  return getDb().prepare(`
    SELECT * FROM caixa WHERE status = 'aberto' ORDER BY id DESC LIMIT 1
  `).get();
}

function abrirCaixa(valorInicial) {
  const aberto = getCaixaAberto();
  if (aberto) {
    throw new Error('Já existe um caixa aberto');
  }

  const result = getDb().prepare(`
    INSERT INTO caixa (data_abertura, valor_inicial, status)
    VALUES (datetime('now', 'localtime'), ?, 'aberto')
  `).run(valorInicial || 0);

  return getDb().prepare('SELECT * FROM caixa WHERE id = ?').get(result.lastInsertRowid);
}

function fecharCaixa(observacoes) {
  const caixa = getCaixaAberto();
  if (!caixa) {
    throw new Error('Nenhum caixa aberto');
  }

  const movimentacoes = getMovimentacoes(caixa.id);
  const entradas = movimentacoes.filter(m => m.tipo === 'entrada').reduce((s, m) => s + m.valor, 0);
  const saidas = movimentacoes.filter(m => m.tipo === 'saida').reduce((s, m) => s + m.valor, 0);
  const vendas = movimentacoes.filter(m => m.tipo === 'entrada' && m.pedido_id).reduce((s, m) => s + m.valor, 0);

  const pedidos = getDb().prepare(`
    SELECT
      COUNT(*) as total,
      SUM(CASE WHEN status = 'finalizado' THEN 1 ELSE 0 END) as finalizados,
      SUM(CASE WHEN status = 'cancelado' THEN 1 ELSE 0 END) as cancelados
    FROM pedidos
    WHERE created_at >= ? AND created_at <= datetime('now', 'localtime')
  `).get(caixa.data_abertura);

  const valorFinal = caixa.valor_inicial + entradas - saidas;

  getDb().prepare(`
    UPDATE caixa SET
      data_fechamento = datetime('now', 'localtime'),
      valor_final = ?,
      total_entradas = ?,
      total_saidas = ?,
      total_vendas = ?,
      qtd_pedidos = ?,
      qtd_cancelados = ?,
      status = 'fechado',
      observacoes = ?
    WHERE id = ?
  `).run(valorFinal, entradas, saidas, vendas, pedidos.finalizados || 0, pedidos.cancelados || 0, observacoes || null, caixa.id);

  return getDb().prepare('SELECT * FROM caixa WHERE id = ?').get(caixa.id);
}

function registrarMovimentacao(data) {
  const caixa = getCaixaAberto();
  if (!caixa) {
    throw new Error('Nenhum caixa aberto');
  }

  const result = getDb().prepare(`
    INSERT INTO movimentacoes_caixa (
      caixa_id, tipo, categoria, descricao, valor,
      forma_pagamento, pedido_id, observacao
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    caixa.id, data.tipo, data.categoria || null,
    data.descricao, data.valor, data.forma_pagamento || null,
    data.pedido_id || null, data.observacao || null
  );

  return getDb().prepare('SELECT * FROM movimentacoes_caixa WHERE id = ?').get(result.lastInsertRowid);
}

function registrarEntradaPedido(pedido) {
  return registrarMovimentacao({
    tipo: 'entrada',
    categoria: 'venda',
    descricao: `Pedido #${pedido.numero}`,
    valor: pedido.valor_total,
    forma_pagamento: pedido.forma_pagamento,
    pedido_id: pedido.id
  });
}

function getMovimentacoes(caixaId) {
  return getDb().prepare(`
    SELECT * FROM movimentacoes_caixa
    WHERE caixa_id = ?
    ORDER BY created_at DESC
  `).all(caixaId);
}

function getResumoFinanceiro() {
  const caixa = getCaixaAberto();
  const hoje = new Date().toISOString().split('T')[0];
  const mesAtual = hoje.substring(0, 7);

  const faturamentoHoje = getDb().prepare(`
    SELECT COALESCE(SUM(valor_total), 0) as total
    FROM pedidos WHERE status = 'finalizado' AND date(created_at) = date(?)
  `).get(hoje);

  const faturamentoMes = getDb().prepare(`
    SELECT COALESCE(SUM(valor_total), 0) as total
    FROM pedidos WHERE status = 'finalizado' AND strftime('%Y-%m', created_at) = ?
  `).get(mesAtual);

  const pedidosHoje = getDb().prepare(`
    SELECT COUNT(*) as total FROM pedidos WHERE date(created_at) = date(?)
  `).get(hoje);

  const pixRecebido = getDb().prepare(`
    SELECT COALESCE(SUM(valor_total), 0) as total
    FROM pedidos WHERE status = 'finalizado' AND forma_pagamento = 'PIX' AND date(created_at) = date(?)
  `).get(hoje);

  const dinheiroRecebido = getDb().prepare(`
    SELECT COALESCE(SUM(valor_total), 0) as total
    FROM pedidos WHERE status = 'finalizado' AND forma_pagamento = 'Dinheiro' AND date(created_at) = date(?)
  `).get(hoje);

  let caixaAtual = 0;
  if (caixa) {
    const movs = getMovimentacoes(caixa.id);
    const entradas = movs.filter(m => m.tipo === 'entrada').reduce((s, m) => s + m.valor, 0);
    const saidas = movs.filter(m => m.tipo === 'saida').reduce((s, m) => s + m.valor, 0);
    caixaAtual = caixa.valor_inicial + entradas - saidas;
  }

  return {
    caixa_atual: caixaAtual,
    caixa_aberto: !!caixa,
    faturamento_hoje: faturamentoHoje.total,
    faturamento_mes: faturamentoMes.total,
    pedidos_hoje: pedidosHoje.total,
    pix_recebido: pixRecebido.total,
    dinheiro_recebido: dinheiroRecebido.total
  };
}

function getRelatorio(periodo, dataInicio, dataFim) {
  let whereClause = "p.status = 'finalizado'";
  const params = [];

  if (dataInicio && dataFim) {
    whereClause += ' AND date(p.created_at) BETWEEN date(?) AND date(?)';
    params.push(dataInicio, dataFim);
  } else if (periodo === 'diario') {
    whereClause += ' AND date(p.created_at) = date(?)';
    params.push(new Date().toISOString().split('T')[0]);
  } else if (periodo === 'semanal') {
    whereClause += " AND p.created_at >= datetime('now', '-7 days', 'localtime')";
  } else if (periodo === 'mensal') {
    whereClause += " AND strftime('%Y-%m', p.created_at) = strftime('%Y-%m', 'now', 'localtime')";
  } else if (periodo === 'anual') {
    whereClause += " AND strftime('%Y', p.created_at) = strftime('%Y', 'now', 'localtime')";
  }

  const faturamento = getDb().prepare(`
    SELECT COALESCE(SUM(p.valor_total), 0) as total, COUNT(*) as qtd_pedidos
    FROM pedidos p WHERE ${whereClause}
  `).get(...params);

  const cancelados = getDb().prepare(`
    SELECT COUNT(*) as total FROM pedidos p
    WHERE p.status = 'cancelado' ${whereClause.replace("p.status = 'finalizado'", '1=1')}
  `).get(...params);

  const produtos = getDb().prepare(`
    SELECT ip.nome_produto, SUM(ip.quantidade) as quantidade, SUM(ip.subtotal) as valor
    FROM itens_pedido ip
    JOIN pedidos p ON p.id = ip.pedido_id
    WHERE ${whereClause}
    GROUP BY ip.nome_produto
    ORDER BY quantidade DESC
  `).all(...params);

  const despesas = getDb().prepare(`
    SELECT COALESCE(SUM(m.valor), 0) as total
    FROM movimentacoes_caixa m
    WHERE m.tipo = 'saida'
    ${dataInicio && dataFim ? 'AND date(m.created_at) BETWEEN date(?) AND date(?)' : ''}
  `).get(...(dataInicio && dataFim ? [dataInicio, dataFim] : []));

  const ticketMedio = faturamento.qtd_pedidos > 0
    ? faturamento.total / faturamento.qtd_pedidos
    : 0;

  return {
    faturamento: faturamento.total,
    lucro: faturamento.total - (despesas.total || 0),
    qtd_pedidos: faturamento.qtd_pedidos,
    pedidos_cancelados: cancelados.total,
    ticket_medio: ticketMedio,
    despesas: despesas.total || 0,
    saldo: faturamento.total - (despesas.total || 0),
    produtos_mais_vendidos: produtos
  };
}

module.exports = {
  getCaixaAberto, abrirCaixa, fecharCaixa,
  registrarMovimentacao, registrarEntradaPedido,
  getMovimentacoes, getResumoFinanceiro, getRelatorio
};

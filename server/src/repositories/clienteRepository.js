const { getDb } = require('../database/db');

function findAll() {
  return getDb().prepare(`
    SELECT c.*,
      (SELECT COUNT(*) FROM pedidos p
       WHERE p.cliente_id = c.id AND p.status NOT IN ('finalizado', 'cancelado')
      ) as pedidos_ativos
    FROM clientes c
    WHERE COALESCE(c.ativo, 1) = 1
    ORDER BY c.nome
  `).all();
}

function findById(id) {
  return getDb().prepare('SELECT * FROM clientes WHERE id = ?').get(id);
}

function findByTelefone(telefone) {
  const tel = String(telefone || '').replace(/\D/g, '');
  if (!tel) return null;

  let cliente = getDb().prepare('SELECT * FROM clientes WHERE telefone = ?').get(tel);
  if (cliente) return cliente;

  if (tel.length >= 10) {
    const sufixo = tel.slice(-11);
    cliente = getDb().prepare(`
      SELECT * FROM clientes WHERE telefone LIKE ?
      ORDER BY updated_at DESC LIMIT 1
    `).get(`%${sufixo}`);
    if (cliente) return cliente;
  }

  return null;
}

function findClienteRecorrente(telefone, chatId) {
  const candidatos = new Set();
  const tel = String(telefone || '').replace(/\D/g, '');
  if (tel.length >= 10) candidatos.add(tel);
  if (tel.length >= 10 && !tel.startsWith('55')) candidatos.add(`55${tel}`);
  if (tel.startsWith('55') && tel.length > 11) candidatos.add(tel.slice(2));

  for (const numero of candidatos) {
    const cliente = findByTelefone(numero);
    if (cliente?.nome && cliente.ativo !== 0) return cliente;
  }

  if (chatId) {
    const porChat = getDb().prepare(`
      SELECT c.* FROM clientes c
      JOIN pedidos p ON p.cliente_id = c.id
      WHERE p.whatsapp_chat_id = ? AND c.nome IS NOT NULL AND c.nome != ''
        AND COALESCE(c.ativo, 1) = 1
      ORDER BY p.created_at DESC LIMIT 1
    `).get(chatId);
    if (porChat) return porChat;
  }

  return null;
}

function create(data) {
  const telefone = data.telefone.replace(/\D/g, '');
  const result = getDb().prepare(`
    INSERT INTO clientes (nome, telefone, endereco, observacoes, ativo)
    VALUES (?, ?, ?, ?, 1)
  `).run(data.nome, telefone, data.endereco || null, data.observacoes || null);
  return findById(result.lastInsertRowid);
}

function update(id, data) {
  const fields = [];
  const values = [];

  ['nome', 'telefone', 'endereco', 'observacoes', 'ativo'].forEach((key) => {
    if (data[key] !== undefined) {
      fields.push(`${key} = ?`);
      values.push(key === 'telefone' ? data[key].replace(/\D/g, '') : data[key]);
    }
  });

  if (fields.length === 0) return findById(id);

  fields.push("updated_at = datetime('now', 'localtime')");
  values.push(id);

  getDb().prepare(`UPDATE clientes SET ${fields.join(', ')} WHERE id = ?`).run(...values);
  return findById(id);
}

function findOrCreate(data) {
  const telefone = data.telefone.replace(/\D/g, '');
  let cliente = findByTelefone(telefone);

  if (cliente) {
    const updates = {};
    if (cliente.ativo === 0) updates.ativo = 1;
    if (data.nome && data.nome !== cliente.nome) updates.nome = data.nome;
    if (data.endereco && data.endereco !== cliente.endereco) updates.endereco = data.endereco;
    if (Object.keys(updates).length > 0) {
      cliente = update(cliente.id, updates);
    }
    return cliente;
  }

  return create({ ...data, telefone });
}

function atualizarEstatisticas(clienteId, valorPedido) {
  getDb().prepare(`
    UPDATE clientes SET
      qtd_pedidos = qtd_pedidos + 1,
      valor_total = valor_total + ?,
      ultima_compra = datetime('now', 'localtime'),
      updated_at = datetime('now', 'localtime'),
      ativo = 1
    WHERE id = ?
  `).run(valorPedido, clienteId);
}

function getHistorico(clienteId) {
  return getDb().prepare(`
    SELECT p.* FROM pedidos p
    WHERE p.cliente_id = ?
    ORDER BY p.created_at DESC
  `).all(clienteId);
}

function countPedidosAtivos(clienteId) {
  const row = getDb().prepare(`
    SELECT COUNT(*) as qtd FROM pedidos
    WHERE cliente_id = ? AND status NOT IN ('finalizado', 'cancelado')
  `).get(clienteId);
  return row?.qtd || 0;
}

function remove(id) {
  const cliente = findById(id);
  if (!cliente) return null;

  if (countPedidosAtivos(id) > 0) {
    throw new Error('Cliente possui pedidos em andamento. Finalize ou cancele antes de excluir.');
  }

  getDb().prepare(`
    UPDATE clientes SET ativo = 0, updated_at = datetime('now', 'localtime')
    WHERE id = ?
  `).run(id);
  return true;
}

module.exports = {
  findAll, findById, findByTelefone, findClienteRecorrente, create, update,
  findOrCreate, atualizarEstatisticas, getHistorico, countPedidosAtivos, remove
};

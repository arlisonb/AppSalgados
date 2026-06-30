const { getDb } = require('../database/db');
const { normalizeProduto } = require('../utils/normalize');
const categoriaRepo = require('./categoriaRepository');

function resolveCategoriaId(p) {
  if (p.categoria_id) return p.categoria_id;
  const nome = String(p.categoria_nome || '').trim();
  if (!nome) return null;
  const existing = getDb().prepare(
    'SELECT id FROM categorias WHERE lower(trim(nome)) = lower(trim(?))'
  ).get(nome);
  if (existing) return existing.id;
  return categoriaRepo.create({ nome }).id;
}

function findAll(filters = {}) {
  let sql = `
    SELECT p.*, c.nome as categoria_nome
    FROM produtos p
    JOIN categorias c ON c.id = p.categoria_id
    WHERE 1=1
  `;
  const params = [];

  if (filters.ativo !== undefined) {
    sql += ' AND p.ativo = ?';
    params.push(filters.ativo ? 1 : 0);
  }
  if (filters.categoria_id) {
    sql += ' AND p.categoria_id = ?';
    params.push(filters.categoria_id);
  }

  sql += ' ORDER BY p.ordem, p.nome';
  return getDb().prepare(sql).all(...params);
}

function findById(id) {
  return getDb().prepare(`
    SELECT p.*, c.nome as categoria_nome
    FROM produtos p
    JOIN categorias c ON c.id = p.categoria_id
    WHERE p.id = ?
  `).get(id);
}

function findByCategoria(categoriaId) {
  return getDb().prepare(`
    SELECT * FROM produtos
    WHERE categoria_id = ? AND ativo = 1
    ORDER BY ordem, nome
  `).all(categoriaId);
}

function create(data) {
  const p = normalizeProduto(data);
  p.categoria_id = resolveCategoriaId(p);
  if (!p.nome || !p.categoria_id) {
    throw new Error('nome e categoria são obrigatórios');
  }
  const result = getDb().prepare(`
    INSERT INTO produtos (
      nome, categoria_id, descricao, imagem,
      preco_unidade, preco_cento, preco_cento_cinquenta,
      preco_duzentos, preco_trezentos, preco_quinhentos,
      preco_personalizado, tempo_preparo, ativo,
      observacao_padrao, ordem
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    p.nome, p.categoria_id, p.descricao, p.imagem,
    p.preco_unidade, p.preco_cento, p.preco_cento_cinquenta,
    p.preco_duzentos, p.preco_trezentos, p.preco_quinhentos,
    p.preco_personalizado, p.tempo_preparo, p.ativo,
    p.observacao_padrao, p.ordem
  );
  return findById(result.lastInsertRowid);
}

function update(id, data) {
  const fields = [];
  const values = [];
  const allowed = [
    'nome', 'categoria_id', 'descricao', 'imagem',
    'preco_unidade', 'preco_cento', 'preco_cento_cinquenta',
    'preco_duzentos', 'preco_trezentos', 'preco_quinhentos',
    'preco_personalizado', 'tempo_preparo', 'ativo',
    'observacao_padrao', 'ordem'
  ];

  allowed.forEach((key) => {
    if (data[key] !== undefined) {
      fields.push(`${key} = ?`);
      values.push(data[key]);
    }
  });

  if (fields.length === 0) return findById(id);

  fields.push("updated_at = datetime('now', 'localtime')");
  values.push(id);

  getDb().prepare(`UPDATE produtos SET ${fields.join(', ')} WHERE id = ?`).run(...values);
  return findById(id);
}

function remove(id) {
  getDb().prepare('UPDATE produtos SET ativo = 0, updated_at = datetime(\'now\', \'localtime\') WHERE id = ?').run(id);
}

function getPrecoPorQuantidade(produto, quantidade) {
  const tiers = [
    { qtd: 500, preco: produto.preco_quinhentos },
    { qtd: 300, preco: produto.preco_trezentos },
    { qtd: 200, preco: produto.preco_duzentos },
    { qtd: 150, preco: produto.preco_cento_cinquenta },
    { qtd: 100, preco: produto.preco_cento },
    { qtd: 1, preco: produto.preco_unidade }
  ];

  for (const tier of tiers) {
    if (quantidade >= tier.qtd && tier.preco > 0) {
      return (tier.preco / tier.qtd) * quantidade;
    }
  }

  if (produto.preco_unidade > 0) {
    return produto.preco_unidade * quantidade;
  }

  if (produto.preco_personalizado > 0) {
    return produto.preco_personalizado;
  }

  return 0;
}

module.exports = {
  findAll, findById, findByCategoria, create, update, remove, getPrecoPorQuantidade
};

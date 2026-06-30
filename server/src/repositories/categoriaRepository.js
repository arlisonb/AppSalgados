const { getDb } = require('../database/db');
const { normalizeCategoria } = require('../utils/normalize');

function findAll() {
  return getDb().prepare(`
    SELECT c.*, COUNT(p.id) as total_produtos
    FROM categorias c
    LEFT JOIN produtos p ON p.categoria_id = c.id AND p.ativo = 1
    GROUP BY c.id
    ORDER BY c.ordem, c.nome
  `).all();
}

function findAtivas() {
  return getDb().prepare(
    'SELECT * FROM categorias WHERE ativo = 1 ORDER BY ordem, nome'
  ).all();
}

function findById(id) {
  return getDb().prepare('SELECT * FROM categorias WHERE id = ?').get(id);
}

function create(data) {
  const c = normalizeCategoria(data);
  if (!c.nome) throw new Error('nome é obrigatório');
  const result = getDb().prepare(`
    INSERT INTO categorias (nome, ordem, ativo)
    VALUES (?, ?, ?)
  `).run(c.nome, c.ordem, c.ativo);
  return findById(result.lastInsertRowid);
}

function update(id, data) {
  const fields = [];
  const values = [];

  ['nome', 'ordem', 'ativo'].forEach((key) => {
    if (data[key] !== undefined) {
      fields.push(`${key} = ?`);
      values.push(data[key]);
    }
  });

  if (fields.length === 0) return findById(id);

  fields.push("updated_at = datetime('now', 'localtime')");
  values.push(id);

  getDb().prepare(`UPDATE categorias SET ${fields.join(', ')} WHERE id = ?`).run(...values);
  return findById(id);
}

module.exports = { findAll, findAtivas, findById, create, update };

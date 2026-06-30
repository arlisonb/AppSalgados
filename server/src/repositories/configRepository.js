const { getDb } = require('../database/db');

function getConfig(chave) {
  const row = getDb().prepare('SELECT valor FROM configuracoes WHERE chave = ?').get(chave);
  return row ? row.valor : null;
}

function getAllConfig() {
  const rows = getDb().prepare('SELECT chave, valor FROM configuracoes').all();
  return rows.reduce((acc, row) => {
    acc[row.chave] = row.valor;
    return acc;
  }, {});
}

function setConfig(chave, valor) {
  getDb().prepare(`
    INSERT INTO configuracoes (chave, valor, updated_at)
    VALUES (?, ?, datetime('now', 'localtime'))
    ON CONFLICT(chave) DO UPDATE SET valor = ?, updated_at = datetime('now', 'localtime')
  `).run(chave, String(valor), String(valor));
}

function setManyConfig(configs) {
  const stmt = getDb().prepare(`
    INSERT INTO configuracoes (chave, valor, updated_at)
    VALUES (?, ?, datetime('now', 'localtime'))
    ON CONFLICT(chave) DO UPDATE SET valor = ?, updated_at = datetime('now', 'localtime')
  `);

  const transaction = getDb().transaction((entries) => {
    entries.forEach(([chave, valor]) => stmt.run(chave, String(valor), String(valor)));
  });

  transaction(Object.entries(configs));
}

module.exports = { getConfig, getAllConfig, setConfig, setManyConfig };

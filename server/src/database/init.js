const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../../data/iona.db');
const SCHEMA_PATH = path.join(__dirname, 'schema.sql');

function initDatabase() {
  const dataDir = path.dirname(DB_PATH);
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
  }

  const db = new Database(DB_PATH);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  const schema = fs.readFileSync(SCHEMA_PATH, 'utf8');
  db.exec(schema);

  try {
    db.exec('ALTER TABLE pedidos ADD COLUMN whatsapp_chat_id TEXT');
  } catch (_) { /* coluna já existe */ }

  try {
    db.exec('ALTER TABLE clientes ADD COLUMN ativo INTEGER DEFAULT 1');
  } catch (_) { /* coluna já existe */ }

  try {
    db.exec(`
      CREATE TABLE IF NOT EXISTS fcm_tokens (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        token TEXT NOT NULL UNIQUE,
        device_name TEXT,
        created_at TEXT DEFAULT (datetime('now', 'localtime')),
        updated_at TEXT DEFAULT (datetime('now', 'localtime'))
      )
    `);
  } catch (_) { /* tabela já existe */ }

  seedCategorias(db);
  seedConfiguracoes(db);

  console.log('Banco de dados inicializado:', DB_PATH);
  db.close();
}

function seedCategorias(db) {
  const categorias = [
    'Salgados Fritos',
    'Salgados Assados',
    'Mini Salgados',
    'Doces',
    'Bolos',
    'Bebidas',
    'Combos',
    'Outros'
  ];

  const insert = db.prepare(
    'INSERT OR IGNORE INTO categorias (nome, ordem) VALUES (?, ?)'
  );

  categorias.forEach((nome, index) => {
    insert.run(nome, index + 1);
  });
}

function seedConfiguracoes(db) {
  const defaults = {
    nome_empresa: 'Iona Salgados',
    telefone: '',
    whatsapp: '',
    endereco: '',
    pix: '',
    banco: '',
    mensagem_inicial: 'Olá! Bem-vindo à Iona Salgados! 🥟',
    mensagem_final: 'Obrigado pela preferência! Volte sempre! 😊',
    taxa_entrega: '0',
    tempo_medio: '60',
    horario_funcionamento: 'Seg-Sáb: 8h às 18h',
    latitude: '-23.5505',
    longitude: '-46.6333'
  };

  const insert = db.prepare(
    'INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES (?, ?)'
  );

  Object.entries(defaults).forEach(([chave, valor]) => {
    insert.run(chave, valor);
  });
}

if (require.main === module) {
  require('dotenv').config({ path: path.join(__dirname, '../../.env') });
  initDatabase();
}

module.exports = { initDatabase, DB_PATH };

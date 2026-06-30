-- Iona Salgados - Schema SQLite

CREATE TABLE IF NOT EXISTS categorias (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL UNIQUE,
    ordem INTEGER DEFAULT 0,
    ativo INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS produtos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    categoria_id INTEGER NOT NULL,
    descricao TEXT,
    imagem TEXT,
    preco_unidade REAL DEFAULT 0,
    preco_cento REAL DEFAULT 0,
    preco_cento_cinquenta REAL DEFAULT 0,
    preco_duzentos REAL DEFAULT 0,
    preco_trezentos REAL DEFAULT 0,
    preco_quinhentos REAL DEFAULT 0,
    preco_personalizado REAL DEFAULT 0,
    tempo_preparo INTEGER DEFAULT 30,
    ativo INTEGER DEFAULT 1,
    observacao_padrao TEXT,
    ordem INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE IF NOT EXISTS clientes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    telefone TEXT NOT NULL UNIQUE,
    endereco TEXT,
    observacoes TEXT,
    qtd_pedidos INTEGER DEFAULT 0,
    valor_total REAL DEFAULT 0,
    ultima_compra TEXT,
    ativo INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS pedidos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    numero INTEGER NOT NULL,
    cliente_id INTEGER NOT NULL,
    status TEXT DEFAULT 'novo',
    valor_itens REAL DEFAULT 0,
    taxa_entrega REAL DEFAULT 0,
    valor_total REAL DEFAULT 0,
    forma_pagamento TEXT,
    troco REAL DEFAULT 0,
    observacoes TEXT,
    endereco TEXT,
    origem TEXT DEFAULT 'whatsapp',
    tempo_estimado INTEGER,
    whatsapp_chat_id TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS itens_pedido (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pedido_id INTEGER NOT NULL,
    produto_id INTEGER NOT NULL,
    nome_produto TEXT NOT NULL,
    quantidade INTEGER NOT NULL,
    preco_unitario REAL NOT NULL,
    subtotal REAL NOT NULL,
    observacao TEXT,
    FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE,
    FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

CREATE TABLE IF NOT EXISTS mensagens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cliente_id INTEGER,
    telefone TEXT NOT NULL,
    direcao TEXT NOT NULL,
    conteudo TEXT NOT NULL,
    tipo TEXT DEFAULT 'texto',
    lida INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS configuracoes (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL,
    updated_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS caixa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    data_abertura TEXT NOT NULL,
    data_fechamento TEXT,
    valor_inicial REAL DEFAULT 0,
    valor_final REAL DEFAULT 0,
    total_entradas REAL DEFAULT 0,
    total_saidas REAL DEFAULT 0,
    total_vendas REAL DEFAULT 0,
    qtd_pedidos INTEGER DEFAULT 0,
    qtd_cancelados INTEGER DEFAULT 0,
    status TEXT DEFAULT 'aberto',
    observacoes TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS movimentacoes_caixa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caixa_id INTEGER NOT NULL,
    tipo TEXT NOT NULL,
    categoria TEXT,
    descricao TEXT NOT NULL,
    valor REAL NOT NULL,
    forma_pagamento TEXT,
    pedido_id INTEGER,
    observacao TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
);

CREATE TABLE IF NOT EXISTS sessoes_whatsapp (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    telefone TEXT NOT NULL UNIQUE,
    estado TEXT DEFAULT 'menu',
    dados TEXT,
    updated_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS historico_pedidos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pedido_id INTEGER NOT NULL,
    status_anterior TEXT,
    status_novo TEXT NOT NULL,
    usuario TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
);

CREATE TABLE IF NOT EXISTS impressoes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pedido_id INTEGER,
    tipo TEXT NOT NULL,
    sucesso INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
);

CREATE INDEX IF NOT EXISTS idx_pedidos_status ON pedidos(status);
CREATE INDEX IF NOT EXISTS idx_pedidos_data ON pedidos(created_at);
CREATE INDEX IF NOT EXISTS idx_clientes_telefone ON clientes(telefone);
CREATE INDEX IF NOT EXISTS idx_mensagens_telefone ON mensagens(telefone);

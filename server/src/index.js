require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const { initDatabase } = require('./database/init');
const apiRoutes = require('./routes/api');
const whatsappService = require('./services/whatsappService');
const pushService = require('./services/pushService');

const PORT = Number(process.env.PORT) || 3000;
const HOST = process.env.HOST || '0.0.0.0';
const SERVE_WEB = process.env.SERVE_WEB !== 'false';
const PUBLIC_URL = process.env.PUBLIC_URL || '';

const app = express();
app.set('trust proxy', 1);

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] },
  path: '/socket.io/',
  transports: ['websocket', 'polling']
});

app.set('io', io);

['data', 'uploads', 'backups', 'tokens'].forEach((dir) => {
  const p = path.join(__dirname, '..', dir);
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
});

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

if (SERVE_WEB) {
  app.use(express.static(path.join(__dirname, '../public')));
}

app.use('/uploads', express.static(path.join(__dirname, '../uploads')));
app.use('/api', apiRoutes);

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.get('/', (req, res) => {
  res.json({
    app: 'iona-salgados',
    servidor: 'online',
    modo: SERVE_WEB ? 'web+api' : 'app',
    url: PUBLIC_URL || null,
    timestamp: new Date().toISOString()
  });
});

if (SERVE_WEB) {
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api') || req.path.startsWith('/uploads') || req.path.startsWith('/socket.io')) {
      return next();
    }
    res.sendFile(path.join(__dirname, '../public/index.html'));
  });
}

io.on('connection', (socket) => {
  console.log('Cliente conectado:', socket.id);

  socket.emit('statusServidor', { status: 'online' });
  socket.emit('statusWhatsApp', whatsappService.getStatus());

  socket.on('disconnect', () => {
    console.log('Cliente desconectado:', socket.id);
  });
});

async function start() {
  try {
    initDatabase();
    console.log('Banco de dados pronto');
    pushService.initFirebase();

    server.listen(PORT, HOST, () => {
      const modo = SERVE_WEB ? 'web + API' : 'somente app (API)';
      console.log(`Servidor Iona Salgados [${modo}] em http://${HOST}:${PORT}`);
      if (PUBLIC_URL) console.log(`URL pública: ${PUBLIC_URL}`);
    });

    whatsappService.initWhatsApp(io).catch((err) => {
      console.warn('WhatsApp: configure WHATSAPP_PHONE no .env e use código de pareamento:', err.message);
    });
  } catch (err) {
    console.error('Erro ao iniciar servidor:', err);
    process.exit(1);
  }
}

let shuttingDown = false;

async function gracefulShutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(`Encerrando servidor (${signal})...`);

  try {
    await whatsappService.shutdown();
  } catch (_) { }

  await new Promise((resolve) => {
    server.close(() => resolve());
    setTimeout(resolve, 5000);
  });

  process.exit(0);
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

start();

module.exports = { app, server, io };

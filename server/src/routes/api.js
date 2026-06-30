const express = require('express');
const produtoRepo = require('../repositories/produtoRepository');
const categoriaRepo = require('../repositories/categoriaRepository');
const clienteRepo = require('../repositories/clienteRepository');
const pedidoRepo = require('../repositories/pedidoRepository');
const entregaService = require('../services/entregaService');
const financeiroRepo = require('../repositories/financeiroRepository');
const configRepo = require('../repositories/configRepository');
const pushService = require('../services/pushService');

const router = express.Router();

// --- Produtos ---
router.get('/produtos', (req, res) => {
  const filters = {};
  if (req.query.ativo !== undefined) filters.ativo = req.query.ativo === 'true';
  if (req.query.categoria_id) filters.categoria_id = req.query.categoria_id;
  res.json(produtoRepo.findAll(filters));
});

router.get('/produtos/:id', (req, res) => {
  const produto = produtoRepo.findById(req.params.id);
  if (!produto) return res.status(404).json({ error: 'Produto não encontrado' });
  res.json(produto);
});

router.post('/produtos', (req, res) => {
  try {
    const produto = produtoRepo.create(req.body);
    req.app.get('io')?.emit('produtoAtualizado', produto);
    res.status(201).json(produto);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.put('/produtos/:id', (req, res) => {
  const produto = produtoRepo.update(req.params.id, req.body);
  req.app.get('io')?.emit('produtoAtualizado', produto);
  res.json(produto);
});

router.delete('/produtos/:id', (req, res) => {
  produtoRepo.remove(req.params.id);
  req.app.get('io')?.emit('produtoRemovido', { id: req.params.id });
  res.status(204).send();
});

// --- Categorias ---
router.get('/categorias', (req, res) => {
  res.json(categoriaRepo.findAll());
});

router.post('/categorias', (req, res) => {
  try {
    const categoria = categoriaRepo.create(req.body);
    res.status(201).json(categoria);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.put('/categorias/:id', (req, res) => {
  const categoria = categoriaRepo.update(req.params.id, req.body);
  res.json(categoria);
});

// --- Clientes ---
router.get('/clientes', (req, res) => {
  res.json(clienteRepo.findAll());
});

router.get('/clientes/:id', (req, res) => {
  const cliente = clienteRepo.findById(req.params.id);
  if (!cliente) return res.status(404).json({ error: 'Cliente não encontrado' });
  cliente.historico = clienteRepo.getHistorico(cliente.id);
  res.json(cliente);
});

router.put('/clientes/:id', (req, res) => {
  const cliente = clienteRepo.update(req.params.id, req.body);
  res.json(cliente);
});

router.delete('/clientes/:id', (req, res) => {
  try {
    const ok = clienteRepo.remove(req.params.id);
    if (!ok) return res.status(404).json({ error: 'Cliente não encontrado' });
    req.app.get('io')?.emit('clienteRemovido', { id: Number(req.params.id) });
    res.status(204).send();
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// --- Pedidos ---
router.get('/pedidos', (req, res) => {
  const filters = {};
  if (req.query.status) filters.status = req.query.status;
  if (req.query.data) filters.data = req.query.data;
  res.json(pedidoRepo.findAll(filters));
});

router.get('/pedidos/:id', (req, res) => {
  const pedido = pedidoRepo.findById(req.params.id);
  if (!pedido) return res.status(404).json({ error: 'Pedido não encontrado' });
  res.json(pedido);
});

router.post('/pedidos', (req, res) => {
  const pedido = pedidoRepo.create(req.body);
  req.app.get('io')?.emit('novoPedido', pedido);
  req.app.get('io')?.emit('imprimirPedido', pedido);
  pushService.notifyNovoPedido(pedido);
  res.status(201).json(pedido);
});

router.patch('/pedidos/:id/status', async (req, res) => {
  const { status } = req.body;
  const pedido = pedidoRepo.updateStatus(req.params.id, status, req.body.usuario);
  if (!pedido) return res.status(404).json({ error: 'Pedido não encontrado' });

  const io = req.app.get('io');
  io?.emit('pedidoAtualizado', pedido);
  if (status === 'cancelado') {
    io?.emit('pedidoCancelado', pedido);
    pushService.notifyPedidoCancelado(pedido);
  }
  if (status === 'finalizado') io?.emit('pedidoFinalizado', pedido);

  if (status === 'saiu_entrega') {
    try {
      await entregaService.notificarSaidaEntrega(pedido, io);
    } catch (err) {
      console.error('Erro ao avisar cliente no WhatsApp:', err.message);
      return res.status(502).json({
        error: 'Status atualizado, mas não foi possível avisar o cliente no WhatsApp',
        pedido,
        whatsapp_erro: err.message
      });
    }
  }

  res.json(pedido);
});

router.post('/pedidos/:id/imprimir', (req, res) => {
  const pedido = pedidoRepo.findById(req.params.id);
  if (!pedido) return res.status(404).json({ error: 'Pedido não encontrado' });
  req.app.get('io')?.emit('imprimirPedido', pedido);
  res.json({ ok: true });
});

// --- Dashboard ---
router.get('/dashboard', (req, res) => {
  res.json(pedidoRepo.getDashboard());
});

// --- Produção ---
router.get('/producao', (req, res) => {
  res.json(pedidoRepo.getProducao(req.query.data));
});

// --- Financeiro ---
router.get('/financeiro/resumo', (req, res) => {
  res.json(financeiroRepo.getResumoFinanceiro());
});

router.get('/financeiro/caixa', (req, res) => {
  const caixa = financeiroRepo.getCaixaAberto();
  if (!caixa) return res.json({ aberto: false });
  caixa.movimentacoes = financeiroRepo.getMovimentacoes(caixa.id);
  res.json({ aberto: true, ...caixa });
});

router.post('/financeiro/caixa/abrir', (req, res) => {
  try {
    const caixa = financeiroRepo.abrirCaixa(req.body.valor_inicial || 0);
    res.status(201).json(caixa);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.post('/financeiro/caixa/fechar', (req, res) => {
  try {
    const caixa = financeiroRepo.fecharCaixa(req.body.observacoes);
    res.json(caixa);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.post('/financeiro/movimentacao', (req, res) => {
  try {
    const mov = financeiroRepo.registrarMovimentacao(req.body);
    res.status(201).json(mov);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.get('/financeiro/relatorio', (req, res) => {
  const { periodo, data_inicio, data_fim } = req.query;
  res.json(financeiroRepo.getRelatorio(periodo, data_inicio, data_fim));
});

// --- Configurações ---
router.get('/configuracoes', (req, res) => {
  res.json(configRepo.getAllConfig());
});

router.put('/configuracoes', (req, res) => {
  configRepo.setManyConfig(req.body);
  req.app.get('io')?.emit('configAtualizada', configRepo.getAllConfig());
  res.json(configRepo.getAllConfig());
});

// --- Status ---
router.get('/status', (req, res) => {
  const whatsappService = require('../services/whatsappService');
  res.json({
    app: 'iona-salgados',
    servidor: 'online',
    whatsapp: whatsappService.getStatus(),
    timestamp: new Date().toISOString()
  });
});

// --- WhatsApp ---
router.get('/whatsapp/status', (req, res) => {
  const whatsappService = require('../services/whatsappService');
  res.json(whatsappService.getStatus());
});

router.post('/whatsapp/reconectar', async (req, res) => {
  try {
    const whatsappService = require('../services/whatsappService');
    const { telefone } = req.body;
    const result = await whatsappService.reconectar(telefone);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.post('/whatsapp/desconectar', async (req, res) => {
  try {
    const whatsappService = require('../services/whatsappService');
    const result = await whatsappService.desconectar();
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- FCM (push notifications) ---
router.post('/fcm/register', (req, res) => {
  const { token, device_name } = req.body;
  if (!token || typeof token !== 'string') {
    return res.status(400).json({ error: 'Token FCM obrigatório' });
  }
  try {
    const fcmRepo = require('../repositories/fcmRepository');
    fcmRepo.upsertToken(token.trim(), device_name);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;

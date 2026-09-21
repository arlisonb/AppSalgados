const { getDb } = require('../database/db');
const pushService = require('./pushService');
const produtoRepo = require('../repositories/produtoRepository');
const clienteRepo = require('../repositories/clienteRepository');
const pedidoRepo = require('../repositories/pedidoRepository');
const configRepo = require('../repositories/configRepository');
const { parseQuantidadeNatural, parseMultiplosItens, matchProdutoCatalogo } = require('../utils/nlpParser');
const { isChatIdValido, normalizeChatId } = require('../utils/whatsappChat');
const { gerarPixCopiaECola } = require('../utils/pix');

let whatsappClient = null;
let io = null;

const ESTADOS = {
  ESCOLHENDO_ITENS: 'escolhendo_itens',
  ESCOLHER_ENTREGA: 'escolher_entrega',
  ESCOLHER_PAGAMENTO: 'escolher_pagamento',
  ESCOLHER_TROCO: 'escolher_troco',
  VALOR_DINHEIRO: 'valor_dinheiro',
  PEDIDO_NOME: 'pedido_nome',
  PEDIDO_ENDERECO: 'pedido_endereco',
  PEDIDO_TELEFONE: 'pedido_telefone',
  CONFIRMAR: 'confirmar',
  CONFIRMAR_ENTREGA: 'confirmar_entrega',
  CONFIRMAR_DADOS: 'confirmar_dados'
};

function init(client, socketIo) {
  whatsappClient = client;
  io = socketIo;
  console.log('Bot WhatsApp inicializado');
}

function normalizePhoneKey(telefone) {
  let clean = String(telefone || '').replace(/\D/g, '');
  if (clean.length >= 10 && clean.length <= 11 && !clean.startsWith('55')) {
    clean = `55${clean}`;
  }
  return clean;
}

function resolveSessionKey(telefone, chatId) {
  const normalizedChatId = normalizeChatId(chatId);
  if (normalizedChatId && isChatIdValido(normalizedChatId)) return normalizedChatId;
  const clean = normalizePhoneKey(telefone);
  return clean ? `${clean}@c.us` : null;
}

function getSessaoPendenteEntrega(telefone, chatId) {
  const rows = getDb().prepare(`
    SELECT * FROM sessoes_whatsapp
    WHERE estado = ?
    ORDER BY updated_at DESC
  `).all(ESTADOS.CONFIRMAR_ENTREGA);

  const telNormalizado = normalizePhoneKey(telefone);

  for (const row of rows) {
    const dados = JSON.parse(row.dados || '{}');
    const pedido = pedidoRepo.findById(dados.pedido_id);
    if (!pedido || pedido.status !== 'saiu_entrega') continue;

    const sessionChatId = dados.chat_id || row.telefone;
    if (chatId && (sessionChatId === chatId || pedido.whatsapp_chat_id === chatId)) {
      return { ...row, dados: { ...dados, chat_id: chatId || dados.chat_id } };
    }

    const telPedido = normalizePhoneKey(pedido.cliente_telefone);
    if (telNormalizado && telPedido && telNormalizado === telPedido) {
      return { ...row, dados: { ...dados, chat_id: chatId || dados.chat_id } };
    }
  }
  return null;
}

function getSessao(telefone, chatId = null) {
  const entregaPendente = getSessaoPendenteEntrega(telefone, chatId);
  if (entregaPendente) return entregaPendente;

  const key = resolveSessionKey(telefone, chatId);
  if (!key) {
    return { telefone: '', estado: ESTADOS.ESCOLHENDO_ITENS, dados: {} };
  }

  let sessao = getDb().prepare('SELECT * FROM sessoes_whatsapp WHERE telefone = ?').get(key);

  if (!sessao) {
    getDb().prepare('INSERT INTO sessoes_whatsapp (telefone, estado, dados) VALUES (?, ?, ?)').run(key, ESTADOS.ESCOLHENDO_ITENS, '{}');
    sessao = { telefone: key, estado: ESTADOS.ESCOLHENDO_ITENS, dados: '{}' };
  }

  const parsed = { ...sessao, dados: JSON.parse(sessao.dados || '{}') };

  // Sessão de confirmação de entrega expirada (pedido já finalizado/cancelado).
  if (parsed.estado === ESTADOS.CONFIRMAR_ENTREGA) {
    const pedido = pedidoRepo.findById(parsed.dados.pedido_id);
    if (!pedido || pedido.status !== 'saiu_entrega') {
      const limpa = { chat_id: parsed.dados.chat_id || key, carrinho: [] };
      setSessao(telefone, ESTADOS.ESCOLHENDO_ITENS, limpa, chatId);
      return { telefone: key, estado: ESTADOS.ESCOLHENDO_ITENS, dados: limpa };
    }
  }

  return parsed;
}

function setSessao(telefone, estado, dados, chatId = null) {
  const key = resolveSessionKey(telefone, chatId || dados?.chat_id);
  if (!key) return;
  const payload = { ...dados, chat_id: chatId || dados?.chat_id || key };
  getDb().prepare(`
    INSERT INTO sessoes_whatsapp (telefone, estado, dados, updated_at)
    VALUES (?, ?, ?, datetime('now', 'localtime'))
    ON CONFLICT(telefone) DO UPDATE SET estado = ?, dados = ?, updated_at = datetime('now', 'localtime')
  `).run(key, estado, JSON.stringify(payload), estado, JSON.stringify(payload));
}

function salvarMensagem(telefone, direcao, conteudo, clienteId = null) {
  getDb().prepare(`
    INSERT INTO mensagens (telefone, direcao, conteudo, cliente_id)
    VALUES (?, ?, ?, ?)
  `).run(telefone.replace(/\D/g, ''), direcao, conteudo, clienteId);
}

function getChatId(telefone, dados = {}) {
  const fromDados = normalizeChatId(dados.chat_id);
  if (fromDados && isChatIdValido(fromDados)) return fromDados;
  const clean = String(telefone || '').replace(/\D/g, '');
  return clean ? `${clean}@c.us` : fromDados;
}

async function enviarMensagem(telefone, texto, chatId = null) {
  if (!whatsappClient) {
    console.warn('WhatsApp client não disponível');
    return;
  }
  const sessao = getSessao(telefone, chatId);
  const to = normalizeChatId(chatId || getChatId(telefone, sessao.dados));
  if (!isChatIdValido(to)) {
    console.warn('ChatId inválido, mensagem não enviada:', to);
    return;
  }
  try {
    await whatsappClient.sendText(to, texto);
    salvarMensagem(telefone, 'saida', texto, sessao.dados.cliente_id);
  } catch (err) {
    console.error('Erro ao enviar mensagem WhatsApp:', err.message, '→', to);
    throw err;
  }
}

function getProdutosAtivos() {
  return produtoRepo.findAll({ ativo: 1 });
}

const EMOJI_DIGITOS = ['0️⃣', '1️⃣', '2️⃣', '3️⃣', '4️⃣', '5️⃣', '6️⃣', '7️⃣', '8️⃣', '9️⃣'];

function emojiNumero(n) {
  if (n === 10) return '🔟';
  if (n >= 1 && n <= 9) return EMOJI_DIGITOS[n];
  return String(n).split('').map((d) => EMOJI_DIGITOS[parseInt(d, 10)]).join('');
}

function fmtMoeda(valor) {
  return `R$ ${valor.toFixed(2).replace('.', ',')}`;
}

function parseValorMonetario(texto) {
  let s = String(texto || '').trim().replace(/[^\d,.]/g, '');
  if (!s) return null;
  if (s.includes(',')) {
    s = s.replace(/\./g, '').replace(',', '.');
  }
  const v = parseFloat(s);
  if (!Number.isFinite(v) || v <= 0) return null;
  return Math.round(v * 100) / 100;
}

function formatPrecos(p) {
  const linhas = [];
  const precoUnidade = p.preco_unidade > 0
    ? p.preco_unidade
    : (p.preco_cento > 0 ? p.preco_cento / 100 : 0);

  if (precoUnidade > 0) {
    linhas.push(`💰 Unidade: ${fmtMoeda(precoUnidade)}`);
  }
  if (p.preco_cento > 0) {
    linhas.push(`📦 Cento (100un): ${fmtMoeda(p.preco_cento)}`);
  }
  return linhas.join('\n   ') || 'Consulte preços';
}

function getOpcoesMenu(produtos) {
  const total = produtos.length;
  return {
    finalizar: total + 1,
    cardapio: total + 2
  };
}

function normalizarTextoPedido(texto) {
  let t = String(texto).trim();
  if (t === '🔟') return '10';
  for (let i = 9; i >= 0; i--) {
    t = t.split(EMOJI_DIGITOS[i]).join(String(i));
  }
  return t.replace(/\s+/g, ' ').trim();
}

function extrairNumeroMenu(texto) {
  const norm = normalizarTextoPedido(texto).toLowerCase();
  if (!/^\d+$/.test(norm)) return null;
  return parseInt(norm, 10);
}

function getCardapioNumerado(opts = {}) {
  const produtos = getProdutosAtivos();
  const opcoes = getOpcoesMenu(produtos);
  const msgInicial = configRepo.getConfig('mensagem_inicial') || 'Olá! Bem-vindo à Iona Salgados! 🥟';
  let texto = opts.omitirSaudacao
    ? '📋 *CARDÁPIO*\n\n'
    : `${msgInicial}\n\n📋 *CARDÁPIO*\n\n`;

  if (produtos.length === 0) {
    texto += '_Nenhum produto cadastrado no momento._\n';
  } else {
    produtos.forEach((p, i) => {
      texto += `${emojiNumero(i + 1)} *${p.nome}*\n   ${formatPrecos(p)}\n\n`;
    });
    texto += `${emojiNumero(opcoes.finalizar)} *FINALIZAR* pedido\n`;
    texto += `${emojiNumero(opcoes.cardapio)} *VER CARDÁPIO*\n\n`;
  }

  texto += `📝 *Como pedir:*\n`;
  texto += `• *${emojiNumero(1)}* — escolhe o item (depois informa a qtd)\n`;
  texto += `• *${emojiNumero(1)} 100* — item 1 com 100 unidades\n`;
  texto += `• *100 coxinha* — quantidade + nome\n`;
  texto += `• *${emojiNumero(opcoes.finalizar)}* ou *FINALIZAR* — concluir pedido\n`;
  texto += `• *${emojiNumero(opcoes.cardapio)}* ou *CARDAPIO* — ver cardápio`;

  return { texto, produtos, opcoes };
}

function getEnderecoLoja() {
  return String(configRepo.getConfig('endereco') || '').trim();
}

function enderecoNoPedido(dados) {
  if (dados.tipo_entrega === 'retirada') {
    const loja = getEnderecoLoja();
    return loja ? `Retirada — ${loja}` : 'Retirada no local';
  }
  return dados.endereco || '';
}

function calcularResumo(carrinho, dados) {
  const taxaConfig = parseFloat(configRepo.getConfig('taxa_entrega') || '0');
  const taxaEntrega = dados?.tipo_entrega === 'retirada' ? 0 : taxaConfig;
  let valorItens = 0;

  const itens = carrinho.map((item) => {
    const subtotal = produtoRepo.getPrecoPorQuantidade(item.produto, item.quantidade);
    valorItens += subtotal;
    return {
      produto_id: item.produto.id,
      nome_produto: item.produto.nome,
      quantidade: item.quantidade,
      preco_unitario: subtotal / item.quantidade,
      subtotal
    };
  });

  return {
    itens,
    valor_itens: valorItens,
    taxa_entrega: taxaEntrega,
    valor_total: valorItens + taxaEntrega,
    endereco: dados.endereco,
    telefone: dados.telefone,
    nome: dados.nome
  };
}

function getResumoCarrinhoTexto(carrinho) {
  if (!carrinho || carrinho.length === 0) return '🛒 Carrinho vazio';
  const resumo = calcularResumo(carrinho, {});
  let texto = '🛒 *Seu pedido até agora:*\n\n';
  resumo.itens.forEach((item) => {
    texto += `• ${item.quantidade}un ${item.nome_produto} — R$ ${item.subtotal.toFixed(2)}\n`;
  });
  texto += `\n💰 *Subtotal: R$ ${resumo.valor_itens.toFixed(2)}*`;
  return texto;
}

function tentarAdicionarItens(mensagem, produtos, opcoes) {
  const texto = normalizarTextoPedido(mensagem);

  const matchNumero = texto.match(/^(\d+)\s+(\d+)$/);
  if (matchNumero) {
    const itemNum = parseInt(matchNumero[1], 10);
    const quantidade = parseInt(matchNumero[2], 10);
    if (itemNum >= 1 && itemNum <= produtos.length && quantidade > 0) {
      return [{ produto: produtos[itemNum - 1], quantidade }];
    }
  }

  const multiplos = parseMultiplosItens(texto, produtos);
  if (multiplos.length > 0) return multiplos;

  const parsed = parseQuantidadeNatural(texto, produtos);
  if (parsed?.produtoObj) {
    return [{ produto: parsed.produtoObj, quantidade: parsed.quantidade }];
  }
  if (parsed) {
    const produto = matchProdutoCatalogo(parsed.produto, produtos);
    if (produto) return [{ produto, quantidade: parsed.quantidade }];
  }

  return [];
}

async function finalizarPedido(tel, dados, carrinho, chatId) {
  if (carrinho.length === 0) {
    await enviarMensagem(tel, 'Seu carrinho está vazio. Escolha os itens do cardápio primeiro.', chatId);
    return;
  }

  dados.carrinho = carrinho;
  delete dados.tipo_entrega;
  delete dados.forma_pagamento;
  delete dados.troco;
  delete dados.valor_pago_dinheiro;
  setSessao(tel, ESTADOS.ESCOLHER_ENTREGA, dados, chatId);

  const enderecoLoja = getEnderecoLoja();
  let texto = `${getResumoCarrinhoTexto(carrinho)}\n\n📦 *Como deseja receber?*\n\n`;
  texto += '1️⃣ *Entrega* no seu endereço\n';
  texto += '2️⃣ *Retirada* na loja';
  if (enderecoLoja) {
    texto += `\n   📍 ${enderecoLoja}`;
  } else {
    texto += '\n   _(Configure o endereço da loja em Configurações)_';
  }
  texto += '\n\nResponda *1* ou *2*';

  await enviarMensagem(tel, texto, chatId);
}

async function handleEscolherEntrega(tel, texto, dados, chatId) {
  const opcao = normalizarTextoPedido(texto).replace(/\D/g, '');

  if (opcao === '1') {
    dados.tipo_entrega = 'entrega';
    await continuarColetaDadosCliente(tel, dados, chatId);
    return;
  }

  if (opcao === '2') {
    dados.tipo_entrega = 'retirada';
    await continuarColetaDadosCliente(tel, dados, chatId);
    return;
  }

  await enviarMensagem(tel, 'Escolha *1️⃣ Entrega* ou *2️⃣ Retirada na loja*', chatId);
}

async function continuarColetaDadosCliente(tel, dados, chatId) {
  const carrinho = dados.carrinho || [];
  const retirada = dados.tipo_entrega === 'retirada';

  if (!retirada && dados.nome && dados.endereco && dados.telefone) {
    const cliente = clienteRepo.findOrCreate({
      nome: dados.nome,
      telefone: dados.telefone,
      endereco: dados.endereco
    });
    dados.cliente_id = cliente.id;
    await irParaEscolherPagamento(tel, dados, chatId);
    return;
  }

  if (retirada && dados.nome && dados.telefone) {
    const cliente = clienteRepo.findOrCreate({
      nome: dados.nome,
      telefone: dados.telefone,
      endereco: dados.endereco || null
    });
    dados.cliente_id = cliente.id;
    await irParaEscolherPagamento(tel, dados, chatId);
    return;
  }

  if (dados.cliente_cadastrado) {
    const c = dados.cliente_cadastrado;
    setSessao(tel, ESTADOS.CONFIRMAR_DADOS, dados, chatId);
    if (retirada) {
      const loja = getEnderecoLoja() || 'Endereço da loja (consulte-nos)';
      await enviarMensagem(
        tel,
        `${getResumoCarrinhoTexto(carrinho)}\n\n🏪 *Retirada na loja*\n📍 ${loja}\n\n📋 *Seus dados:*\n👤 ${c.nome}\n📱 ${c.telefone}\n\nEstá correto?\n\n1️⃣ Sim\n2️⃣ Não`,
        chatId
      );
    } else {
      await enviarMensagem(
        tel,
        `${getResumoCarrinhoTexto(carrinho)}\n\n📋 *Seus dados do último pedido:*\n👤 ${c.nome}\n📍 ${c.endereco}\n📱 ${c.telefone}\n\nSão os mesmos?\n\n1️⃣ Sim\n2️⃣ Não`,
        chatId
      );
    }
    return;
  }

  setSessao(tel, ESTADOS.PEDIDO_NOME, dados, chatId);
  const intro = retirada
    ? `${getResumoCarrinhoTexto(carrinho)}\n\n🏪 Retirada na loja.\n\nQual é o seu *nome*?`
    : `${getResumoCarrinhoTexto(carrinho)}\n\nQual é o seu *nome*?`;
  await enviarMensagem(tel, intro, chatId);
}

async function irParaEscolherPagamento(tel, dados, chatId) {
  setSessao(tel, ESTADOS.ESCOLHER_PAGAMENTO, dados, chatId);
  const temPix = !!(configRepo.getConfig('pix') || '').trim();
  let texto = '💳 *Forma de pagamento:*\n\n';
  texto += '1️⃣ PIX\n';
  texto += '2️⃣ Dinheiro';
  if (!temPix) {
    texto += '\n\n_(PIX ainda não configurado — escolha dinheiro ou fale conosco)_';
  }
  texto += '\n\nResponda *1* ou *2*';
  await enviarMensagem(tel, texto, chatId);
}

async function irParaConfirmarPedido(tel, dados, chatId) {
  setSessao(tel, ESTADOS.CONFIRMAR, dados, chatId);
  await enviarResumo(tel, dados, chatId);
  await enviarMensagem(tel, '✅ *Confirma o pedido?*\n\n1️⃣ Sim\n2️⃣ Não', chatId);
}

async function handleEscolherPagamento(tel, texto, dados, chatId) {
  const opcao = normalizarTextoPedido(texto).replace(/\D/g, '');
  const temPix = !!(configRepo.getConfig('pix') || '').trim();

  if (opcao === '1') {
    if (!temPix) {
      await enviarMensagem(tel, 'PIX indisponível no momento. Escolha *2️⃣ Dinheiro* ou digite *OI* para falar conosco.', chatId);
      return;
    }
    dados.forma_pagamento = 'PIX';
    delete dados.troco;
    delete dados.valor_pago_dinheiro;
    await irParaConfirmarPedido(tel, dados, chatId);
    return;
  }

  if (opcao === '2') {
    dados.forma_pagamento = 'Dinheiro';
    dados.troco = 0;
    delete dados.valor_pago_dinheiro;
    setSessao(tel, ESTADOS.ESCOLHER_TROCO, dados, chatId);
    await enviarMensagem(
      tel,
      '💵 *Pagamento em dinheiro*\n\nPrecisa de troco?\n\n1️⃣ Sim\n2️⃣ Não',
      chatId
    );
    return;
  }

  await enviarMensagem(tel, 'Escolha *1️⃣ PIX* ou *2️⃣ Dinheiro*', chatId);
}

async function handleEscolherTroco(tel, texto, dados, chatId) {
  const opcao = normalizarTextoPedido(texto).replace(/\D/g, '');
  const total = calcularResumo(dados.carrinho || [], dados).valor_total;

  if (opcao === '2') {
    dados.troco = 0;
    delete dados.valor_pago_dinheiro;
    await irParaConfirmarPedido(tel, dados, chatId);
    return;
  }

  if (opcao === '1') {
    setSessao(tel, ESTADOS.VALOR_DINHEIRO, dados, chatId);
    await enviarMensagem(
      tel,
      `Com quanto você vai pagar?\n\nTotal do pedido: *${fmtMoeda(total)}*\n\nInforme o valor (ex: *100* ou *100,50*):`,
      chatId
    );
    return;
  }

  await enviarMensagem(tel, 'Responda *1️⃣ Sim* (preciso de troco) ou *2️⃣ Não*', chatId);
}

async function handleValorDinheiro(tel, texto, dados, chatId) {
  const total = calcularResumo(dados.carrinho || [], dados).valor_total;
  const pago = parseValorMonetario(texto);

  if (pago == null) {
    await enviarMensagem(tel, 'Informe um valor válido. Ex: *50* ou *120,00*', chatId);
    return;
  }

  if (pago < total) {
    await enviarMensagem(
      tel,
      `O valor informado (${fmtMoeda(pago)}) é menor que o total (${fmtMoeda(total)}).\n\nInforme um valor *maior ou igual* ao total:`,
      chatId
    );
    return;
  }

  dados.valor_pago_dinheiro = pago;
  dados.troco = Math.round((pago - total) * 100) / 100;

  await enviarMensagem(
    tel,
    dados.troco > 0
      ? `🔄 Troco calculado: *${fmtMoeda(dados.troco)}*\n(Pagamento ${fmtMoeda(pago)} − total ${fmtMoeda(total)})`
      : `✅ Valor exato: *${fmtMoeda(pago)}* (sem troco)`,
    chatId
  );
  await irParaConfirmarPedido(tel, dados, chatId);
}

async function iniciarAtendimento(tel, dados, chatId) {
  const cliente = clienteRepo.findClienteRecorrente(tel, chatId || dados.chat_id);

  if (cliente?.nome && cliente.endereco && cliente.telefone) {
    dados.cliente_cadastrado = {
      id: cliente.id,
      nome: cliente.nome,
      endereco: cliente.endereco,
      telefone: cliente.telefone
    };
    dados.carrinho = [];
    await enviarMensagem(
      tel,
      `Olá, *${cliente.nome}*! 😊 Que bom ter você de volta na Iona Salgados! 🥟`,
      chatId
    );
    await iniciarCardapio(tel, dados, chatId, { omitirSaudacao: true });
    return;
  }

  await iniciarCardapio(tel, { ...dados, carrinho: [] }, chatId);
}

async function handleConfirmarDados(tel, texto, dados, chatId) {
  const opcao = normalizarTextoPedido(texto).replace(/\D/g, '');

  if (opcao === '1') {
    const c = dados.cliente_cadastrado;
    dados.nome = c.nome;
    dados.telefone = c.telefone;
    if (dados.tipo_entrega !== 'retirada') {
      dados.endereco = c.endereco;
    }
    delete dados.cliente_cadastrado;
    const cliente = clienteRepo.findOrCreate({
      nome: dados.nome,
      telefone: dados.telefone,
      endereco: dados.tipo_entrega === 'retirada' ? (c.endereco || null) : dados.endereco
    });
    dados.cliente_id = cliente.id;
    await irParaEscolherPagamento(tel, dados, chatId);
    return;
  }

  if (opcao === '2') {
    delete dados.cliente_cadastrado;
    delete dados.nome;
    delete dados.endereco;
    delete dados.telefone;
    delete dados.cliente_id;
    dados.atualizar_cadastro = true;
    setSessao(tel, ESTADOS.PEDIDO_NOME, dados, chatId);
    await enviarMensagem(tel, 'Sem problemas! Vamos atualizar seus dados.\n\nQual é o seu *nome*?', chatId);
    return;
  }

  await enviarMensagem(tel, 'Responda *1️⃣* Sim ou *2️⃣* Não', chatId);
}

async function iniciarCardapio(tel, dados, chatId, opts = {}) {
  const { texto } = getCardapioNumerado({ omitirSaudacao: opts.omitirSaudacao });
  setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { ...dados, carrinho: dados.carrinho || [] }, chatId);
  await enviarMensagem(tel, texto, chatId);
}

function isSaudacao(texto) {
  return /^(ola|oi|olá|hey|menu|cardapio|cardápio|inicio|início|bom dia|boa tarde|boa noite)$/i.test(texto.trim());
}

function podeReiniciarPorSaudacao(sessao) {
  const fluxoAtivo = [
    ESTADOS.CONFIRMAR,
    ESTADOS.CONFIRMAR_DADOS,
    ESTADOS.ESCOLHER_ENTREGA,
    ESTADOS.ESCOLHER_PAGAMENTO,
    ESTADOS.ESCOLHER_TROCO,
    ESTADOS.VALOR_DINHEIRO,
    ESTADOS.PEDIDO_NOME,
    ESTADOS.PEDIDO_ENDERECO,
    ESTADOS.PEDIDO_TELEFONE
  ];
  if (fluxoAtivo.includes(sessao.estado)) return false;
  if (sessao.estado === ESTADOS.CONFIRMAR_ENTREGA) {
    const pedido = pedidoRepo.findById(sessao.dados?.pedido_id);
    return pedido?.status !== 'saiu_entrega';
  }
  return true;
}

async function processarMensagem(telefone, mensagem, chatId) {
  const tel = String(telefone || '').replace(/\D/g, '');
  const chatIdNorm = normalizeChatId(chatId);
  const sessao = getSessao(telefone, chatIdNorm);
  const dados = { ...sessao.dados, chat_id: chatIdNorm || sessao.dados.chat_id };
  const texto = mensagem.trim();
  const textoLower = texto.toLowerCase();

  salvarMensagem(tel || chatIdNorm, 'entrada', mensagem, dados.cliente_id);

  if (isSaudacao(textoLower) && podeReiniciarPorSaudacao(sessao)) {
    await iniciarAtendimento(tel, { chat_id: chatIdNorm, carrinho: [] }, chatIdNorm);
    return;
  }

  switch (sessao.estado) {
    case ESTADOS.ESCOLHENDO_ITENS:
      await handleEscolhendoItens(tel, texto, textoLower, { ...sessao, dados }, chatIdNorm);
      break;
    case ESTADOS.CONFIRMAR_DADOS:
      await handleConfirmarDados(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.ESCOLHER_ENTREGA:
      await handleEscolherEntrega(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.ESCOLHER_PAGAMENTO:
      await handleEscolherPagamento(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.ESCOLHER_TROCO:
      await handleEscolherTroco(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.VALOR_DINHEIRO:
      await handleValorDinheiro(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.PEDIDO_NOME:
      await handlePedidoNome(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.PEDIDO_ENDERECO:
      await handlePedidoEndereco(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.PEDIDO_TELEFONE:
      await handlePedidoTelefone(tel, texto, dados, chatIdNorm);
      break;
    case ESTADOS.CONFIRMAR:
      await handleConfirmar(tel, normalizarTextoPedido(texto).replace(/\D/g, ''), dados, chatIdNorm);
      break;
    case ESTADOS.CONFIRMAR_ENTREGA:
      await handleConfirmarEntrega(tel, texto, dados, chatIdNorm);
      break;
    default:
      await iniciarCardapio(tel, dados, chatIdNorm);
  }
}

async function handleEscolhendoItens(tel, texto, textoLower, sessao, chatId) {
  const dados = { ...sessao.dados, chat_id: chatId || sessao.dados.chat_id };
  const { produtos, opcoes } = getCardapioNumerado();
  const carrinho = dados.carrinho || [];
  const textoNorm = normalizarTextoPedido(texto);
  const numMenu = extrairNumeroMenu(texto);

  if (dados.aguardando_item != null && produtos[dados.aguardando_item]) {
    if (textoLower === 'cardapio' || textoLower === 'cardápio' || numMenu === opcoes.cardapio) {
      delete dados.aguardando_item;
      await iniciarCardapio(tel, dados, chatId);
      return;
    }
    if (textoLower === 'finalizar' || textoLower === 'pronto' || numMenu === opcoes.finalizar) {
      delete dados.aguardando_item;
      await finalizarPedido(tel, dados, carrinho, chatId);
      return;
    }
    const qtd = parseInt(textoNorm.replace(/\D/g, ''), 10);
    if (qtd > 0) {
      const produto = produtos[dados.aguardando_item];
      carrinho.push({ produto, quantidade: qtd });
      dados.carrinho = carrinho;
      delete dados.aguardando_item;
      setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, dados, chatId);
      await enviarMensagem(
        tel,
        `✅ ${qtd}un ${produto.nome} adicionado!\n\n${getResumoCarrinhoTexto(carrinho)}\n\nAdicione mais ou digite *${emojiNumero(opcoes.finalizar)}* para finalizar`,
        chatId
      );
      return;
    }
    await enviarMensagem(tel, `Informe a quantidade em números.\nEx: *100*`, chatId);
    return;
  }

  if (textoLower === 'cardapio' || textoLower === 'cardápio' || numMenu === opcoes.cardapio) {
    delete dados.aguardando_item;
    await iniciarCardapio(tel, dados, chatId);
    return;
  }

  if (textoLower === 'finalizar' || textoLower === 'pronto' || textoLower === 'fechar' || numMenu === opcoes.finalizar) {
    delete dados.aguardando_item;
    await finalizarPedido(tel, dados, carrinho, chatId);
    return;
  }

  if (numMenu != null && numMenu >= 1 && numMenu <= produtos.length) {
    dados.aguardando_item = numMenu - 1;
    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, dados, chatId);
    await enviarMensagem(
      tel,
      `${emojiNumero(numMenu)} *${produtos[numMenu - 1].nome}*\n\nQuantas unidades?`,
      chatId
    );
    return;
  }

  const itens = tentarAdicionarItens(texto, produtos, opcoes);
  if (itens.length === 0) {
    await enviarMensagem(
      tel,
      `Não entendi. Exemplos:\n• *${emojiNumero(1)}* — escolher item\n• *${emojiNumero(1)} 100* — item 1, 100un\n• *100 coxinha*\n• *${emojiNumero(opcoes.finalizar)}* — finalizar`,
      chatId
    );
    return;
  }

  delete dados.aguardando_item;
  carrinho.push(...itens);
  dados.carrinho = carrinho;
  setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, dados, chatId);

  const confirmacao = itens.length === 1
    ? `✅ ${itens[0].quantidade}un ${itens[0].produto.nome} adicionado!`
    : `✅ ${itens.length} itens adicionados!\n${itens.map((i) => `• ${i.quantidade}un ${i.produto.nome}`).join('\n')}`;

  await enviarMensagem(
    tel,
    `${confirmacao}\n\n${getResumoCarrinhoTexto(carrinho)}\n\nAdicione mais ou digite *${emojiNumero(opcoes.finalizar)}* para finalizar`,
    chatId
  );
}

async function handlePedidoNome(tel, mensagem, dados, chatId) {
  dados.nome = mensagem.trim();
  if (dados.tipo_entrega === 'retirada') {
    setSessao(tel, ESTADOS.PEDIDO_TELEFONE, dados, chatId);
    await enviarMensagem(tel, `Prazer, ${dados.nome}! 😊\n\nQual é o seu *telefone* para contato?`, chatId);
    return;
  }
  setSessao(tel, ESTADOS.PEDIDO_ENDERECO, dados, chatId);
  await enviarMensagem(tel, `Prazer, ${dados.nome}! 😊\n\nQual é o seu *endereço* para entrega?`, chatId);
}

async function handlePedidoEndereco(tel, mensagem, dados, chatId) {
  dados.endereco = mensagem.trim();
  setSessao(tel, ESTADOS.PEDIDO_TELEFONE, dados, chatId);
  await enviarMensagem(tel, 'Qual é o seu *telefone* para contato?', chatId);
}

async function handlePedidoTelefone(tel, mensagem, dados, chatId) {
  dados.telefone = mensagem.replace(/\D/g, '') || normalizePhoneKey(tel);

  const cliente = clienteRepo.findOrCreate({
    nome: dados.nome,
    telefone: dados.telefone,
    endereco: dados.tipo_entrega === 'retirada' ? null : dados.endereco
  });
  dados.cliente_id = cliente.id;

  if (dados.atualizar_cadastro) {
    delete dados.atualizar_cadastro;
  }

  await irParaEscolherPagamento(tel, dados, chatId);
}

async function enviarResumo(tel, dados, chatId) {
  const resumo = calcularResumo(dados.carrinho || [], dados);
  let texto = '📝 *RESUMO DO PEDIDO*\n\n';

  resumo.itens.forEach((item) => {
    texto += `• ${item.quantidade}un ${item.nome_produto} — R$ ${item.subtotal.toFixed(2)}\n`;
  });

  if (dados.tipo_entrega === 'retirada') {
    texto += '\n🏪 *Retirada na loja* (sem taxa de entrega)';
  } else if (resumo.taxa_entrega > 0) {
    texto += `\n📦 Entrega: R$ ${resumo.taxa_entrega.toFixed(2)}`;
  }
  texto += `\n💰 *TOTAL: R$ ${resumo.valor_total.toFixed(2)}*`;
  texto += `\n\n👤 ${dados.nome}`;
  if (dados.tipo_entrega === 'retirada') {
    texto += `\n📍 ${getEnderecoLoja() || 'Retirada no local'}`;
  } else {
    texto += `\n📍 ${dados.endereco}`;
  }
  texto += `\n📱 ${dados.telefone}`;
  if (dados.forma_pagamento) {
    texto += `\n💳 Pagamento: *${dados.forma_pagamento}*`;
  }
  if (dados.forma_pagamento === 'Dinheiro' && dados.valor_pago_dinheiro) {
    texto += `\n💵 Valor pago: ${fmtMoeda(dados.valor_pago_dinheiro)}`;
    if (dados.troco > 0) {
      texto += `\n🔄 Troco: *${fmtMoeda(dados.troco)}*`;
    }
  }

  await enviarMensagem(tel, texto, chatId);
}

async function handleConfirmar(tel, opcao, dados, chatId) {
  if (opcao === '1') {
    const forma = dados.forma_pagamento || 'Dinheiro';
    const { pedido, resumo } = await criarPedido(tel, dados, chatId, forma);
    const msgFinal = configRepo.getConfig('mensagem_final') || 'Obrigado pela preferência!';
    const retirada = dados.tipo_entrega === 'retirada';

    if (forma === 'PIX') {
      const chavePix = (configRepo.getConfig('pix') || '').trim();
      const copiaECola = gerarPixCopiaECola({
        chave: chavePix,
        nome: configRepo.getConfig('nome_empresa') || 'Iona Salgados',
        cidade: configRepo.getConfig('cidade') || 'Uberlandia',
        valor: resumo.valor_total,
        txid: '***'
      });

      await enviarMensagem(
        tel,
        `✅ *Pedido #${pedido.numero} recebido e enviado para produção!*\n\n💠 *Pagamento via PIX — ${fmtMoeda(resumo.valor_total)}*\n\nCopie o código abaixo e pague no app do seu banco (opção *PIX Copia e Cola*):`,
        chatId
      );
      await enviarMensagem(tel, copiaECola, chatId);
      await enviarMensagem(tel, `Depois de pagar, é só aguardar. 🥟\n\n${msgFinal}`, chatId);
    } else {
      let msgDinheiro = retirada
        ? `Pague em *dinheiro* na retirada${getEnderecoLoja() ? `:\n📍 ${getEnderecoLoja()}` : '.'}`
        : 'Pague em *dinheiro* na entrega.';
      if (dados.valor_pago_dinheiro) {
        msgDinheiro += `\n💵 Valor informado: *${fmtMoeda(dados.valor_pago_dinheiro)}*`;
      }
      if (dados.troco > 0) {
        msgDinheiro += `\n🔄 Troco: *${fmtMoeda(dados.troco)}*`;
      }
      await enviarMensagem(
        tel,
        `✅ *Pedido #${pedido.numero} recebido e enviado para produção!*\n\n${msgDinheiro}\n\nTotal: *${fmtMoeda(resumo.valor_total)}*\n\n${msgFinal}`,
        chatId
      );
    }

    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { chat_id: dados.chat_id, carrinho: [] }, chatId);
  } else if (opcao === '2') {
    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { chat_id: dados.chat_id, carrinho: [] }, chatId);
    await enviarMensagem(tel, 'Pedido cancelado. Digite *OI* para fazer um novo pedido.', chatId);
  } else {
    await enviarMensagem(tel, `Digite *${emojiNumero(1)}* para confirmar ou *${emojiNumero(2)}* para cancelar.`, chatId);
  }
}

async function criarPedido(tel, dados, chatId, formaPagamento) {
  const resumo = calcularResumo(dados.carrinho || [], dados);

  const pedido = pedidoRepo.create({
    cliente_id: dados.cliente_id,
    status: 'novo',
    valor_itens: resumo.valor_itens,
    taxa_entrega: resumo.taxa_entrega,
    valor_total: resumo.valor_total,
    forma_pagamento: formaPagamento || dados.forma_pagamento || null,
    troco: dados.troco || 0,
    endereco: enderecoNoPedido(dados),
    observacoes: dados.tipo_entrega === 'retirada' ? 'Retirada na loja' : null,
    origem: 'whatsapp',
    whatsapp_chat_id: dados.chat_id || chatId,
    itens: resumo.itens
  });

  clienteRepo.atualizarEstatisticas(dados.cliente_id, resumo.valor_total);

  if (io) {
    io.emit('novoPedido', pedido);
    io.emit('imprimirPedido', pedido);
    pushService.notifyNovoPedido(pedido);
    console.log(`Pedido #${pedido.numero} criado via WhatsApp — ${pedido.itens?.length || 0} itens (${formaPagamento})`);
  }

  return { pedido, resumo };
}

function iniciarConfirmacaoEntrega(telefone, pedido, chatId, socketIo) {
  if (socketIo) io = socketIo;
  setSessao(telefone, ESTADOS.CONFIRMAR_ENTREGA, {
    pedido_id: pedido.id,
    pedido_numero: pedido.numero,
    chat_id: chatId
  }, chatId);
}

async function handleConfirmarEntrega(tel, texto, dados, chatId) {
  const opcao = normalizarTextoPedido(texto).replace(/\D/g, '');

  if (opcao === '1') {
    const atual = pedidoRepo.findById(dados.pedido_id);
    const pedido = atual?.status === 'saiu_entrega'
      ? pedidoRepo.updateStatus(dados.pedido_id, 'finalizado')
      : atual;
    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { chat_id: chatId || dados.chat_id, carrinho: [] }, chatId);
    await enviarMensagem(tel, '✅ Obrigado! Pedido confirmado como recebido. Bom apetite! 🥟', chatId);

    if (io && pedido) {
      io.emit('pedidoRecebido', pedido);
      io.emit('pedidoAtualizado', pedido);
      io.emit('pedidoFinalizado', pedido);
      pushService.notifyPedidoRecebido(pedido);
      console.log(`Pedido #${pedido.numero} confirmado como recebido pelo cliente`);
    }
    return;
  }

  if (opcao === '2') {
    await enviarMensagem(tel, '🛵 Sem problemas! Quando receber, responda *1* ou *1️⃣* para confirmar.', chatId);
    return;
  }

  await enviarMensagem(tel, 'Responda:\n\n1️⃣ Recebi o pedido\n2️⃣ Ainda não recebi', chatId);
}

module.exports = { init, processarMensagem, enviarMensagem, iniciarConfirmacaoEntrega };

const { getDb } = require('../database/db');
const pushService = require('./pushService');
const produtoRepo = require('../repositories/produtoRepository');
const clienteRepo = require('../repositories/clienteRepository');
const pedidoRepo = require('../repositories/pedidoRepository');
const configRepo = require('../repositories/configRepository');
const { parseQuantidadeNatural, parseMultiplosItens, matchProdutoCatalogo } = require('../utils/nlpParser');
const { isChatIdValido } = require('../utils/whatsappChat');

let whatsappClient = null;
let io = null;

const ESTADOS = {
  ESCOLHENDO_ITENS: 'escolhendo_itens',
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
  if (chatId && isChatIdValido(chatId)) return chatId;
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

  if (!sessao && chatId) {
    sessao = getDb().prepare(`
      SELECT * FROM sessoes_whatsapp
      WHERE estado = ? AND json_extract(dados, '$.chat_id') = ?
      ORDER BY updated_at DESC LIMIT 1
    `).get(ESTADOS.CONFIRMAR_ENTREGA, chatId);
  }

  if (!sessao) {
    getDb().prepare('INSERT INTO sessoes_whatsapp (telefone, estado, dados) VALUES (?, ?, ?)').run(key, ESTADOS.ESCOLHENDO_ITENS, '{}');
    sessao = { telefone: key, estado: ESTADOS.ESCOLHENDO_ITENS, dados: '{}' };
  }
  return { ...sessao, dados: JSON.parse(sessao.dados || '{}') };
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
  return dados.chat_id || `${telefone.replace(/\D/g, '')}@c.us`;
}

async function enviarMensagem(telefone, texto, chatId = null) {
  if (!whatsappClient) {
    console.warn('WhatsApp client não disponível');
    return;
  }
  const sessao = getSessao(telefone, chatId);
  const to = chatId || getChatId(telefone, sessao.dados);
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

function calcularResumo(carrinho, dados) {
  const taxaEntrega = parseFloat(configRepo.getConfig('taxa_entrega') || '0');
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

  if (dados.nome && dados.endereco && dados.telefone) {
    const cliente = clienteRepo.findOrCreate({
      nome: dados.nome,
      telefone: dados.telefone,
      endereco: dados.endereco
    });
    dados.cliente_id = cliente.id;
    setSessao(tel, ESTADOS.CONFIRMAR, dados, chatId);
    await enviarResumo(tel, dados, chatId);
    await enviarMensagem(tel, '✅ *Confirma o pedido?*\n\n1️⃣ Sim\n2️⃣ Não', chatId);
    return;
  }

  if (dados.cliente_cadastrado) {
    const c = dados.cliente_cadastrado;
    setSessao(tel, ESTADOS.CONFIRMAR_DADOS, dados, chatId);
    await enviarMensagem(
      tel,
      `${getResumoCarrinhoTexto(carrinho)}\n\n📋 *Seus dados do último pedido:*\n👤 ${c.nome}\n📍 ${c.endereco}\n📱 ${c.telefone}\n\nSão os mesmos?\n\n1️⃣ Sim\n2️⃣ Não`,
      chatId
    );
    return;
  }

  setSessao(tel, ESTADOS.PEDIDO_NOME, dados, chatId);
  await enviarMensagem(tel, `${getResumoCarrinhoTexto(carrinho)}\n\nQual é o seu *nome*?`, chatId);
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
    dados.endereco = c.endereco;
    dados.telefone = c.telefone;
    delete dados.cliente_cadastrado;
    const cliente = clienteRepo.findOrCreate({
      nome: dados.nome,
      telefone: dados.telefone,
      endereco: dados.endereco
    });
    dados.cliente_id = cliente.id;
    setSessao(tel, ESTADOS.CONFIRMAR, dados, chatId);
    await enviarResumo(tel, dados, chatId);
    await enviarMensagem(tel, '✅ *Confirma o pedido?*\n\n1️⃣ Sim\n2️⃣ Não', chatId);
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

async function processarMensagem(telefone, mensagem, chatId) {
  const tel = telefone.replace(/\D/g, '');
  const sessao = getSessao(telefone, chatId);
  const dados = { ...sessao.dados, chat_id: chatId || sessao.dados.chat_id };
  const texto = mensagem.trim();
  const textoLower = texto.toLowerCase();

  salvarMensagem(tel, 'entrada', mensagem, dados.cliente_id);

  if (/^(ola|oi|olá|hey|menu|cardapio|cardápio|inicio|início|bom dia|boa tarde|boa noite)$/i.test(textoLower)) {
    const emFluxo = [
      ESTADOS.CONFIRMAR_ENTREGA,
      ESTADOS.CONFIRMAR,
      ESTADOS.CONFIRMAR_DADOS,
      ESTADOS.PEDIDO_NOME,
      ESTADOS.PEDIDO_ENDERECO,
      ESTADOS.PEDIDO_TELEFONE
    ].includes(sessao.estado);
    if (!emFluxo) {
      await iniciarAtendimento(tel, dados, chatId);
      return;
    }
  }

  switch (sessao.estado) {
    case ESTADOS.ESCOLHENDO_ITENS:
      await handleEscolhendoItens(tel, texto, textoLower, { ...sessao, dados }, chatId);
      break;
    case ESTADOS.CONFIRMAR_DADOS:
      await handleConfirmarDados(tel, texto, dados, chatId);
      break;
    case ESTADOS.PEDIDO_NOME:
      await handlePedidoNome(tel, texto, dados, chatId);
      break;
    case ESTADOS.PEDIDO_ENDERECO:
      await handlePedidoEndereco(tel, texto, dados, chatId);
      break;
    case ESTADOS.PEDIDO_TELEFONE:
      await handlePedidoTelefone(tel, texto, dados, chatId);
      break;
    case ESTADOS.CONFIRMAR:
      await handleConfirmar(tel, normalizarTextoPedido(texto).replace(/\D/g, ''), dados, chatId);
      break;
    case ESTADOS.CONFIRMAR_ENTREGA:
      await handleConfirmarEntrega(tel, texto, dados, chatId);
      break;
    default:
      await iniciarCardapio(tel, dados, chatId);
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
    endereco: dados.endereco
  });
  dados.cliente_id = cliente.id;

  if (dados.atualizar_cadastro) {
    delete dados.atualizar_cadastro;
    setSessao(tel, ESTADOS.CONFIRMAR, dados, chatId);
    await enviarResumo(tel, dados, chatId);
    await enviarMensagem(tel, '✅ *Confirma o pedido?*\n\n1️⃣ Sim\n2️⃣ Não', chatId);
    return;
  }

  setSessao(tel, ESTADOS.CONFIRMAR, dados, chatId);
  await enviarResumo(tel, dados, chatId);
  await enviarMensagem(tel, '✅ *Confirma o pedido?*\n\n1️⃣ Sim\n2️⃣ Não', chatId);
}

async function enviarResumo(tel, dados, chatId) {
  const resumo = calcularResumo(dados.carrinho || [], dados);
  let texto = '📝 *RESUMO DO PEDIDO*\n\n';

  resumo.itens.forEach((item) => {
    texto += `• ${item.quantidade}un ${item.nome_produto} — R$ ${item.subtotal.toFixed(2)}\n`;
  });

  if (resumo.taxa_entrega > 0) {
    texto += `\n📦 Entrega: R$ ${resumo.taxa_entrega.toFixed(2)}`;
  }
  texto += `\n💰 *TOTAL: R$ ${resumo.valor_total.toFixed(2)}*`;
  texto += `\n\n👤 ${dados.nome}`;
  texto += `\n📍 ${dados.endereco}`;
  texto += `\n📱 ${dados.telefone}`;

  await enviarMensagem(tel, texto, chatId);
}

async function handleConfirmar(tel, opcao, dados, chatId) {
  if (opcao === '1') {
    const resumo = calcularResumo(dados.carrinho || [], dados);

    const pedido = pedidoRepo.create({
      cliente_id: dados.cliente_id,
      status: 'novo',
      valor_itens: resumo.valor_itens,
      taxa_entrega: resumo.taxa_entrega,
      valor_total: resumo.valor_total,
      endereco: dados.endereco,
      origem: 'whatsapp',
      whatsapp_chat_id: dados.chat_id || chatId,
      itens: resumo.itens
    });

    clienteRepo.atualizarEstatisticas(dados.cliente_id, resumo.valor_total);

    const msgFinal = configRepo.getConfig('mensagem_final') || 'Obrigado pela preferência!';
    await enviarMensagem(tel, `✅ *Pedido #${pedido.numero} recebido!*\n\nSeu pedido já foi enviado para produção.\n\n${msgFinal}`, chatId);

    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { chat_id: dados.chat_id, carrinho: [] }, chatId);

    if (io) {
      io.emit('novoPedido', pedido);
      io.emit('imprimirPedido', pedido);
      pushService.notifyNovoPedido(pedido);
      console.log(`Pedido #${pedido.numero} criado via WhatsApp — ${pedido.itens?.length || 0} itens`);
    }
  } else if (opcao === '2') {
    setSessao(tel, ESTADOS.ESCOLHENDO_ITENS, { chat_id: dados.chat_id, carrinho: [] }, chatId);
    await enviarMensagem(tel, 'Pedido cancelado. Digite *OI* para fazer um novo pedido.', chatId);
  } else {
    await enviarMensagem(tel, `Digite *${emojiNumero(1)}* para confirmar ou *${emojiNumero(2)}* para cancelar.`, chatId);
  }
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

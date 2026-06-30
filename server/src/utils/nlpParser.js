/**
 * Parser de linguagem natural para quantidades e produtos
 * Entende: "100 coxinhas", "350 coxinhas 400 enroladinho", "2 100", etc.
 */

const NUMEROS_POR_EXTENSO = {
  um: 1, uma: 1, dois: 2, duas: 2, tres: 3, três: 3,
  quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9,
  dez: 10, onze: 11, doze: 12, treze: 13, quatorze: 14, catorze: 14,
  quinze: 15, dezesseis: 16, dezessete: 17, dezoito: 18, dezenove: 19,
  vinte: 20, trinta: 30, quarenta: 40, cinquenta: 50,
  sessenta: 60, setenta: 70, oitenta: 80, noventa: 90,
  cem: 100, cento: 100, duzentos: 200, trezentos: 300,
  quatrocentos: 400, quinhentos: 500
};

const PRODUTOS_COMUNS = [
  'coxinha', 'coxinhas', 'kibe', 'kibes', 'quibe', 'quibes',
  'risole', 'risoles', 'bolinha', 'bolinhas', 'pastel', 'pasteis',
  'empada', 'empadas', 'esfiha', 'esfihas', 'enroladinho', 'enroladinhos',
  'salgado', 'salgados', 'mini', 'doce', 'doces', 'bolo', 'bolos'
];

function normalizarTexto(texto) {
  return String(texto).toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

function normalizarNome(nome) {
  return normalizarTexto(nome).replace(/s$/, '');
}

function matchProdutoCatalogo(termo, produtos = []) {
  const alvo = normalizarNome(termo);
  if (!alvo || !produtos.length) return null;

  return produtos.find((p) => {
    const nome = normalizarNome(p.nome);
    return nome.includes(alvo) || alvo.includes(nome);
  }) || null;
}

function parseQuantidadeNatural(texto, produtos = []) {
  const normalizado = normalizarTexto(texto);

  const matchNumero = normalizado.match(/(\d+)\s+([a-z]+)/);
  if (matchNumero) {
    const quantidade = parseInt(matchNumero[1], 10);
    const termo = matchNumero[2];
    const produto = matchProdutoCatalogo(termo, produtos);
    if (produto) return { quantidade, produto: produto.nome, produtoObj: produto };

    const produtoComum = PRODUTOS_COMUNS.find((p) => termo.includes(p) || p.includes(termo));
    if (produtoComum) return { quantidade, produto: produtoComum };
  }

  const palavras = normalizado.split(/\s+/);
  for (let i = 0; i < palavras.length - 1; i++) {
    const qtd = NUMEROS_POR_EXTENSO[palavras[i]];
    if (qtd) {
      const termo = palavras.slice(i + 1).join(' ');
      const produto = matchProdutoCatalogo(termo, produtos);
      if (produto) return { quantidade: qtd, produto: produto.nome, produtoObj: produto };
    }
  }

  const matchDesejo = normalizado.match(
    /(?:quero|preciso|gostaria|pode separar|me manda|me envia)\s+(?:de\s+)?(\d+|\w+)\s+([a-z]+)/
  );
  if (matchDesejo) {
    let qtd = parseInt(matchDesejo[1], 10);
    if (Number.isNaN(qtd)) qtd = NUMEROS_POR_EXTENSO[matchDesejo[1]];
    if (qtd) {
      const produto = matchProdutoCatalogo(matchDesejo[2], produtos);
      if (produto) return { quantidade: qtd, produto: produto.nome, produtoObj: produto };
    }
  }

  return null;
}

function parseMultiplosItens(texto, produtos = []) {
  const normalizado = normalizarTexto(texto);
  const itens = [];
  const regex = /(\d+)\s+([a-z]+)/gi;
  let match;

  while ((match = regex.exec(normalizado)) !== null) {
    const quantidade = parseInt(match[1], 10);
    const termo = match[2];
    if (quantidade <= 0 || !termo) continue;

    const produto = matchProdutoCatalogo(termo, produtos);
    if (produto) {
      itens.push({ produto, quantidade });
    }
  }

  return itens;
}

module.exports = {
  parseQuantidadeNatural,
  parseMultiplosItens,
  matchProdutoCatalogo,
  NUMEROS_POR_EXTENSO,
  PRODUTOS_COMUNS
};

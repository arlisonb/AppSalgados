function toIntBool(value, defaultValue = 1) {
  if (value === undefined || value === null) return defaultValue;
  if (typeof value === 'boolean') return value ? 1 : 0;
  return Number(value) ? 1 : 0;
}

function normalizeProduto(data = {}) {
  const categoriaId = data.categoria_id ?? data.categoriaId;
  return {
    nome: data.nome,
    categoria_id: categoriaId && Number(categoriaId) > 0 ? Number(categoriaId) : null,
    categoria_nome: data.categoria_nome ?? data.categoriaNome ?? null,
    descricao: data.descricao ?? null,
    imagem: data.imagem ?? null,
    preco_unidade: Number(data.preco_unidade ?? data.precoUnidade ?? 0),
    preco_cento: Number(data.preco_cento ?? data.precoCento ?? 0),
    preco_cento_cinquenta: Number(data.preco_cento_cinquenta ?? data.precoCentoCinquenta ?? 0),
    preco_duzentos: Number(data.preco_duzentos ?? data.precoDuzentos ?? 0),
    preco_trezentos: Number(data.preco_trezentos ?? data.precoTrezentos ?? 0),
    preco_quinhentos: Number(data.preco_quinhentos ?? data.precoQuinhentos ?? 0),
    preco_personalizado: Number(data.preco_personalizado ?? data.precoPersonalizado ?? 0),
    tempo_preparo: Number(data.tempo_preparo ?? data.tempoPreparo ?? 30),
    ativo: toIntBool(data.ativo, 1),
    observacao_padrao: data.observacao_padrao ?? data.observacaoPadrao ?? null,
    ordem: Number(data.ordem ?? 0)
  };
}

function normalizeCategoria(data = {}) {
  return {
    nome: data.nome,
    ordem: Number(data.ordem ?? 0),
    ativo: toIntBool(data.ativo, 1)
  };
}

module.exports = { toIntBool, normalizeProduto, normalizeCategoria };

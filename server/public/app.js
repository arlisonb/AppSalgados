const API = '/api';
let currentView = 'home';
let pedidoSelecionado = null;
let socket = null;

const titles = {
  home: ['Iona Salgados', ''],
  pedidos: ['Pedidos', ''],
  dashboard: ['Painel', ''],
  producao: ['Produção', ''],
  cardapio: ['Cardápio', ''],
  clientes: ['Clientes', ''],
  whatsapp: ['WhatsApp', ''],
  config: ['Configurações', '']
};

const statusLabels = {
  novo: 'Novo', confirmado: 'Confirmado', preparando: 'Preparando',
  pronto: 'Pronto', saiu_entrega: 'Saiu p/ entrega', finalizado: 'Finalizado', cancelado: 'Cancelado'
};

async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `Erro ${res.status}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 3000);
}

function money(v) {
  return 'R$ ' + Number(v || 0).toFixed(2);
}

function navigate(view) {
  currentView = view;
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  document.getElementById('view-' + view).classList.add('active');
  const [title, sub] = titles[view] || ['Iona Salgados', ''];
  document.getElementById('page-title').textContent = title;
  document.getElementById('page-subtitle').textContent = sub;
  document.getElementById('btn-back').classList.toggle('hidden', view === 'home');
  loadView(view);
}

async function loadView(view) {
  try {
    if (view === 'pedidos') await loadPedidos();
    if (view === 'dashboard') await loadDashboard();
    if (view === 'producao') await loadProducao();
    if (view === 'cardapio') await loadCardapio();
    if (view === 'clientes') await loadClientes();
    if (view === 'whatsapp') await loadWhatsApp();
    if (view === 'config') await loadConfig();
    if (view === 'home') await checkStatus();
  } catch (e) {
    toast(e.message);
  }
}

async function checkStatus() {
  try {
    const s = await api('/status');
    setOnline(true);
    const wa = s.whatsapp?.status || '';
    const ok = ['conectado', 'isLogged', 'inChat'].includes(wa);
    document.getElementById('page-subtitle').textContent = ok ? 'WhatsApp conectado' : (wa ? `WhatsApp: ${wa}` : '');
  } catch {
    setOnline(false);
  }
}

function setOnline(ok) {
  const b = document.getElementById('badge-online');
  b.textContent = ok ? 'Online' : 'Offline';
  b.className = 'badge ' + (ok ? 'online' : 'offline');
}

function connectSocket() {
  if (socket) return;
  socket = io();
  socket.on('connect', () => setOnline(true));
  socket.on('disconnect', () => setOnline(false));
  socket.on('novoPedido', () => {
    toast('🔔 Novo pedido!');
    if (currentView === 'pedidos') loadPedidos();
    if (currentView === 'home') checkStatus();
    if (currentView === 'dashboard') loadDashboard();
  });
  socket.on('pedidoAtualizado', () => {
    if (currentView === 'pedidos') loadPedidos();
    if (currentView === 'dashboard') loadDashboard();
  });
  socket.on('pedidoRecebido', (pedido) => {
    toast(`✅ Pedido #${pedido?.numero || ''} recebido pelo cliente!`);
    if (currentView === 'pedidos') loadPedidos();
    if (currentView === 'home') checkStatus();
  });
  socket.on('statusWhatsApp', (data) => {
    if (currentView === 'whatsapp') updateWhatsAppUI(data);
    if (currentView === 'home') checkStatus();
  });
  socket.on('codigoWhatsApp', (data) => {
    if (data?.code) showWaCode(data.code);
  });
}

async function loadPedidos() {
  const pedidos = await api('/pedidos');
  const el = document.getElementById('pedidos-list');
  const det = document.getElementById('pedido-detalhe');
  det.classList.add('hidden');
  pedidoSelecionado = null;

  const ativos = pedidos.filter(p => !['finalizado', 'cancelado'].includes(p.status));
  if (!ativos.length) {
    el.innerHTML = '<p class="empty">Nenhum pedido ativo</p>';
    return;
  }
  el.innerHTML = ativos.map(p => `
    <div class="card" data-id="${p.id}">
      <h3>#${p.numero} — ${p.cliente_nome || 'Cliente'}</h3>
      <div class="meta">${statusLabels[p.status] || p.status} · ${p.origem || 'app'}</div>
      <div class="valor">${money(p.valor_total)}</div>
    </div>
  `).join('');
  el.querySelectorAll('.card').forEach(c => {
    c.onclick = () => abrirPedido(Number(c.dataset.id));
  });
}

async function abrirPedido(id) {
  const p = await api('/pedidos/' + id);
  pedidoSelecionado = p;
  document.getElementById('pedidos-list').classList.add('hidden');
  const det = document.getElementById('pedido-detalhe');
  det.classList.remove('hidden');
  const itens = (p.itens || []).map(i =>
    `<li>${i.quantidade}un ${i.nome_produto} — ${money(i.subtotal)}</li>`
  ).join('');
  det.innerHTML = `
    <button class="btn sm outline" id="btn-voltar-pedidos">← Voltar</button>
    <h2>Pedido #${p.numero}</h2>
    <p><strong>${p.cliente_nome}</strong></p>
    <p class="meta">📱 ${p.cliente_telefone || '-'}<br>📍 ${p.endereco || '-'}</p>
    <ul>${itens || '<li>Sem itens</li>'}</ul>
    <p class="valor">Total: ${money(p.valor_total)}</p>
    <div class="status-btns">
      ${p.status === 'finalizado'
        ? `<p class="meta">✅ Entregue e confirmado pelo cliente</p>
           <button class="btn sm outline" id="btn-imprimir">🖨 Imprimir</button>`
        : p.status === 'saiu_entrega'
        ? `<p class="meta">🛵 Aguardando confirmação do cliente no WhatsApp</p>
           <div class="btn-row">
             <button class="btn sm outline" id="btn-imprimir">🖨 Imprimir</button>
             <button class="btn sm primary" data-status="finalizado">Marcar como entregue</button>
           </div>`
        : `<div class="btn-row">
             <button class="btn sm outline" id="btn-imprimir">🖨 Imprimir</button>
             <button class="btn sm primary" data-status="saiu_entrega">${statusLabels.saiu_entrega}</button>
           </div>`
      }
    </div>
  `;
  det.querySelector('#btn-voltar-pedidos').onclick = () => {
    det.classList.add('hidden');
    document.getElementById('pedidos-list').classList.remove('hidden');
    loadPedidos();
  };
  const btnImprimir = det.querySelector('#btn-imprimir');
  if (btnImprimir) {
    btnImprimir.onclick = async () => {
      try {
        await api('/pedidos/' + id + '/imprimir', { method: 'POST' });
        toast('Enviado para impressão!');
      } catch (err) {
        toast(err.message);
      }
    };
  }
  det.querySelectorAll('[data-status]').forEach(btn => {
    btn.onclick = async () => {
      try {
        await api('/pedidos/' + id + '/status', {
          method: 'PATCH',
          body: JSON.stringify({ status: btn.dataset.status })
        });
        toast(btn.dataset.status === 'finalizado' ? 'Pedido marcado como entregue!' : 'Cliente avisado no WhatsApp!');
        abrirPedido(id);
      } catch (err) {
        toast(err.message);
      }
    };
  });
}

let pedidosHojeAbertos = false;

async function loadDashboard() {
  const d = await api('/dashboard');
  pedidosHojeAbertos = false;
  const pedidosEl = document.getElementById('dashboard-pedidos');
  pedidosEl.classList.add('hidden');
  pedidosEl.innerHTML = '';

  document.getElementById('dashboard-cards').innerHTML = `
    <div class="stat-card stat-card-clickable" id="card-pedidos-hoje">
      <div class="num">${d.pedidos_hoje ?? 0}</div><div class="lbl">Pedidos hoje</div>
    </div>
    <div class="stat-card"><div class="num">${money(d.valor_mes)}</div><div class="lbl">Faturamento mensal</div></div>
    <div class="stat-card"><div class="num">${money(d.valor_hoje)}</div><div class="lbl">Faturamento diário</div></div>
    <div class="stat-card"><div class="num">${d.finalizados ?? 0}</div><div class="lbl">Finalizados</div></div>
    <div class="stat-card"><div class="num">${d.clientes_atendidos ?? 0}</div><div class="lbl">Clientes</div></div>
  `;

  document.getElementById('card-pedidos-hoje').onclick = () => togglePedidosHoje();
}

async function togglePedidosHoje() {
  const el = document.getElementById('dashboard-pedidos');
  const card = document.getElementById('card-pedidos-hoje');
  if (pedidosHojeAbertos) {
    pedidosHojeAbertos = false;
    el.classList.add('hidden');
    card?.classList.remove('active');
    return;
  }
  pedidosHojeAbertos = true;
  card?.classList.add('active');
  await loadPedidosHoje();
  el.classList.remove('hidden');
}

async function loadPedidosHoje() {
  const hoje = new Date().toISOString().split('T')[0];
  const pedidos = await api('/pedidos?data=' + hoje);
  const el = document.getElementById('dashboard-pedidos');
  if (!pedidos.length) {
    el.innerHTML = '<h3 class="section-title">Pedidos de hoje</h3><p class="empty">Nenhum pedido hoje</p>';
    return;
  }
  el.innerHTML = `
    <h3 class="section-title">Pedidos de hoje</h3>
    ${pedidos.map(p => `
      <div class="card card-row">
        <div class="card-body">
          <h3>#${p.numero} — ${p.cliente_nome || 'Cliente'}</h3>
          <div class="meta">${statusLabels[p.status] || p.status}${p.created_at ? ' · ' + p.created_at.substring(11, 16) : ''}</div>
          <div class="valor">${money(p.valor_total)}</div>
        </div>
        <button class="btn sm outline btn-imprimir-pedido" data-id="${p.id}" title="Imprimir">🖨</button>
      </div>
    `).join('')}
  `;
  el.querySelectorAll('.btn-imprimir-pedido').forEach(btn => {
    btn.onclick = async (e) => {
      e.stopPropagation();
      try {
        await api('/pedidos/' + btn.dataset.id + '/imprimir', { method: 'POST' });
        toast('Enviado para impressão!');
      } catch (err) {
        toast(err.message);
      }
    };
  });
}

async function loadProducao() {
  const p = await api('/producao');
  const el = document.getElementById('producao-content');
  let html = '<h3 style="margin-bottom:10px">Total por produto</h3>';
  if (p.porProduto?.length) {
    html += p.porProduto.map(i => `
      <div class="card"><h3>${i.nome_produto}</h3><div class="valor">${i.quantidade_total} un</div></div>
    `).join('');
  } else {
    html += '<p class="empty">Nada em produção hoje</p>';
  }
  html += '<h3 style="margin:16px 0 10px">Pedidos do dia</h3>';
  if (p.porCliente?.length) {
    html += p.porCliente.map(c => `
      <div class="card"><h3>#${c.numero} ${c.cliente_nome}</h3><div class="meta">${c.status}</div></div>
    `).join('');
  }
  el.innerHTML = html;
}

function formatPrecosProduto(p) {
  const linhas = [];
  const unidade = p.preco_unidade > 0 ? p.preco_unidade : (p.preco_cento > 0 ? p.preco_cento / 100 : 0);
  if (unidade > 0) linhas.push(`Unidade: ${money(unidade)}`);
  if (p.preco_cento > 0) linhas.push(`Cento (100un): ${money(p.preco_cento)}`);
  return linhas.join(' · ') || 'Sem preço';
}

async function loadCardapio() {
  const produtos = await api('/produtos?ativo=true');
  const el = document.getElementById('cardapio-list');
  if (!produtos.length) {
    el.innerHTML = '<p class="empty">Nenhum produto cadastrado</p>';
    return;
  }
  el.innerHTML = produtos.map(p => `
    <div class="card card-row" data-id="${p.id}">
      <div class="card-body">
        <h3>${p.nome}</h3>
        <div class="meta">${p.categoria_nome || ''}</div>
        <div class="meta">${formatPrecosProduto(p)}</div>
      </div>
      <button class="btn sm danger btn-del-produto" data-id="${p.id}" title="Excluir">🗑</button>
    </div>
  `).join('');
  el.querySelectorAll('.btn-del-produto').forEach(btn => {
    btn.onclick = async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      const nome = btn.closest('.card')?.querySelector('h3')?.textContent || 'produto';
      if (!confirm(`Excluir "${nome}" do cardápio?`)) return;
      try {
        await api('/produtos/' + id, { method: 'DELETE' });
        toast('Produto excluído');
        loadCardapio();
      } catch (err) {
        toast(err.message);
      }
    };
  });
}

async function loadClientes() {
  const clientes = await api('/clientes');
  const el = document.getElementById('clientes-list');
  if (!clientes.length) {
    el.innerHTML = '<p class="empty">Nenhum cliente</p>';
    return;
  }
  el.innerHTML = clientes.map(c => `
    <div class="card card-row" data-id="${c.id}">
      <div class="card-body">
        <h3>${c.nome}</h3>
        <div class="meta">${c.telefone || ''}</div>
        <div class="meta">${c.endereco || ''}</div>
        <div class="meta">${c.qtd_pedidos || 0} pedidos · ${money(c.valor_total)}</div>
      </div>
      <button class="btn sm danger btn-del-cliente" data-id="${c.id}" data-pedidos="${c.qtd_pedidos || 0}" data-ativos="${c.pedidos_ativos || 0}" title="Excluir">🗑</button>
    </div>
  `).join('');
  el.querySelectorAll('.btn-del-cliente').forEach(btn => {
    btn.onclick = async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      const pedidos = Number(btn.dataset.pedidos || 0);
      const ativos = Number(btn.dataset.ativos || 0);
      const nome = btn.closest('.card')?.querySelector('h3')?.textContent || 'cliente';
      if (ativos > 0) {
        toast('Cliente possui pedidos em andamento. Finalize ou cancele antes de excluir.');
        return;
      }
      const msg = pedidos > 0
        ? `"${nome}" tem ${pedidos} pedido(s) no histórico. Remover da lista?`
        : `Excluir "${nome}"?`;
      if (!confirm(msg)) return;
      try {
        await api('/clientes/' + id, { method: 'DELETE' });
        toast('Cliente excluído');
        loadClientes();
      } catch (err) {
        toast(err.message);
      }
    };
  });
}

function updateWhatsAppUI(data) {
  const status = data?.status || data;
  const code = data?.pairingCode || data?.code;
  const ok = ['conectado', 'isLogged', 'inChat'].includes(typeof status === 'string' ? status : status?.status);
  document.getElementById('wa-connected').classList.toggle('hidden', !ok);
  document.getElementById('wa-form').classList.toggle('hidden', ok);
  if (code) showWaCode(code);
}

async function loadWhatsApp() {
  const wa = await api('/whatsapp/status');
  updateWhatsAppUI(wa);
  if (wa.phoneNumber) document.getElementById('wa-telefone').value = wa.phoneNumber;
}

function showWaCode(code) {
  document.getElementById('wa-code-box').classList.remove('hidden');
  document.getElementById('wa-code').textContent = code;
}

async function loadConfig() {
  document.getElementById('config-url').textContent = window.location.origin;
  const cfg = await api('/configuracoes');
  const form = document.getElementById('form-config');
  form.nome_empresa.value = cfg.nome_empresa || '';
  form.telefone.value = cfg.telefone || '';
  form.endereco.value = cfg.endereco || '';
  form.pix.value = cfg.pix || '';
}

document.querySelectorAll('.menu-card').forEach(btn => {
  btn.onclick = () => navigate(btn.dataset.view);
});

document.getElementById('btn-back').onclick = () => navigate('home');
document.getElementById('btn-refresh').onclick = () => loadView(currentView);

document.getElementById('form-produto').onsubmit = async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    await api('/produtos', {
      method: 'POST',
      body: JSON.stringify({
        nome: f.nome.value,
        categoria_nome: f.categoria_nome.value,
        preco_unidade: parseFloat(f.preco_unidade.value) || 0,
        preco_cento: parseFloat(f.preco_cento.value) || 0,
        ativo: true
      })
    });
    toast('Produto salvo!');
    f.reset();
    loadCardapio();
  } catch (err) {
    toast(err.message);
  }
};

document.getElementById('form-config').onsubmit = async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    await api('/configuracoes', {
      method: 'PUT',
      body: JSON.stringify({
        nome_empresa: f.nome_empresa.value,
        telefone: f.telefone.value,
        endereco: f.endereco.value,
        pix: f.pix.value
      })
    });
    toast('Configurações salvas!');
  } catch (err) {
    toast(err.message);
  }
};

document.getElementById('btn-wa-codigo').onclick = async () => {
  const tel = document.getElementById('wa-telefone').value.replace(/\D/g, '');
  if (tel.length < 10) return toast('Informe o número com DDI');
  document.getElementById('wa-msg').textContent = 'Gerando código...';
  try {
    await api('/configuracoes', { method: 'PUT', body: JSON.stringify({ whatsapp: tel }) });
    const wa = await api('/whatsapp/reconectar', { method: 'POST', body: JSON.stringify({ telefone: tel }) });
    if (wa.pairingCode) showWaCode(wa.pairingCode);
    document.getElementById('wa-msg').textContent = wa.pairingCode ? 'Código gerado!' : 'Aguarde o código...';
  } catch (err) {
    document.getElementById('wa-msg').textContent = err.message;
  }
};

document.getElementById('btn-copy-code').onclick = () => {
  const code = document.getElementById('wa-code').textContent;
  navigator.clipboard.writeText(code).then(() => toast('Código copiado!'));
};

document.getElementById('btn-wa-desconectar').onclick = async () => {
  if (!confirm('Desconectar o bot do WhatsApp? Será necessário gerar novo código para reconectar.')) return;
  try {
    await api('/whatsapp/desconectar', { method: 'POST', body: '{}' });
    document.getElementById('wa-code-box').classList.add('hidden');
    document.getElementById('wa-msg').textContent = 'WhatsApp desconectado';
    updateWhatsAppUI({ status: 'desconectado' });
    toast('Bot desconectado');
  } catch (err) {
    toast(err.message);
  }
};

connectSocket();
checkStatus();
navigate('home');

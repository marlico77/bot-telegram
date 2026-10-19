'use strict';
(() => {
  const bridge = window.Marlico;
  const pages = [
    ['overview','home','Visão geral'], ['monitor','monitor','Monitorar PC'],
    ['remote','monitor','Tela remota'], ['processes','list','Processos'],
    ['activity','activity','Atividade'], ['pairing','link','Pareamento'],
    ['settings','settings','Configurações'], ['help','help','Como usar']
  ];
  const model = {
    page:'overview', tab:'metrics', mobileOpen:false, sidebarCollapsed:false,
    state:{configured:false,running:false,state:'off',events:[]},
    metrics:null, processes:null, inventory:null, history:[], range:30,
    processQuery:'',installedQuery:'',activityFilter:'all',
    screenStatus:'idle', screenMessage:'Toque em Iniciar visualização para abrir a tela do computador.',
    live:false, zoom:100, monitor:0, lastFrame:null, expanded:false
  };
  const el = id => document.getElementById(id);
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, c => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  })[c]);
  const icon = (name, size='') => {
    const item=faIcons[name] || faIcons.info;
    return `<svg class="ico ${size}" viewBox="0 0 ${item[0]} ${item[1]}" aria-hidden="true"><path fill="currentColor" stroke="none" d="${item[2]}"></path></svg>`;
  };
  const button = (action,label,glyph='',style='') =>
    `<button type="button" class="btn ${style}" data-action="${action}">${glyph?icon(glyph):''}<span>${label}</span></button>`;
  const heading = (title,description,actions='') =>
    `<div class="page-head"><div><h1>${title}</h1><p>${description}</p></div><div class="page-actions">${actions}</div></div>`;
  const badge = (text,style='') => `<span class="badge ${style}">${esc(text)}</span>`;
  const percent = value => Number.isFinite(Number(value)) && Number(value)>=0 ? Math.round(Number(value))+'%' : '—';
  const clamp = value => Math.max(0,Math.min(100,Number(value)||0));
  function bytes(value) {
    let n=Number(value);
    if (!Number.isFinite(n) || n<0) return '—';
    const unit=['B','KB','MB','GB','TB']; let i=0;
    while(n>=1024 && i<unit.length-1){n/=1024;i++;}
    return (i?n.toFixed(1):Math.round(n))+' '+unit[i];
  }
  function uptime(value){
    const n=Number(value); if(!Number.isFinite(n)||n<0)return '—';
    return Math.floor(n/86400)+'d '+Math.floor(n%86400/3600)+'h';
  }
  function invoke(method,arg){
    if(model.state.updateRequired && !['refresh','checkUpdates','downloadUpdate','installUpdate','stop','stopLiveScreen','frameDisplayed','cancelShutdown'].includes(method))return;
    if(!bridge || typeof bridge[method]!=='function'){window.notify('Esta ação está disponível no aplicativo Android.');return;}
    if(arg===undefined)bridge[method]();else bridge[method](arg);
  }
  let toastTimer;
  window.notify = message => {
    const box=el('toast-container'); if(!box)return;
    box.innerHTML=`<div class="toast">${icon('info','sm')}<span>${esc(message)}</span></div>`;
    clearTimeout(toastTimer);toastTimer=setTimeout(()=>box.replaceChildren(),6000);
  };
  function statusLabel(){
    const value=model.state.state;
    return ({online:'Bot conectado',connecting:'Conectando',retry:'Reconectando',error:'Erro de conexão',off:'Bot pausado'})[value]||'Bot pausado';
  }
  function pcOnline(){return model.state.pcState==='online' && (!model.state.pcStateAt || Date.now()-model.state.pcStateAt<120000);}
  function navItem(item){
    return `<button class="nav-item ${model.page===item[0]?'active':''}" data-nav="${item[0]}" aria-current="${model.page===item[0]?'page':'false'}">${icon(item[1])}<span class="hide-collapsed">${item[2]}</span></button>`;
  }
  function sidebar(){
    return `<aside class="sidebar ${model.sidebarCollapsed?'collapsed':''} ${model.mobileOpen?'mobile-open':''}">
      <div class="brand"><div class="brand-logo"><img src="avatar.png" alt="" aria-hidden="true"></div><div class="hide-collapsed"><strong>MarlicoBot</strong><span>Controle do PC</span></div></div>
      <span class="section-label">Workspace</span>${pages.slice(0,5).map(navItem).join('')}
      <span class="section-label">Gerenciamento</span>${pages.slice(5).map(navItem).join('')}
      <div class="sidebar-bottom"><div class="side-state"><span class="dot ${model.state.state==='online'?'':'red'}"></span><div><div class="bold small">Bot Telegram</div><div class="tiny muted">${esc(model.state.botName?'@'+model.state.botName:statusLabel())}</div></div></div>
      ${button('toggleBot',model.state.running?'Pausar bot':'Conectar bot','power','block ghost')}</div></aside>`;
  }
  function topbar(){
    const title=(pages.find(p=>p[0]===model.page)||pages[0])[2];
    return `<header class="topbar"><div class="topbar-left">
      <button class="iconbtn" data-action="toggleSidebar" aria-label="Abrir ou recolher menu">${icon('menu')}</button>
      <span class="mobile-brand-logo" aria-hidden="true"><img src="avatar.png" alt=""></span>
      <span class="header-divider"></span><div class="crumb"><span class="crumb-root">MarlicoBot</span><span class="crumb-sep">/</span><strong>${title}</strong></div>
      <button class="search-trigger" data-action="search" aria-label="Buscar seção">${icon('search')}<span>Buscar no painel...</span><kbd>Ctrl K</kbd></button>
      </div><div class="top-actions"><span class="pill ${model.state.state==='online'?'':'offline'}"><span class="dot ${model.state.state==='online'?'':'red'}"></span><span class="status-label">${statusLabel()}</span><span class="small">${model.state.state==='online'?'Online':'Offline'}</span></span>
      <button class="iconbtn" data-action="alerts" aria-label="Atividade recente">${icon('bell')}</button><button class="iconbtn" data-nav="settings" aria-label="Configurações">${icon('settings')}</button>
      </div></header>`;
  }
  function bottomNav(){
    return `<nav class="mobile-bottom" aria-label="Navegação inferior">
      ${[['overview','home','Início'],['monitor','monitor','Monitorar'],['activity','activity','Atividade'],['settings','settings','Config.'],['more','menu','Menu']].map(p=>
        `<button class="${model.page===p[0]?'active':''}" data-nav="${p[0]}">${icon(p[1])}<span>${p[2]}</span></button>`).join('')}
    </nav>`;
  }
  function footer(){
    return '<footer class="footer"><span>MarlicoBot · Android · Geist · Font Awesome Free</span><span>Dados do seu dispositivo e do agente Windows</span></footer>';
  }
  function stat(label,value,detail,glyph,color='pink',points=[]){
    const coords=points.length>1?points.map((n,i)=>`${(i/(points.length-1)*135).toFixed(1)},${(23-clamp(n)*.2).toFixed(1)}`).join(' '):'';
    return `<div class="card stat"><div class="s-head"><span class="sicon ${color}">${icon(glyph)}</span><span class="stat-label">${label}</span></div>
      <div class="stat-big number">${esc(value)}</div><div class="stat-under">${esc(detail)}</div>
      ${coords?`<svg class="mini-chart" viewBox="0 0 135 26" preserveAspectRatio="none" aria-hidden="true"><polyline points="${coords}" fill="none" stroke="${color==='green'?'#19d9a2':color==='blue'?'#4daaf7':'#ff39c8'}" stroke-width="2.2"/></svg>`:''}</div>`;
  }
  function chart(values,color,id,first='—',last='—'){
    if(values.length<2)return '<div class="chart-empty">Aguardando leituras do agente Windows.</div>';
    const path=values.map((v,i)=>`${i?'L':'M'} ${(i/(values.length-1)*600).toFixed(1)} ${(165-clamp(v)*1.5).toFixed(1)}`).join(' ');
    return `<div class="chart-area"><div class="chart-y"><span>100%</span><span>75%</span><span>50%</span><span>25%</span><span>0%</span></div>
      <div class="chart-inner"><div class="gridlines"></div><svg viewBox="0 0 600 165" preserveAspectRatio="none" aria-label="Histórico de leituras">
      <defs><linearGradient id="gradient-${id}" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="${color}" stop-opacity=".3"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></linearGradient></defs>
      <path d="${path} L 600 165 L 0 165 Z" fill="url(#gradient-${id})"/><path d="${path}" fill="none" stroke="${color}" stroke-width="2.3"/></svg>
      <div class="chart-x"><span>${esc(first)}</span><span>${esc(last)}</span></div></div></div>`;
  }
  function visibleHistory(){
    const from=Date.now()-model.range*60000;
    return model.history.filter(h=>Number(h.time)>=from);
  }
  function chartCard(title,value,key,color='var(--pink)'){
    const samples=visibleHistory().filter(h=>Number.isFinite(Number(h[key]))&&Number(h[key])>=0);
    const values=samples.map(h=>Number(h[key]));
    const first=samples.length?new Date(samples[0].time).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'—';
    const last=samples.length?new Date(samples[samples.length-1].time).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'—';
    const average=values.length?Math.round(values.reduce((sum,n)=>sum+n,0)/values.length):null;
    const peak=values.length?Math.round(Math.max(...values)):null;
    return `<section class="card pad chart-card"><div class="card-heading"><div><h3 class="card-title">${title}</h3><div class="card-subtitle">Leituras reais armazenadas neste Android</div></div>
      <select class="select-min" data-range aria-label="Intervalo do gráfico"><option value="30" ${model.range===30?'selected':''}>Últimos 30 minutos</option><option value="60" ${model.range===60?'selected':''}>Última hora</option><option value="120" ${model.range===120?'selected':''}>Últimas 2 horas</option></select></div>
      <div class="chart-value number">${esc(value)}</div><div class="chart-hint">${values.length} ${values.length===1?'leitura':'leituras'}${average===null?'':' · Média: '+average+'% · Pico: '+peak+'%'}</div>
      ${chart(values,color,key,first,last)}</section>`;
  }
  function progress(value,color='pink'){
    return `<div class="progress ${color}"><span style="width:${clamp(value)}%"></span></div>`;
  }
  function metricCard(label,value,detail,glyph,color='pink'){
    return `<div class="card metric-card"><div class="s-head"><span class="sicon ${color}">${icon(glyph)}</span><span class="stat-label">${label}</span></div>
      <div class="value number">${esc(value)}</div>${progress(parseFloat(value),color)}<div class="muted small" style="margin-top:7px">${esc(detail)}</div></div>`;
  }
  function events(limit=30){
    const rows=(model.state.events||[]).filter(e=>limit<30||model.activityFilter==='all'||e.kind===model.activityFilter).slice(0,limit);
    if(!rows.length)return '<div class="muted-box">Ainda não há atividades registradas neste dispositivo.</div>';
    return `<div class="table-scroll"><table class="table"><thead><tr><th>Horário</th><th>Tipo</th><th>Atividade</th></tr></thead><tbody>${rows.map(e=>{
      const date=new Date(e.time);const time=Number.isNaN(date.getTime())?'—':date.toLocaleString('pt-BR');
      const label=e.kind==='good'?'Sucesso':e.kind==='warn'?'Aviso':'Comando';
      return `<tr><td class="muted number">${esc(time)}</td><td>${esc(label)}</td><td>${esc(e.text)}</td></tr>`;
    }).join('')}</tbody></table></div>`;
  }
  function pcInfo(){
    const s=model.state,m=model.metrics||{};
    const last=s.lastWake?new Date(s.lastWake).toLocaleString('pt-BR'):'—';
    return `<section class="card pad quick-info"><div class="card-heading"><div><h3 class="card-title">Informações do PC</h3><div class="card-subtitle">Dados configurados e últimas leituras</div></div>
      <button class="btn xs ghost" data-nav="settings">${icon('settings','sm')}Editar</button></div>
      <dl><dt>Computador</dt><dd>${esc(m.computer||'—')}</dd><dt>IP local do PC</dt><dd>${esc(s.pcIp||'—')}</dd><dt>IP Tailscale</dt><dd>${esc(s.tailscaleIp||'—')}</dd><dt>MAC</dt><dd>${esc(s.mac||'—')}</dd>
      <dt>Broadcast e porta WoL</dt><dd>${esc(s.broadcast||'—')}:${esc(s.port||'—')}</dd><dt>Porta do agente</dt><dd>${esc(s.agentPort||'—')}</dd><dt>Monitores</dt><dd>${esc(m.monitorCount||'—')}</dd>
      <dt>Placa de vídeo</dt><dd>${esc(m.gpuName||'—')}</dd><dt>Último WoL</dt><dd>${esc(last)}</dd><dt>Sinais enviados</dt><dd>${esc(s.wakeCount||0)}</dd></dl></section>`;
  }
  function quickActions(){
    const online=pcOnline();
    return `<section class="card pad"><div class="card-heading"><div><h3 class="card-title">Ações rápidas</h3><div class="card-subtitle">Controle direto do dispositivo</div></div>${icon('power')}</div>
      <div class="quick-buttons">
      <button type="button" class="btn ${online?'ghost':'primary'} block" data-action="wake" ${online?'disabled':''}>${icon('power')}<span>${online?'Computador ligado':'Ligar PC'}</span></button>
      ${button('shutdown','Desligar PC','power','block danger')}
      ${button('cancelShutdown','Cancelar desligamento','refresh','block ghost')}
      ${button('remote','Abrir tela remota','monitor','block ghost')}
      ${button('screenshot','Foto da tela','camera','block ghost')}</div>
      <dl class="kv" style="margin-top:15px"><dt>Rede Android</dt><dd>${esc(model.state.network||'—')}</dd><dt>IP deste Android</dt><dd>${esc(model.state.androidIp||'—')}</dd><dt>Pareamento</dt><dd>${model.state.hasAgentToken?'Ativo':'Pendente'}</dd></dl></section>`;
  }
  function overview(){
    const s=model.state,m=model.metrics||{}, online=pcOnline();
    const mem=Number(m.memoryTotalBytes)>0?100*Number(m.memoryUsedBytes)/Number(m.memoryTotalBytes):-1;
    const disk=Number(m.diskTotalBytes)>0?100*Number(m.diskUsedBytes)/Number(m.diskTotalBytes):-1;
    const pcText=online?'Ligado':s.pcState==='checking'?'Verificando':s.pcState==='unconfirmed'?'Sem confirmação':s.pcState==='offline'?'Sem resposta':'Não verificado';
    return `${heading('Visão geral','Acompanhe o estado do bot, do computador e da sua rede em um só lugar.',button('refresh','Atualizar','refresh','ghost'))}
      ${s.state==='error'?`<div class="callout status-note">${icon('info')} ${esc(s.detail||'Confira as configurações e a rede do bot.')}</div>`:''}
      ${s.pcState==='checking'||s.pcState==='unconfirmed'?`<div class="callout status-note">${icon('info')} ${s.pcState==='checking'?'Sinal enviado. Aguardando resposta do PC por até 45 segundos.':'O sinal foi enviado, mas o PC não confirmou que ligou.'}</div>`:''}
      <section class="stats-grid">
      ${stat('Bot Telegram',s.state==='online'?'Conectado':s.running?'Conectando':'Pausado',s.botName?'@'+s.botName:statusLabel(),'bot','blue')}
      ${stat('Computador',pcText,online&&m.uptimeSeconds>=0?'Tempo ligado: '+uptime(m.uptimeSeconds):'Estado consultado pela rede','monitor',online?'green':'pink')}
      ${stat('Uso de CPU',percent(m.cpuPercent),m.cpuPercent>=0?'Leitura do agente Windows':'Sem leitura','cpu','pink',model.history.slice(-30).map(h=>h.cpu))}
      ${stat('Memória RAM',percent(mem),m.memoryTotalBytes>0?bytes(m.memoryUsedBytes)+' / '+bytes(m.memoryTotalBytes):'Sem leitura','ram','pink',model.history.slice(-30).map(h=>h.memory))}
      ${stat('Uso de GPU',percent(m.gpuPercent),m.gpuName||'Sem leitura','cpu','pink',model.history.slice(-30).map(h=>h.gpu))}
      ${stat('Armazenamento',percent(disk),m.diskTotalBytes>0?bytes(m.diskUsedBytes)+' / '+bytes(m.diskTotalBytes):'Sem leitura','drive','pink',model.history.slice(-30).map(h=>h.disk))}</section>
      <div class="section-grid">${chartCard('Uso da CPU',percent(m.cpuPercent),'cpu')}${chartCard('Memória RAM',m.memoryTotalBytes>0?bytes(m.memoryUsedBytes)+' / '+bytes(m.memoryTotalBytes):'—','memory')}${quickActions()}</div>
      <div class="settings-grid"><section class="card pad"><div class="card-heading"><div><h3 class="card-title">Últimas atividades</h3><div class="card-subtitle">Eventos recentes do aplicativo</div></div><button class="link-button" data-nav="activity">Ver todas ${icon('arrow','sm')}</button></div>${events(5)}</section>${pcInfo()}</div>${footer()}`;
  }
  function monitorTabs(){
    return `<div class="monitor-tabs" role="tablist" aria-label="Dados do computador">${[['metrics','Métricas'],['screen','Tela'],['programs','Programas'],['hardware','Hardware']].map(p=>`<button class="tab ${model.tab===p[0]?'active':''}" role="tab" aria-selected="${model.tab===p[0]}" data-tab="${p[0]}">${p[1]}</button>`).join('')}</div>`;
  }
  function monitor(){
    const m=model.metrics||{},mem=Number(m.memoryTotalBytes)>0?100*Number(m.memoryUsedBytes)/Number(m.memoryTotalBytes):-1,disk=Number(m.diskTotalBytes)>0?100*Number(m.diskUsedBytes)/Number(m.diskTotalBytes):-1;
    let content='';
    if(model.tab==='metrics')content=`<div class="metric-grid">
      ${metricCard('CPU',percent(m.cpuPercent),m.cpuPercent>=0?'Leitura atual':'Aguardando agente','cpu')}
      ${metricCard('Memória RAM',percent(mem),m.memoryTotalBytes>0?bytes(m.memoryUsedBytes)+' utilizados':'Aguardando agente','ram')}
      ${metricCard('GPU',percent(m.gpuPercent),m.gpuName||'Aguardando agente','cpu','yellow')}
      ${metricCard('Armazenamento',percent(disk),m.diskTotalBytes>0?bytes(m.diskUsedBytes)+' utilizados':'Aguardando agente','drive','green')}</div>
      <div class="settings-grid" style="margin-top:15px">${chartCard('Histórico da CPU',percent(m.cpuPercent),'cpu')}
      <section class="card pad"><div class="card-heading"><h3 class="card-title">Detalhes do dispositivo</h3>${badge(pcOnline()?'Agente online':'Sem resposta',pcOnline()?'':'off')}</div>
      <dl class="kv"><dt>Placa de vídeo</dt><dd>${esc(m.gpuName||'—')}</dd><dt>Memória dedicada da GPU</dt><dd>${bytes(m.gpuMemoryDedicatedUsedBytes)}</dd>
      <dt>Tempo ligado</dt><dd>${uptime(m.uptimeSeconds)}</dd><dt>Janela ativa</dt><dd>${esc(m.activeWindow||'—')}</dd>
      <dt>Monitores disponíveis</dt><dd>${esc(m.monitorCount||'—')}</dd><dt>Sistema</dt><dd>${esc(model.inventory?.fields?.['Sistema operacional']||'Consulte a aba Hardware')}</dd></dl></section></div>`;
    if(model.tab==='screen')content=remoteContent();
    if(model.tab==='programs')content=processContent();
    if(model.tab==='hardware')content=hardwareContent();
    return `${heading('Monitorar PC','Diagnóstico, telemetria e informações fornecidas pelo agente Windows.',button('refreshMonitor','Atualizar status','refresh')+button('pairing','Pareamento','link','ghost'))}
      ${model.state.hasAgentToken?'':`<div class="callout">${icon('shield')} Pareie este Android com o agente Windows para consultar métricas e visualizar a tela. <button class="link-button" data-nav="pairing">Abrir pareamento</button></div>`}
      ${monitorTabs()}${content}${footer()}`;
  }
  function remoteContent(){
    const modes=Array.isArray(model.metrics?.monitorModes)?model.metrics.monitorModes:[];
    const count=Math.max(1,Number(model.metrics?.monitorCount)||1,modes.length);
    return `<div class="remote-layout"><section class="card pad"><div class="card-heading"><div><h3 class="card-title">Visualização remota</h3><div class="card-subtitle">Somente imagem · mouse e teclado desativados</div></div>${badge(model.live?'Ao vivo':'Aguardando',model.live?'':'yellow')}</div>
      <div class="filter-row"><select class="input" id="monitor-selector" aria-label="Escolher monitor" style="max-width:250px">${Array.from({length:count},(_,i)=>`<option value="${i}" ${i===model.monitor?'selected':''}>Monitor ${i+1}${modes[i]?.width?' ('+modes[i].width+' × '+modes[i].height+')':''}</option>`).join('')}</select><span class="muted small">Pareamento: ${model.state.hasAgentToken?'ativo':'necessário'}</span></div>
      <div class="desktop-preview ${model.expanded?'remote-expanded':''}" id="remote-preview"><button class="btn xs expand-close" data-action="maximize">Fechar ampliação</button><div class="screen-holder" style="width:${model.zoom}%"><div class="remote-screen" id="remote-screen">
      ${model.lastFrame?`<img id="pc-screen" src="${model.lastFrame}" alt="Tela recebida do computador">`:`<div class="screen-placeholder">${icon('monitor','xl')}<span>${esc(model.screenMessage)}</span></div>`}
      </div></div></div>
      <div class="remote-controls"><div class="zoom-choices">${button('zoomOut','−','','xs ghost')}${[50,100,150,200,250].map(z=>`<button class="btn xs ${model.zoom===z?'active':''}" data-zoom="${z}">${z}%</button>`).join('')}${button('zoomIn','+','','xs ghost')}</div>
      <div class="flex">${button('zoomReset','100%','','xs ghost')}${button('maximize','Ampliar','max','xs ghost')}</div></div>
      <div class="remote-action-row">${button('remoteToggle',model.live?'Parar visualização':'Iniciar visualização',model.live?'x':'monitor',model.live?'danger':'primary')}${button('screenshot','Foto única','camera','ghost')}</div>
      <p class="muted small">A autorização de captura e o acesso sem supervisão são configurados no agente Windows.</p></section>
      <div><section class="card pad"><h3 class="card-title">Informações da sessão</h3><dl class="kv" style="margin-top:13px"><dt>Modo</dt><dd>Somente visualização</dd><dt>Protocolo</dt><dd>JPEG sucessivo</dd><dt>Monitor ativo</dt><dd>${model.monitor+1} de ${count}</dd><dt>Escala</dt><dd>${model.zoom}%</dd><dt>Autorização no Windows</dt><dd>${model.metrics?.unattendedScreenAllowed===true?'Acesso sem supervisão':'Aprovação por pedido'}</dd><dt>Estado</dt><dd id="remote-session-state">${esc(model.screenMessage)}</dd></dl></section>
      <div class="callout" style="margin-top:14px">${icon('shield')} A tela real depende do agente Windows e do pareamento deste dispositivo.</div>
      <div style="margin-top:13px">${button('pairing','Gerenciar pareamento','link','block')}</div></div></div>`;
  }
  function remote(){
    return `${heading('Tela remota','Visualize o monitor selecionado, sem enviar mouse ou teclado ao computador.',button('refreshMonitor','Atualizar','refresh'))}${remoteContent()}${footer()}`;
  }
  function processContent(){
    if(!model.processes)return '<div class="card pad muted">Carregando programas abertos…</div>';
    const all=model.processes.processes||[],query=model.processQuery.toLocaleLowerCase('pt-BR').trim();
    const list=all.filter(p=>!query||String(p.name||'').toLocaleLowerCase('pt-BR').includes(query)||String(p.pid).includes(query));
    return `<section class="card pad"><div class="card-heading"><div><h3 class="card-title">Programas abertos</h3><div class="card-subtitle" id="process-count">${list.length} de ${all.length} itens informados pelo agente Windows</div></div>
      ${button('refreshProcesses','Atualizar','refresh','xs ghost')}</div>
      <div class="filter-row"><input id="process-filter" class="input" value="${esc(model.processQuery)}" placeholder="Buscar por nome ou PID" aria-label="Buscar processo"></div>
      <div class="table-scroll"><table class="table"><thead><tr><th>Nome</th><th>PID</th><th>Memória</th><th>Estado</th></tr></thead><tbody>${all.map(p=>{const search=String(p.name||'').toLocaleLowerCase('pt-BR')+' '+p.pid;return `<tr data-process="${esc(search)}" ${query&&!search.includes(query)?'hidden':''}><td>${esc(p.name)}</td><td class="muted number">${esc(p.pid)}</td><td>${bytes(p.memoryBytes)}</td><td>${badge('Em execução')}</td></tr>`;}).join('')}</tbody></table></div><p id="process-empty" class="muted small" ${list.length?'hidden':''}>Nenhum programa corresponde à busca.</p></section>`;
  }
  function processes(){
    return `${heading('Processos','Programas em execução informados pelo agente Windows.',button('refreshProcesses','Atualizar programas','refresh'))}${processContent()}${footer()}`;
  }
  function hardwareContent(){
    if(!model.inventory)return `<div class="card pad muted">${button('refreshInventory','Atualizar hardware','refresh')} Consulte o inventário do agente Windows.</div>`;
    const fields=model.inventory.fields||{},all=model.inventory.installedPrograms||[],query=model.installedQuery.toLocaleLowerCase('pt-BR').trim();
    const programs=all.filter(p=>!query||String(p.name||'').toLocaleLowerCase('pt-BR').includes(query)||String(p.version||'').toLocaleLowerCase('pt-BR').includes(query));
    return `<div class="settings-grid"><section class="card pad"><div class="card-heading"><h3 class="card-title">Hardware</h3>${button('refreshInventory','Atualizar','refresh','xs ghost')}</div><dl class="kv" style="margin-top:13px">${Object.keys(fields).map(k=>`<dt>${esc(k)}</dt><dd>${esc(fields[k])}</dd>`).join('')}</dl></section>
      <section class="card pad"><div class="card-heading"><div><h3 class="card-title">Programas instalados</h3><div class="card-subtitle" id="installed-count">${programs.length} de ${all.length}</div></div></div>
      <div class="filter-row"><input id="installed-filter" class="input" value="${esc(model.installedQuery)}" placeholder="Buscar programa ou versão" aria-label="Buscar programa instalado"></div>
      <div class="table-scroll"><table class="table"><thead><tr><th>Programa</th><th>Versão</th></tr></thead><tbody>${all.map(p=>{const search=(p.name||'').toLocaleLowerCase('pt-BR')+' '+(p.version||'').toLocaleLowerCase('pt-BR');return `<tr data-installed="${esc(search)}" ${query&&!search.includes(query)?'hidden':''}><td>${esc(p.name)}</td><td class="muted">${esc(p.version||'')}</td></tr>`;}).join('')}</tbody></table></div><p id="installed-empty" class="muted small" ${programs.length?'hidden':''}>Nenhum programa corresponde à busca.</p></section></div>`;
  }
  function activity(){
    return `${heading('Atividade','Os últimos eventos registrados neste dispositivo.',button('refresh','Atualizar','refresh')+button('clearLogs','Limpar histórico','x','ghost'))}
      <section class="card pad"><div class="filter-pills">${[['all','Todas'],['good','Sucesso'],['info','Comandos'],['warn','Avisos']].map(p=>`<button class="btn xs ${model.activityFilter===p[0]?'active':''}" data-filter="${p[0]}">${p[1]}</button>`).join('')}</div>${events()}</section>
      <section class="card pad status-summary"><h3 class="card-title">Resumo do serviço</h3><dl class="kv"><dt>Bot Telegram</dt><dd>${statusLabel()}</dd><dt>PC monitorado</dt><dd>${pcOnline()?'Ligado':'Sem confirmação'}</dd><dt>Dispositivo</dt><dd>${model.state.hasAgentToken?'Pareado':'Não pareado'}</dd><dt>Eventos locais</dt><dd>${(model.state.events||[]).length}</dd></dl></section>${footer()}`;
  }
  function pairing(){
    const s=model.state;
    return `${heading('Pareamento','Associe este Android ao agente instalado no Windows.',button('refreshMonitor','Verificar conexão','refresh','ghost'))}
      <div class="settings-grid"><section class="card pad"><div class="card-heading"><h3 class="card-title">Este dispositivo</h3>${badge(s.hasAgentToken?'Pareado':'Não pareado',s.hasAgentToken?'':'off')}</div>
      <p class="muted">O pedido precisa ser aceito na tela do computador. A chave de acesso permanece protegida no Android.</p>
      <dl class="kv"><dt>IP do agente</dt><dd>${esc(s.pcIp||'Não configurado')}:${esc(s.agentPort||8765)}</dd><dt>Rede</dt><dd>Local ou Tailscale</dd></dl>
      <div class="pair-actions">${button('pair','Parear pela rede local','link','primary')}${s.hasAgentToken?button('unpair','Desvincular este Android','x','danger'):''}</div>
      ${s.hasAgentToken?'<p class="muted small">A desvinculação remove a chave deste aparelho. A chave do agente Windows é compartilhada com outros aparelhos pareados.</p>':''}</section>
      <section class="card pad"><h3 class="card-title">Etapas</h3>
      <ol class="steps"><li>Instale e abra o agente no Windows.</li><li>Informe o IP do PC em Configurações.</li><li>Solicite o pareamento neste Android.</li><li>Aceite a solicitação no computador.</li></ol>
      <div class="callout">${icon('shield')} O acesso à tela sem supervisão é autorizado ou revogado no agente Windows.</div></section></div>${footer()}`;
  }
  function field(key,label,help='',type='text'){
    const value=key==='token'?'':model.state[key]??'';
    return `<div class="field"><label for="cfg-${key}">${label}</label><input id="cfg-${key}" name="${key}" type="${type}" value="${esc(value)}" autocomplete="off" spellcheck="false">${help?`<span class="help">${help}</span>`:''}</div>`;
  }
  function settings(){
    const s=model.state;
    return `${heading('Configurações','Dados do Telegram, do computador e do aplicativo.')}
      <form id="settings-form"><div class="settings-grid">
      <section class="card settings-group"><div class="card-heading"><div><h3 class="card-title">${icon('bot')} Telegram</h3><div class="card-subtitle">Somente IDs autorizados podem emitir comandos</div></div>${badge('Protegido')}</div>
      ${field('token','Token do bot',s.hasToken?'Token salvo. Deixe vazio para manter.':'Cole o token criado no BotFather.','password')}
      ${field('users','IDs numéricos autorizados')}${field('displayName','Nome para saudação (opcional)')}
      <div class="callout">${icon('shield')} O token salvo não é exibido na tela.</div></section>
      <section class="card settings-group"><div class="card-heading"><div><h3 class="card-title">${icon('monitor')} PC e rede</h3><div class="card-subtitle">Wake-on-LAN e comunicação com o agente</div></div></div>
      <div class="settings-grid form-columns">${field('mac','Endereço MAC')}${field('pcIp','IP local do PC','Necessário para o pareamento inicial.')}${field('tailscaleIp','IP Tailscale do PC (opcional)','Endereço 100.x para acesso fora de casa. O app escolhe a rede automaticamente.')}${field('broadcast','Endereço broadcast')}
      ${field('port','Porta Wake-on-LAN','','number')}${field('pcCheckPort','Porta de verificação','','number')}${field('agentPort','Porta do agente Windows','','number')}</div></section>
      <section class="card settings-group"><h3 class="card-title">${icon('settings')} Aplicativo Android</h3>
      <label class="switch-row"><span><strong>Iniciar com a TV Box</strong><small>Retoma o bot após reiniciar, se ele estava conectado.</small></span><input id="cfg-autoStart" type="checkbox" ${s.autoStart!==false?'checked':''}><span class="toggle-ui"></span></label>
      <p class="muted small">Bateria sem restrições: ${s.batteryFree?'permitida':'verifique nas configurações do Android'}</p>
      <div class="settings-footer">${button('battery','Permitir segundo plano','settings','ghost')}${button('pairing','Pareamento','link','ghost')}</div></section>
      <section class="card settings-group"><h3 class="card-title">${icon('refresh')} Atualizações oficiais</h3><p id="update-status" class="muted">${esc(s.updateStatus||'Verificando…')}</p>
      <div class="update-buttons">${button('checkUpdates','Verificar','refresh','ghost')}<span id="update-actions" class="update-buttons" ${s.updateAvailable?'':'hidden'}>${button('downloadUpdate','Baixar atualização','upload','primary')}${button('installUpdate','Instalar download','save','ghost')}</span>${button('importEnv','Importar .env','upload','ghost')}</div></section></div>
      <div class="settings-footer">${button('resetSettings','Reverter campos','x','ghost')}${button('saveSettings','Salvar configuração','save','primary')}</div></form>${footer()}`;
  }
  function help(){
    return `${heading('Como usar','Prepare a TV Box, o PC e seu bot do Telegram.',button('settings','Abrir configurações','settings','ghost'))}
      <section class="three-column help-steps">
      ${[['01','Configure o Android','Informe o token do BotFather, seus IDs numéricos do Telegram e os dados de rede do PC.'],['02','Conecte o bot','Mantenha somente uma instalação Android conectada ao mesmo bot e permita execução em segundo plano.'],['03','Prepare o Windows','Instale o agente, habilite Wake-on-LAN e aprove o pareamento solicitado pelo Android.'],['04','Use o Telegram','Envie uma mensagem ao bot, abra o menu e selecione os comandos disponíveis.']].map(x=>`<div class="card step"><div class="step-count">${x[0]}</div><h3>${x[1]}</h3><p>${x[2]}</p></div>`).join('')}</section>
      <div class="settings-grid"><section class="card pad"><h3 class="card-title">Rede e acesso remoto</h3><p>Para acessar de outra rede, mantenha o Tailscale ativo no Android e no Windows e configure o IP 100.x em “IP Tailscale do PC”. O IP local continua necessário para o pareamento inicial. A visualização é somente leitura. Autorize captura e acesso sem supervisão no agente Windows.</p></section>
      <section class="card pad"><h3 class="card-title">Confirmação de energia</h3><p>O envio do pacote Wake-on-LAN não confirma que o PC ligou. O aplicativo aguarda a resposta do computador antes de informar o estado.</p></section></div>${footer()}`;
  }
  function render(){
    const view={overview,monitor,remote,processes,activity,pairing,settings,help}[model.page]||overview;
    const scroll=window.scrollY;
    el('app').innerHTML=`<div class="shell">${model.mobileOpen?'<div class="mobile-mask" data-action="closeMobile"></div>':''}
      ${sidebar()}<div class="main-wrap">${topbar()}<main class="main-content">${view()}</main></div>${bottomNav()}</div>`;
    if(scroll)window.scrollTo(0,scroll);
    syncRequired();
  }
  function syncRequired(){
    const required=!!model.state.updateRequired;
    el('required-update').hidden=!required;
    el('app').inert=required;
    el('required-status').textContent=model.state.updateStatus||'Verificando atualização…';
    const status=el('update-status');if(status)status.textContent=model.state.updateStatus||'Verificando…';
    const actions=el('update-actions');if(actions)actions.hidden=!model.state.updateAvailable;
    document.querySelectorAll('[data-action="downloadUpdate"],[data-action="installUpdate"]').forEach(button=>{button.disabled=!!model.state.updateBusy;});
  }
  function navigate(page){
    if(page==='more'){model.mobileOpen=true;render();return;}
    if(model.live && page!=='remote' && !(model.page==='monitor' && page==='monitor')){invoke('stopLiveScreen');model.live=false;}
    model.page=page;model.mobileOpen=false;model.expanded=false;window.scrollTo(0,0);render();
    if(['overview','monitor','remote'].includes(page)&&model.state.hasAgentToken)invoke('monitor');
    if(page==='processes'||(page==='monitor'&&model.tab==='programs'))invoke('processes');
    if(page==='monitor'&&model.tab==='hardware')invoke('inventory');
  }
  function act(name){
    switch(name){
      case 'toggleSidebar': if(window.innerWidth<=700)model.mobileOpen=!model.mobileOpen;else model.sidebarCollapsed=!model.sidebarCollapsed;render();break;
      case 'closeMobile':model.mobileOpen=false;render();break;
      case 'search':openSearch();break;
      case 'closeSearch':el('quick-search-overlay')?.remove();break;
      case 'alerts':openAlerts();break;
      case 'closeAlerts':el('alerts-overlay')?.remove();break;
      case 'refresh':invoke('refresh');if(model.state.hasAgentToken)invoke('monitor');break;
      case 'refreshMonitor':invoke('monitor');break;
      case 'refreshProcesses':invoke('processes');break;
      case 'refreshInventory':invoke('inventory');break;
      case 'toggleBot':invoke(model.state.running?'stop':'connect');break;
      case 'wake':if(!pcOnline())invoke('wake');break;
      case 'shutdown':invoke('shutdownPc');break;
      case 'cancelShutdown':invoke('cancelShutdown');break;
      case 'remote':navigate('remote');break;
      case 'screenshot':if(model.page!=='remote'){model.page='remote';render();}invoke('screen',model.monitor);break;
      case 'remoteToggle':
        if(model.live)invoke('stopLiveScreen');
        else {model.lastFrame=null;model.screenMessage='Conectando ao computador…';render();invoke('startLiveScreen',model.monitor);}
        break;
      case 'zoomIn':model.zoom=Math.min(250,model.zoom+25);updateZoom();break;
      case 'zoomOut':model.zoom=Math.max(50,model.zoom-25);updateZoom();break;
      case 'zoomReset':model.zoom=100;updateZoom();break;
      case 'maximize':{
        model.expanded=!model.expanded;
        el('remote-preview')?.classList.toggle('remote-expanded',model.expanded);
        break;
      }
      case 'pair':invoke('pairAgent');window.notify('Confirme a solicitação no computador.');break;
      case 'unpair':invoke('unpairAgent');break;
      case 'clearLogs':model.state.events=[];render();invoke('clearEvents');break;
      case 'battery':invoke('battery');break;
      case 'importEnv':invoke('importEnv');break;
      case 'checkUpdates':case 'downloadUpdate':case 'installUpdate':invoke(name);break;
      case 'saveSettings':el('settings-form')?.requestSubmit();break;
      case 'resetSettings':el('settings-form')?.reset();break;
      default: if(pages.some(p=>p[0]===name))navigate(name);
    }
  }
  function updateZoom(){
    const holder=el('remote-preview')?.querySelector('.screen-holder');
    if(holder)holder.style.width=model.zoom+'%';
    const state=el('remote-session-state');if(state)state.textContent=model.screenMessage;
    document.querySelectorAll('[data-zoom]').forEach(b=>b.classList.toggle('active',Number(b.dataset.zoom)===model.zoom));
  }
  function openSearch(){
    if(el('quick-search-overlay'))return;
    const overlay=document.createElement('div');overlay.id='quick-search-overlay';overlay.className='search-overlay';
    overlay.innerHTML=`<div class="search-dialog" role="dialog" aria-modal="true" aria-label="Buscar seção">
      <label for="quick-search">Buscar seção</label><input id="quick-search" class="input" placeholder="Monitorar, tela remota, configurações…" autocomplete="off">
      <div id="quick-search-results">${pages.map(p=>`<button class="nav-item" data-nav="${p[0]}">${icon(p[1])}<span>${p[2]}</span></button>`).join('')}</div>
      <button class="btn ghost" data-action="closeSearch">Fechar</button></div>`;
    document.body.appendChild(overlay);el('quick-search').focus();
  }
  function openAlerts(){
    if(el('alerts-overlay'))return;
    const overlay=document.createElement('div');overlay.id='alerts-overlay';overlay.className='search-overlay';
    const recent=(model.state.events||[]).slice(0,5);
    overlay.innerHTML=`<div class="search-dialog" role="dialog" aria-modal="true" aria-label="Notificações e atividade">
      <h2>Notificações</h2><p class="muted small">${esc(statusLabel())} · PC: ${pcOnline()?'ligado':'sem confirmação'}</p>
      <div class="alert-list">${recent.length?recent.map(e=>`<div class="alert-item"><strong>${esc(e.kind==='good'?'Sucesso':e.kind==='warn'?'Aviso':'Informação')}</strong><span>${esc(e.text)}</span></div>`).join(''):'Nenhuma atividade recente.'}</div>
      <div class="page-actions"><button class="btn" data-nav="activity">Ver atividade</button><button class="btn ghost" data-action="closeAlerts">Fechar</button></div></div>`;
    document.body.appendChild(overlay);
  }
  document.addEventListener('input',event=>{
    const id=event.target.id,query=event.target.value.toLocaleLowerCase('pt-BR');
    if(id==='quick-search')el('quick-search-results')?.querySelectorAll('[data-nav]').forEach(n=>{n.hidden=!n.textContent.toLocaleLowerCase('pt-BR').includes(query);});
    if(id==='process-filter'){
      model.processQuery=event.target.value;
      let shown=0;document.querySelectorAll('[data-process]').forEach(n=>{n.hidden=!n.dataset.process.includes(query);if(!n.hidden)shown++;});
      if(el('process-count'))el('process-count').textContent=shown+' de '+(model.processes?.processes||[]).length+' itens informados pelo agente Windows';
      if(el('process-empty'))el('process-empty').hidden=shown>0;
    }
    if(id==='installed-filter'){
      model.installedQuery=event.target.value;
      let shown=0;document.querySelectorAll('[data-installed]').forEach(n=>{n.hidden=!n.dataset.installed.includes(query);if(!n.hidden)shown++;});
      if(el('installed-count'))el('installed-count').textContent=shown+' de '+(model.inventory?.installedPrograms||[]).length;
      if(el('installed-empty'))el('installed-empty').hidden=shown>0;
    }
  });
  document.addEventListener('click',event=>{
    if(event.target.id==='quick-search-overlay'||event.target.id==='alerts-overlay'){event.target.remove();return;}
    const n=event.target.closest('[data-nav],[data-tab],[data-zoom],[data-filter],[data-action]');if(!n)return;
    if(n.dataset.nav){el('quick-search-overlay')?.remove();el('alerts-overlay')?.remove();navigate(n.dataset.nav);return;}
    if(n.dataset.tab){model.tab=n.dataset.tab;render();if(model.tab==='programs')invoke('processes');if(model.tab==='hardware')invoke('inventory');return;}
    if(n.dataset.zoom){model.zoom=Number(n.dataset.zoom);updateZoom();return;}
    if(n.dataset.filter){model.activityFilter=n.dataset.filter;render();return;}
    act(n.dataset.action);
  });
  document.addEventListener('change',event=>{
    if(event.target.matches('[data-range]')){model.range=Number(event.target.value)||30;render();return;}
    if(event.target.id==='monitor-selector'){
      model.monitor=Number(event.target.value)||0;invoke('selectMonitor',model.monitor);
      if(model.live){invoke('stopLiveScreen');model.live=false;model.lastFrame=null;model.screenMessage='Monitor alterado. Inicie novamente a visualização.';render();}
    }
  });
  document.addEventListener('submit',event=>{
    if(event.target.id!=='settings-form')return;event.preventDefault();
    const form=event.target;
    const data={autoStart:!!el('cfg-autoStart')?.checked};
    ['token','users','displayName','mac','pcIp','tailscaleIp','broadcast','port','pcCheckPort','agentPort'].forEach(k=>data[k]=(form.elements[k]?.value||'').trim());
    invoke('save',JSON.stringify(data));
  });
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'||event.key==='BrowserBack'){
      if(window.closePanel())event.preventDefault();return;
    }
    if(event.ctrlKey&&event.key.toLowerCase()==='k'){event.preventDefault();openSearch();return;}
    if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key))return;
    const active=document.activeElement;if(active&&['INPUT','SELECT','TEXTAREA'].includes(active.tagName))return;
    const nodes=Array.from(document.querySelectorAll('button,input,select')).filter(n=>!n.disabled&&n.getClientRects().length);
    const rect=active?.getBoundingClientRect();if(!rect||active===document.body){nodes[0]?.focus();return;}
    const x=rect.left+rect.width/2,y=rect.top+rect.height/2;let best=null,score=Infinity;
    for(const node of nodes){if(node===active)continue;const r=node.getBoundingClientRect(),dx=r.left+r.width/2-x,dy=r.top+r.height/2-y;
      const forward=event.key==='ArrowRight'?dx:event.key==='ArrowLeft'?-dx:event.key==='ArrowDown'?dy:-dy;
      if(forward<=3)continue;const cross=event.key==='ArrowUp'||event.key==='ArrowDown'?Math.abs(dx):Math.abs(dy);
      const s=forward+cross*2.4;if(s<score){score=s;best=node;}}
    if(best){best.focus();best.scrollIntoView({block:'nearest',inline:'nearest'});event.preventDefault();}
  });
  window.closePanel=()=>{
    if(model.expanded){model.expanded=false;el('remote-preview')?.classList.remove('remote-expanded');return true;}
    if(el('alerts-overlay')){el('alerts-overlay').remove();return true;}
    if(el('quick-search-overlay')){el('quick-search-overlay').remove();return true;}
    if(model.mobileOpen){model.mobileOpen=false;render();return true;}
    if(model.page!=='overview'){navigate('overview');return true;}
    return false;
  };
  window.configSaved=()=>{window.notify('Configuração salva.');navigate('overview');};
  window.pairingSaved=()=>{window.notify('Pareamento concluído.');invoke('refresh');};
  window.pairingRemoved=()=>{model.metrics=null;model.inventory=null;model.processes=null;model.history=[];model.lastFrame=null;model.live=false;window.notify('Android desvinculado.');invoke('refresh');navigate('pairing');};
  window.pcStatus=data=>{
    model.metrics=data;
    if(Array.isArray(data.history))model.history=data.history;
    if(model.page==='overview'||(model.page==='monitor'&&model.tab==='metrics'))render();
    if((model.page==='remote'||(model.page==='monitor'&&model.tab==='screen')) && !model.live)render();
  };
  window.pcProcesses=data=>{model.processes=data;if((model.page==='processes'||(model.page==='monitor'&&model.tab==='programs'))&&document.activeElement?.id!=='process-filter')render();};
  window.pcInventory=data=>{model.inventory=data;if(model.page==='monitor'&&model.tab==='hardware'&&document.activeElement?.id!=='installed-filter')render();};
  window.pcFailure=message=>{window.notify(message);};
  window.pcMonitorFailure=message=>{model.metrics=null;if(['overview','monitor','remote'].includes(model.page)&&!model.live)render();window.notify(message);};
  window.pcScreen=(source,frameId)=>{
    let image=el('pc-screen');
    if(!image){
      if(model.page!=='remote'&&!(model.page==='monitor'&&model.tab==='screen')){model.page='remote';render();}
      image=el('pc-screen');
      if(!image){
        const placeholder=el('remote-screen');if(placeholder){placeholder.replaceChildren();image=document.createElement('img');image.id='pc-screen';image.alt='Tela recebida do computador';placeholder.appendChild(image);}
      }
    }
    if(!image){if(frameId!=null)invoke('frameDisplayed',frameId);return;}
    model.lastFrame=source;
    image.onload=()=>{model.screenMessage=model.live?'Tela ao vivo · somente visualização':'Captura recebida';const label=el('remote-session-state');if(label)label.textContent=model.screenMessage;if(frameId!=null)invoke('frameDisplayed',frameId);};
    image.onerror=()=>{if(frameId!=null)invoke('frameDisplayed',frameId);window.notify('A imagem recebida não pôde ser exibida.');};
    image.src=source;
  };
  window.screenState=(status,message)=>{
    model.screenStatus=status;model.screenMessage=message;
    const wasLive=model.live;model.live=['waiting','live','retry'].includes(status);
    if(status==='waiting')model.lastFrame=null;
    if(model.page==='remote'||(model.page==='monitor'&&model.tab==='screen')){
      if(status==='waiting'||wasLive!==model.live)render();
      else {const label=el('remote-session-state');if(label)label.textContent=message;}
    }
    if(status==='error')window.notify(message);
  };
  window.screenStopped=()=>{model.live=false;model.screenStatus='stopped';model.screenMessage='Visualização encerrada.';if(model.page==='remote'||(model.page==='monitor'&&model.tab==='screen'))render();};
  window.renderState=data=>{
    const prev=JSON.stringify(model.state);
    if(data.updateRequired && !model.state.updateRequired && model.live){invoke('stopLiveScreen');model.live=false;}
    model.state=data;
    syncRequired();
    if(model.page==='settings')return;
    if(['process-filter','installed-filter'].includes(document.activeElement?.id))return;
    if(model.live && (model.page==='remote'||(model.page==='monitor'&&model.tab==='screen')))return;
    if(prev!==JSON.stringify(data))render();
  };
  let poll=0;
  setInterval(()=>{
    if(!model.state.hasAgentToken)return;
    if(['overview','monitor','remote'].includes(model.page))invoke('monitor');
    if(model.page==='processes'||(model.page==='monitor'&&model.tab==='programs')){if(++poll%3===0)invoke('processes');}
  },5000);
  if(bridge&&typeof bridge.metricHistory==='function'){
    try{const saved=JSON.parse(bridge.metricHistory()||'[]');if(Array.isArray(saved))model.history=saved;}catch(_ignored){}
  }
  render();
  if(bridge)invoke('refresh');
})();

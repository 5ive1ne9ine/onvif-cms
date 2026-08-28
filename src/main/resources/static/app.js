const icons = {
  grid: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>',
  pulse: '<svg viewBox="0 0 24 24"><path d="M3 12h4l2.2-6 4.1 12 2.3-6H21"/></svg>',
  radar: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/><path d="M12 12 19 5M12 3v2M3 12h2"/></svg>',
  camera: '<svg viewBox="0 0 24 24"><path d="M3 8.5A2.5 2.5 0 0 1 5.5 6h10A2.5 2.5 0 0 1 18 8.5v7a2.5 2.5 0 0 1-2.5 2.5h-10A2.5 2.5 0 0 1 3 15.5z"/><path d="m18 10 3-2v8l-3-2z"/></svg>',
  signal: '<svg viewBox="0 0 24 24"><path d="M5 12.5a10 10 0 0 1 14 0M8 16a6 6 0 0 1 8 0M11 19.5a2 2 0 0 1 2 0"/></svg>',
  brain: '<svg viewBox="0 0 24 24"><path d="M9.5 4A3.5 3.5 0 0 0 6 7.5v.4A3.5 3.5 0 0 0 4 14a3.5 3.5 0 0 0 5.5 4.8V4ZM14.5 4A3.5 3.5 0 0 1 18 7.5v.4a3.5 3.5 0 0 1 2 6.1 3.5 3.5 0 0 1-5.5 4.8V4Z"/><path d="M7 10h2.5M14.5 8H17M14.5 14H18M6 16h3.5"/></svg>',
  alert: '<svg viewBox="0 0 24 24"><path d="M10.3 4.2 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/></svg>',
  settings: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></svg>',
  play: '<svg viewBox="0 0 24 24"><path d="m9 6 9 6-9 6z"/></svg>',
  refresh: '<svg viewBox="0 0 24 24"><path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 9a7 7 0 0 1 11.6-2L20 12M4 12l2.3 5a7 7 0 0 0 11.6-2"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg>',
  up: '<svg viewBox="0 0 24 24"><path d="m6 15 6-6 6 6"/></svg>',
  down: '<svg viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg>',
  left: '<svg viewBox="0 0 24 24"><path d="m15 6-6 6 6 6"/></svg>',
  right: '<svg viewBox="0 0 24 24"><path d="m9 6 6 6-6 6"/></svg>',
  stop: '<svg viewBox="0 0 24 24"><rect x="8" y="8" width="8" height="8"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  minus: '<svg viewBox="0 0 24 24"><path d="M5 12h14"/></svg>'
};

const state = { cameras: [], events: [], selectedCamera: null, unreadEvents: 0 };
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

function mountIcons(root = document) {
  root.querySelectorAll('[data-icon]').forEach(node => {
    node.innerHTML = icons[node.dataset.icon] || '';
  });
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) }
  });
  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    try { message = (await response.json()).detail || message; } catch (_) {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[char]);
}

function toast(message, type = '') {
  const item = document.createElement('div');
  item.className = `toast ${type}`;
  item.textContent = message;
  $('#toastStack').append(item);
  setTimeout(() => item.remove(), 4200);
}

function updateClock() {
  const now = new Date();
  $('#clockTime').textContent = now.toLocaleTimeString('zh-CN', { hour12: false });
  $('#videoTime').textContent = $('#clockTime').textContent;
  $('#clockDate').textContent = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

function cameraCard(camera) {
  const visual = camera.streamConfigured
    ? `<img loading="lazy" src="/api/cameras/${camera.id}/snapshot.jpg" alt="${escapeHtml(camera.name)} 画面" onerror="this.closest('.camera-visual').classList.add('feed-error');this.remove()">`
    : `<div class="no-feed">${icons.camera}</div>`;
  const model = [camera.manufacturer, camera.model].filter(Boolean).join(' · ') || '等待设备认证';
  return `<article class="camera-card" data-camera-id="${camera.id}">
    <div class="camera-visual" data-action="preview">
      ${visual}<div class="feed-lines"></div>
      <span class="camera-state ${camera.online ? 'live' : ''}">${camera.online ? 'LIVE' : 'SETUP'}</span>
      ${camera.detectionEnabled ? `<span class="ai-chip">${icons.brain} AI ACTIVE</span>` : ''}
      ${camera.streamConfigured ? `<div class="play-overlay"><span>${icons.play}</span></div>` : ''}
    </div>
    <div class="camera-info">
      <div class="camera-info-head">
        <div><h3 title="${escapeHtml(camera.name)}">${escapeHtml(camera.name)}</h3><p>${escapeHtml(camera.host)} · ${escapeHtml(model)}</p></div>
        <div class="camera-actions">
          <button data-action="detection" title="Mage-VL 规则">${icons.brain}</button>
          <button data-action="settings" title="设备配置">${icons.settings}</button>
        </div>
      </div>
      <div class="camera-tags">
        <span class="${camera.streamConfigured ? 'ready' : ''}">${camera.streamConfigured ? 'RTSP READY' : 'NO STREAM'}</span>
        ${camera.ptzSupported ? '<span class="ready">PTZ</span>' : '<span>FIXED</span>'}
        <span>${camera.detectionIntervalSeconds || 30}s</span>
      </div>
    </div>
  </article>`;
}

function renderCameras() {
  $('#cameraGrid').innerHTML = state.cameras.map(cameraCard).join('');
  $('#cameraGrid').hidden = state.cameras.length === 0;
  $('#cameraEmpty').hidden = state.cameras.length !== 0;
}

async function loadCameras(silent = false) {
  try {
    state.cameras = await api('/api/cameras');
    renderCameras();
  } catch (error) {
    if (!silent) toast(error.message, 'error');
  }
}

async function loadStatus(silent = false) {
  try {
    const status = await api('/api/system/status');
    $('#cameraCount').textContent = status.cameras;
    $('#onlineCount').textContent = status.online;
    $('#aiCount').textContent = status.detectionEnabled;
    $('#todayEventCount').textContent = status.eventsLast24Hours;
    $('#modelName').textContent = status.mageModel;
    $('#mageEndpoint').textContent = status.mageBaseUrl;
    $('#ffmpegState').textContent = status.ffmpegAvailable ? 'READY' : 'NOT FOUND';
    $('#ffmpegState').style.color = status.ffmpegAvailable ? 'var(--green)' : 'var(--red)';
    const healthy = status.ffmpegAvailable;
    $('#systemDot').className = `status-dot ${healthy ? 'ok' : 'error'}`;
    $('#systemLabel').textContent = healthy ? '系统就绪' : '缺少 FFmpeg';
  } catch (error) {
    $('#systemDot').className = 'status-dot error';
    $('#systemLabel').textContent = '服务异常';
    if (!silent) toast(error.message, 'error');
  }
}

function eventItem(event) {
  const date = new Date(event.occurredAt);
  return `<article class="event-item">
    <div class="event-time"><strong>${date.toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit', second:'2-digit', hour12:false})}</strong><span>${date.toLocaleDateString('zh-CN')}</span></div>
    <div class="event-main"><h3>${escapeHtml(event.eventType)} · ${escapeHtml(event.cameraName)}</h3><p>${escapeHtml(event.summary)} · 置信度 ${Math.round(Number(event.confidence) * 100)}%</p></div>
    <span class="severity ${escapeHtml(event.severity)}">${escapeHtml(event.severity)}</span>
  </article>`;
}

function renderEvents() {
  $('#eventFeed').innerHTML = state.events.length
    ? state.events.map(eventItem).join('')
    : '<div class="empty-state"><h3>暂无智能事件</h3><p>启用摄像头 Mage‑VL 检测后，超过阈值的事件会显示在这里。</p></div>';
}

async function loadEvents(silent = false) {
  try {
    state.events = await api('/api/events?limit=100');
    renderEvents();
  } catch (error) {
    if (!silent) toast(error.message, 'error');
  }
}

async function scan() {
  const button = $('#scanButton');
  button.disabled = true;
  button.querySelector('span').textContent = '正在搜索…';
  try {
    state.cameras = await api('/api/discovery/scan', { method: 'POST' });
    renderCameras();
    await loadStatus(true);
    toast(`搜索完成，当前发现 ${state.cameras.length} 台设备`, 'success');
  } catch (error) {
    toast(error.message, 'error');
  } finally {
    button.disabled = false;
    button.querySelector('span').textContent = '搜索设备';
  }
}

function selected(id) {
  return state.cameras.find(camera => camera.id === Number(id));
}

function openCameraSettings(camera) {
  const form = $('#cameraForm');
  form.cameraId.value = camera.id;
  form.name.value = camera.name || '';
  form.username.value = '';
  form.password.value = '';
  $('#cameraDialog').showModal();
}

function openDetectionSettings(camera) {
  const form = $('#detectionForm');
  form.cameraId.value = camera.id;
  form.enabled.checked = camera.detectionEnabled;
  form.prompt.value = camera.detectionPrompt || '';
  form.intervalSeconds.value = camera.detectionIntervalSeconds || 30;
  form.confidenceThreshold.value = camera.confidenceThreshold ?? .6;
  $('#detectionDialog').showModal();
}

function openPreview(camera) {
  if (!camera.streamConfigured) {
    openCameraSettings(camera);
    toast('请先配置设备账号并建立连接');
    return;
  }
  state.selectedCamera = camera;
  $('#previewTitle').textContent = camera.name;
  $('#previewMeta').textContent = `${camera.host} · ${camera.manufacturer || ''} ${camera.model || ''}`.trim();
  $('#previewLoading').hidden = false;
  const image = $('#previewImage');
  image.src = `/api/cameras/${camera.id}/preview.mjpg?t=${Date.now()}`;
  image.onload = () => { $('#previewLoading').hidden = true; };
  image.onerror = () => { $('#previewLoading p').textContent = '视频流连接失败，请检查 RTSP 与 FFmpeg'; };
  $('#ptzControl').style.opacity = camera.ptzSupported ? '1' : '.3';
  $('#previewDialog').showModal();
}

function closePreview() {
  $('#previewImage').src = '';
  $('#previewDialog').close();
  state.selectedCamera = null;
}

async function ptz(pan = 0, tilt = 0, zoom = 0) {
  const camera = state.selectedCamera;
  if (!camera?.ptzSupported) return toast('当前摄像头不支持 PTZ', 'error');
  try {
    await api(`/api/cameras/${camera.id}/ptz/move`, {
      method: 'POST', body: JSON.stringify({ pan, tilt, zoom, durationMillis: 450 })
    });
  } catch (error) { toast(error.message, 'error'); }
}

async function stopPtz() {
  if (!state.selectedCamera?.ptzSupported) return;
  try { await api(`/api/cameras/${state.selectedCamera.id}/ptz/stop`, { method: 'POST' }); }
  catch (error) { toast(error.message, 'error'); }
}

function connectEvents() {
  const source = new EventSource('/api/events/stream');
  source.addEventListener('detection', message => {
    const event = JSON.parse(message.data);
    state.events.unshift(event);
    state.events = state.events.slice(0, 100);
    renderEvents();
    state.unreadEvents++;
    $('#eventBadge').hidden = false;
    $('#eventBadge').textContent = state.unreadEvents;
    toast(`${event.cameraName}：${event.summary}`, event.severity === 'LOW' ? '' : 'error');
    loadStatus(true);
  });
}

function setView(view) {
  $$('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === view));
  $$('.view').forEach(item => item.classList.remove('active'));
  $(`#${view}View`).classList.add('active');
  $('#pageTitle').textContent = view === 'monitor' ? '实时监控' : '智能事件';
  if (view === 'events') {
    state.unreadEvents = 0;
    $('#eventBadge').hidden = true;
  }
}

$('#cameraGrid').addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  const card = event.target.closest('[data-camera-id]');
  if (!action || !card) return;
  const camera = selected(card.dataset.cameraId);
  if (action === 'preview') openPreview(camera);
  if (action === 'settings') openCameraSettings(camera);
  if (action === 'detection') openDetectionSettings(camera);
});

$('#cameraForm').addEventListener('submit', async event => {
  event.preventDefault();
  if (event.submitter?.value === 'cancel') return $('#cameraDialog').close();
  const form = event.currentTarget;
  $('#saveCamera').disabled = true;
  try {
    await api(`/api/cameras/${form.cameraId.value}/credentials`, {
      method: 'PUT', body: JSON.stringify({ name: form.name.value, username: form.username.value, password: form.password.value })
    });
    $('#cameraDialog').close();
    await Promise.all([loadCameras(true), loadStatus(true)]);
    toast('摄像头已连接，媒体参数读取成功', 'success');
  } catch (error) { toast(error.message, 'error'); }
  finally { $('#saveCamera').disabled = false; }
});

$('#detectionForm').addEventListener('submit', async event => {
  event.preventDefault();
  if (event.submitter?.value === 'cancel') return $('#detectionDialog').close();
  const form = event.currentTarget;
  $('#saveDetection').disabled = true;
  try {
    await api(`/api/cameras/${form.cameraId.value}/detection`, {
      method: 'PUT', body: JSON.stringify({ enabled: form.enabled.checked, prompt: form.prompt.value,
        intervalSeconds: Number(form.intervalSeconds.value), confidenceThreshold: Number(form.confidenceThreshold.value) })
    });
    $('#detectionDialog').close();
    await Promise.all([loadCameras(true), loadStatus(true)]);
    toast('Mage‑VL 检测规则已保存', 'success');
  } catch (error) { toast(error.message, 'error'); }
  finally { $('#saveDetection').disabled = false; }
});

$('#ptzControl').addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.hasAttribute('data-stop')) stopPtz();
  else ptz(Number(button.dataset.pan), Number(button.dataset.tilt), 0);
});
$$('.zoom-control button').forEach(button => button.addEventListener('click', () => ptz(0, 0, Number(button.dataset.zoom))));
$('#analyzeNow').addEventListener('click', async () => {
  if (!state.selectedCamera) return;
  const button = $('#analyzeNow');
  button.disabled = true;
  button.querySelector('span').textContent = '已提交';
  try {
    await api(`/api/cameras/${state.selectedCamera.id}/detection/run`, { method: 'POST' });
    toast('Mage‑VL 分析任务已提交', 'success');
  } catch (error) { toast(error.message, 'error'); }
  setTimeout(() => { button.disabled = false; button.querySelector('span').textContent = '立即分析'; }, 2500);
});

$('#scanButton').addEventListener('click', scan);
document.addEventListener('click', event => { if (event.target.closest('[data-action="scan"]')) scan(); });
$('#refreshEvents').addEventListener('click', () => loadEvents());
$('#closePreview').addEventListener('click', closePreview);
$('#previewDialog').addEventListener('cancel', event => { event.preventDefault(); closePreview(); });
$$('.nav-item').forEach(item => item.addEventListener('click', () => setView(item.dataset.view)));

mountIcons();
updateClock();
setInterval(updateClock, 1000);
Promise.all([loadCameras(), loadEvents(), loadStatus()]);
setInterval(() => { loadCameras(true); loadStatus(true); }, 15000);
connectEvents();


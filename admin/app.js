const API = 'https://taxiapp-production-1a53.up.railway.app/api/admin';

// ─── Navegación ───────────────────────────────────────────────────────────────
function navigate(view) {
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById('view-' + view).classList.add('active');
  document.querySelector(`[data-view="${view}"]`).classList.add('active');
  document.getElementById('pageTitle').textContent =
    { dashboard: 'Dashboard', drivers: 'Conductores', queue: 'Cola de Turnos', trips: 'Viajes' }[view];
  if (view === 'dashboard') loadDashboard();
  if (view === 'drivers')   loadDrivers();
  if (view === 'queue')     { loadQueue(); loadDriversForQueue(); }
  if (view === 'trips')     loadTrips();
}
document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', (e) => { e.preventDefault(); navigate(item.dataset.view); });
});
function toggleSidebar() {
  document.getElementById('sidebar').classList.toggle('open');
}

// ─── API Helper ───────────────────────────────────────────────────────────────
async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
  });
  const data = await res.json().catch(() => ({ success: false, message: res.statusText }));
  if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`);
  return data;
}

// ─── Toast ────────────────────────────────────────────────────────────────────
function toast(msg, type = 'success') {
  const el = document.getElementById('toast');
  el.textContent = (type === 'success' ? '✅ ' : '❌ ') + msg;
  el.className = 'toast show ' + type;
  setTimeout(() => el.classList.remove('show'), 3500);
}

// ─── Status API ───────────────────────────────────────────────────────────────
async function checkStatus() {
  try {
    await fetch('https://taxiapp-production-1a53.up.railway.app/health');
    document.querySelector('.status-dot').className = 'status-dot online';
    document.getElementById('statusText').textContent = 'Backend OK';
  } catch {
    document.querySelector('.status-dot').className = 'status-dot offline';
    document.getElementById('statusText').textContent = 'Sin conexión';
  }
}

// ─── DASHBOARD ────────────────────────────────────────────────────────────────
async function loadDashboard() {
  try {
    const { data } = await api('/dashboard');
    document.getElementById('st-drivers').textContent = data.total_drivers;
    document.getElementById('st-clients').textContent = data.total_clients;
    document.getElementById('st-trips').textContent   = data.total_trips;
    document.getElementById('st-queue').textContent   = data.queue_size;
    document.getElementById('st-active').textContent  = data.active_trips;
  } catch (e) { console.error(e); }

  try {
    const { data } = await api('/queue');
    const el = document.getElementById('dashQueueList');
    if (!data.length) { el.innerHTML = '<div class="empty-state">🔄 La cola está vacía</div>'; return; }
    el.innerHTML = data.slice(0, 5).map(d => `
      <div class="queue-preview-item">
        <div class="qpi-pos">${d.queue_position}</div>
        <div style="flex:1">
          <strong>${d.full_name}</strong>
          <span style="color:var(--sub);font-size:12px;margin-left:8px">Unidad ${d.unit_number} · ${d.vehicle_plate}</span>
        </div>
        <span class="badge badge-green">Disponible</span>
      </div>`).join('');
  } catch (e) { console.error(e); }
}

// ─── CONDUCTORES ──────────────────────────────────────────────────────────────
let allDrivers = [];

async function loadDrivers() {
  try {
    const { data } = await api('/drivers');
    allDrivers = data;
    renderDrivers(data);
  } catch (e) { toast(e.message, 'error'); }
}

function renderDrivers(data) {
  const tbody = document.getElementById('driversBody');
  if (!data.length) {
    tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No hay conductores registrados</td></tr>';
    return;
  }
  tbody.innerHTML = data.map((d, i) => `
    <tr>
      <td style="color:var(--sub);font-size:12px">${i+1}</td>
      <td>
        <div class="driver-info">
          ${d.avatar_url
            ? `<img src="${d.avatar_url}" class="driver-avatar" style="width:36px;height:36px;border-radius:50%;object-fit:cover">`
            : `<div class="driver-avatar">${(d.full_name||'?')[0].toUpperCase()}</div>`}
          <div>
            <div style="font-weight:600;font-size:13px">${d.full_name}</div>
            <div style="font-size:11px;color:var(--sub)">${d.email||''}</div>
          </div>
        </div>
      </td>
      <td style="font-size:13px">${d.phone}</td>
      <td><span class="badge badge-yellow">N° ${d.unit_number||'—'}</span></td>
      <td style="font-family:monospace;font-weight:700">${d.vehicle_plate||'—'}</td>
      <td style="font-size:13px">${d.vehicle_make||''} ${d.vehicle_model||''} ${d.vehicle_year||''}</td>
      <td>
        ${d.is_active
          ? `<span class="badge badge-green">Activo</span>`
          : `<span class="badge badge-red">Inactivo</span>`}
        ${d.in_queue
          ? `<span class="badge badge-blue" style="margin-left:4px">En cola #${d.queue_position}</span>`
          : ''}
      </td>
      <td>
        <div style="display:flex;align-items:center;gap:4px">
          <span style="color:var(--yellow);font-weight:700">★</span>
          <span style="font-size:13px">${parseFloat(d.rating||5).toFixed(1)}</span>
        </div>
      </td>
      <td>
        <div style="display:flex;gap:6px">
          ${d.in_queue
            ? `<button class="btn-icon danger" title="Quitar de cola" onclick="removeFromQueue('${d.id}')">✕ Cola</button>`
            : `<button class="btn-icon success" title="Agregar a cola" onclick="quickAddToQueue('${d.id}','${d.full_name}')">+ Cola</button>`}
          <button class="btn-icon" title="Activar/Desactivar" onclick="toggleDriver('${d.id}',${!d.is_active})">${d.is_active?'⏸':'▶'}</button>
        </div>
      </td>
    </tr>`).join('');
}

function filterDrivers() {
  const q = document.getElementById('searchDriver').value.toLowerCase();
  renderDrivers(allDrivers.filter(d =>
    (d.full_name||'').toLowerCase().includes(q) ||
    (d.phone||'').includes(q) ||
    (d.vehicle_plate||'').toLowerCase().includes(q) ||
    (d.unit_number||'').toLowerCase().includes(q)
  ));
}

async function toggleDriver(id, active) {
  try {
    await api(`/drivers/${id}`, { method: 'PATCH', body: JSON.stringify({ is_active: active }) });
    toast(`Conductor ${active ? 'activado' : 'desactivado'}`);
    loadDrivers();
  } catch (e) { toast(e.message, 'error'); }
}

async function quickAddToQueue(id, name) {
  try {
    const { position } = await api(`/queue/add/${id}`, { method: 'POST' });
    toast(`${name} agregado a la cola (posición ${position})`);
    loadDrivers();
  } catch (e) { toast(e.message, 'error'); }
}

async function removeFromQueue(driverId) {
  try {
    await api(`/queue/remove/${driverId}`, { method: 'DELETE' });
    toast('Conductor removido de la cola');
    loadDrivers();
    loadDashboard();
  } catch (e) { toast(e.message, 'error'); }
}

// ─── REGISTRAR CONDUCTOR ──────────────────────────────────────────────────────
async function submitDriver(e) {
  e.preventDefault();
  const btn = document.getElementById('submitDriverBtn');
  btn.textContent = 'Registrando...';
  btn.disabled = true;

  const fd = new FormData(e.target);
  const body = Object.fromEntries([...fd.entries()].filter(([,v]) => v));
  if (body.vehicle_year) body.vehicle_year = parseInt(body.vehicle_year);

  try {
    const { data } = await api('/drivers', { method: 'POST', body: JSON.stringify(body) });
    toast(`Conductor ${data.full_name} registrado exitosamente`);
    closeModal('modalDriver');
    e.target.reset();
    loadDrivers();
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    btn.textContent = 'Registrar conductor';
    btn.disabled = false;
  }
}

// ─── COLA ─────────────────────────────────────────────────────────────────────
async function loadQueue() {
  try {
    const { data } = await api('/queue');
    const el = document.getElementById('queueList');
    if (!data.length) {
      el.innerHTML = '<div class="empty-state">🔄 La cola está vacía.<br>Agrega conductores desde la lista de conductores o desde el panel derecho.</div>';
      return;
    }
    el.innerHTML = data.map(d => `
      <div class="queue-card" id="qc-${d.queue_id}">
        <div class="queue-pos${d.queue_position===1?' pos-1':''}">${d.queue_position}</div>
        <div style="font-size:24px">${vehicleEmoji(d.vehicle_type)}</div>
        <div class="queue-info">
          <div class="queue-name">${d.full_name}</div>
          <div class="queue-meta">Unidad <strong style="color:var(--yellow)">${d.unit_number||'—'}</strong> · ${d.vehicle_plate} · ${d.vehicle_make||''} ${d.vehicle_model||''} ${d.vehicle_color||''}</div>
          <div class="queue-meta" style="margin-top:2px">📞 ${d.phone} · ★ ${parseFloat(d.rating||5).toFixed(1)} · ${d.total_trips} viajes</div>
        </div>
        <div class="queue-actions">
          <button class="btn-icon" onclick="moveQueue('${d.queue_id}','up')" title="Subir">▲</button>
          <button class="btn-icon" onclick="moveQueue('${d.queue_id}','down')" title="Bajar">▼</button>
          <button class="btn-icon danger" onclick="removeFromQueueById('${d.driver_id}')" title="Quitar">✕</button>
        </div>
      </div>`).join('');
  } catch (e) { toast(e.message, 'error'); }
}

async function loadDriversForQueue() {
  try {
    const { data } = await api('/drivers');
    const sel = document.getElementById('addDriverSelect');
    const available = data.filter(d => !d.in_queue && d.is_active);
    sel.innerHTML = available.length
      ? `<option value="">Seleccionar conductor...</option>` +
        available.map(d => `<option value="${d.id}">Unidad ${d.unit_number||'—'} · ${d.full_name} · ${d.vehicle_plate}</option>`).join('')
      : '<option value="">No hay conductores disponibles</option>';
  } catch (e) { console.error(e); }
}

async function addToQueue() {
  const sel = document.getElementById('addDriverSelect');
  if (!sel.value) return toast('Selecciona un conductor', 'error');
  try {
    const { position } = await api(`/queue/add/${sel.value}`, { method: 'POST' });
    toast(`Conductor agregado a la cola (posición ${position})`);
    loadQueue();
    loadDriversForQueue();
    loadDashboard();
  } catch (e) { toast(e.message, 'error'); }
}

async function moveQueue(queueId, direction) {
  try {
    await api(`/queue/${queueId}/move`, { method: 'PATCH', body: JSON.stringify({ direction }) });
    loadQueue();
  } catch (e) { toast(e.message, 'error'); }
}

async function removeFromQueueById(driverId) {
  try {
    await api(`/queue/remove/${driverId}`, { method: 'DELETE' });
    toast('Conductor removido de la cola');
    loadQueue();
    loadDriversForQueue();
    loadDashboard();
  } catch (e) { toast(e.message, 'error'); }
}

// ─── VIAJES ───────────────────────────────────────────────────────────────────
let allTrips = [];

async function loadTrips() {
  try {
    const { data } = await api('/trips');
    allTrips = data;
    renderTrips(data);
  } catch (e) { toast(e.message, 'error'); }
}

function renderTrips(data) {
    const tbody = document.getElementById('tripsBody');
    if (!data.length) {
      tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No hay viajes registrados</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(t => `
      <tr>
        <td style="font-family:monospace;font-size:11px;color:var(--sub)">${t.id.slice(0,8)}...</td>
        <td style="font-size:13px">${t.client_name||'—'}<br><span style="color:var(--sub);font-size:11px">${t.client_phone||''}</span></td>
        <td style="font-size:13px">${t.driver_name||'<span style="color:var(--sub)">Sin asignar</span>'}</td>
        <td>${t.unit_number ? `<span class="badge badge-yellow">N° ${t.unit_number}</span>` : '—'}</td>
        <td style="font-size:12px;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${t.origin_address}</td>
        <td style="font-size:12px;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${t.dest_address}</td>
        <td>${tripBadge(t.status)}</td>
        <td style="font-size:12px;color:var(--sub)">${new Date(t.created_at).toLocaleString('es')}</td>
        <td>
          ${t.status==='pending'&&!t.driver_id
            ? `<button class="btn-sm" onclick="assignDriver('${t.id}')">🚕 Asignar</button>`
            : ''}
        </td>
      </tr>`).join('');
}

function filterTrips() {
  const q = document.getElementById('searchTrip').value.toLowerCase();
  const st = document.getElementById('filterTripStatus').value;
  
  const filtered = allTrips.filter(t => {
    const matchSearch = (t.client_name||'').toLowerCase().includes(q) ||
                        (t.driver_name||'').toLowerCase().includes(q) ||
                        (t.id||'').toLowerCase().includes(q) ||
                        (t.origin_address||'').toLowerCase().includes(q);
    
    let matchStatus = true;
    if (st === 'pending') matchStatus = ['pending', 'cancelled_no_drivers'].includes(t.status);
    else if (st === 'active') matchStatus = ['accepted', 'arrived', 'on_route', 'in_progress'].includes(t.status);
    else if (st === 'finished') matchStatus = ['completed', 'cancelled'].includes(t.status);
    
    return matchSearch && matchStatus;
  });
  renderTrips(filtered);
}

async function assignDriver(tripId) {
  try {
    const { data } = await api(`/trips/${tripId}/assign`, { method: 'POST' });
    toast(`Conductor asignado: ${data.driver.full_name} (Unidad ${data.driver.unit_number})`);
    loadTrips();
    loadDashboard();
  } catch (e) { toast(e.message, 'error'); }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function tripBadge(status) {
  const map = {
    pending:              ['badge-yellow', 'Pendiente'],
    cancelled_no_drivers: ['badge-red',    'Sin conductor'],
    accepted:             ['badge-blue',   'Aceptado'],
    arrived:              ['badge-blue',   'Llegó'],
    on_route:             ['badge-blue',   'En ruta'],
    in_progress:          ['badge-green',  'En curso'],
    completed:            ['badge-gray',   'Completado'],
    cancelled:            ['badge-red',    'Cancelado'],
  };
  const [cls, label] = map[status] || ['badge-gray', status];
  return `<span class="badge ${cls}">${label}</span>`;
}

function vehicleEmoji(type) {
  return { sedan:'🚗', suv:'🚙', van:'🚐', pickup:'🛻' }[type] || '🚕';
}

function openModal(id)  { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

// ─── Init ─────────────────────────────────────────────────────────────────────
checkStatus();
setInterval(checkStatus, 30000);
loadDashboard();

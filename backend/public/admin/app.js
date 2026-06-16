const API = 'https://taxiapp-production-1a53.up.railway.app/api/admin';

// ─── Navegación ───────────────────────────────────────────────────────────────
function navigate(view) {
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById('view-' + view).classList.add('active');
  document.querySelector(`[data-view="${view}"]`).classList.add('active');
  document.getElementById('pageTitle').textContent =
    { dashboard: 'Dashboard', drivers: 'Conductores', queue: 'Cola de Turnos', trips: 'Viajes', support: '🆘 Soporte' }[view];
  if (view === 'dashboard') loadDashboard();
  if (view === 'drivers')   loadDrivers();
  if (view === 'queue')     { loadQueue(); loadDriversForQueue(); }
  if (view === 'trips')     loadTrips();
  if (view === 'support')   loadSupportTickets();
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
          <button class="btn-icon" title="Editar" onclick="editDriver('${d.id}')">✏️</button>
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

// ─── REGISTRAR / EDITAR CONDUCTOR ─────────────────────────────────────────────
function openNewDriverModal() {
  document.getElementById('driverForm').reset();
  document.getElementById('driver_id_input').value = '';
  document.getElementById('submitDriverBtn').textContent = 'Registrar conductor';
  document.querySelector('#modalDriver h2').textContent = 'Registrar Conductor';
  openModal('modalDriver');
}

function editDriver(id) {
  const d = allDrivers.find(x => x.id === id);
  if (!d) return;
  document.getElementById('driverForm').reset();
  const form = document.getElementById('driverForm');
  
  form.elements['driver_id'].value = d.id;
  form.elements['full_name'].value = d.full_name || '';
  form.elements['phone'].value = d.phone || '';
  form.elements['email'].value = d.email || '';
  form.elements['avatar_url'].value = d.avatar_url || '';
  form.elements['unit_number'].value = d.unit_number || '';
  form.elements['vehicle_plate'].value = d.vehicle_plate || '';
  form.elements['vehicle_make'].value = d.vehicle_make || '';
  form.elements['vehicle_model'].value = d.vehicle_model || '';
  form.elements['vehicle_year'].value = d.vehicle_year || '';
  form.elements['vehicle_color'].value = d.vehicle_color || '';
  form.elements['vehicle_type'].value = d.vehicle_type || 'sedan';
  form.elements['license_number'].value = d.license_number || '';
  form.elements['vehicle_photo_url'].value = d.vehicle_photo_url || '';

  document.getElementById('submitDriverBtn').textContent = 'Guardar cambios';
  document.querySelector('#modalDriver h2').textContent = 'Editar Conductor';
  openModal('modalDriver');
}
async function submitDriver(e) {
  e.preventDefault();
  const btn = document.getElementById('submitDriverBtn');
  btn.textContent = 'Guardando...';
  btn.disabled = true;

  const fd = new FormData(e.target);
  const body = Object.fromEntries([...fd.entries()].filter(([,v]) => v));
  if (body.vehicle_year) body.vehicle_year = parseInt(body.vehicle_year);
  
  const driverId = body.driver_id;
  delete body.driver_id;

  try {
    if (driverId) {
      await api(`/drivers/${driverId}`, { method: 'PATCH', body: JSON.stringify(body) });
      toast('Conductor actualizado exitosamente');
    } else {
      const { data } = await api('/drivers', { method: 'POST', body: JSON.stringify(body) });
      toast(`Conductor ${data.full_name} registrado exitosamente`);
    }
    closeModal('modalDriver');
    e.target.reset();
    loadDrivers();
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    btn.textContent = driverId ? 'Guardar cambios' : 'Registrar conductor';
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
function showTripTab(tabName) {
  // Update buttons
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.remove('active');
    btn.style.borderBottomColor = 'transparent';
    btn.style.color = '#64748b';
  });
  const activeBtn = document.getElementById('tab-btn-' + tabName);
  if (activeBtn) {
    activeBtn.classList.add('active');
    activeBtn.style.borderBottomColor = '#3b82f6';
    activeBtn.style.color = 'inherit';
  }

  // Update tables
  document.getElementById('trips-table-pending').style.display = 'none';
  document.getElementById('trips-table-active').style.display = 'none';
  document.getElementById('trips-table-completed').style.display = 'none';
  
  const targetTable = document.getElementById('trips-table-' + tabName);
  if (targetTable) targetTable.style.display = 'block';
}

async function loadTrips() {
  try {
    const { data } = await api('/trips');
    
    const pending = data.filter(t => t.status === 'pending');
    const active = data.filter(t => ['accepted', 'arrived', 'on_route', 'in_progress'].includes(t.status));
    const completed = data.filter(t => ['completed', 'cancelled', 'cancelled_no_drivers'].includes(t.status));

    const renderRows = (trips, emptyMsg) => {
      if (!trips.length) return `<tr><td colspan="9" class="empty-state">${emptyMsg}</td></tr>`;
      return trips.map(t => `
      <tr>
        <td style="font-family:monospace;font-size:11px;color:var(--sub)">${t.id.slice(0,8)}...</td>
        <td style="font-size:13px">${t.client_name||'&mdash;'}<br><span style="color:var(--sub);font-size:11px">${t.client_phone||''}</span></td>
        <td style="font-size:13px">${t.driver_name||'<span style="color:var(--sub)">Sin asignar</span>'}</td>
        <td>${t.unit_number ? `<span class="badge badge-yellow">N° ${t.unit_number}</span>` : '&mdash;'}</td>
        <td style="font-size:12px;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${t.origin_address}">${t.origin_address}</td>
        <td style="font-size:12px;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${t.dest_address}">${t.dest_address}</td>
        <td>${tripBadge(t.status)}</td>
        <td style="font-size:12px;color:var(--sub)">${new Date(t.created_at).toLocaleString('es')}</td>
        <td>
          <div style="display:flex;gap:6px;flex-wrap:wrap">
            ${t.status==='pending'&&!t.driver_id
              ? `<button class="btn-sm" onclick="assignDriver('${t.id}')">&#x1F695; Asignar</button>`
              : ''}
            ${['pending','accepted','arrived','on_route','in_progress'].includes(t.status)
              ? `<button class="btn-sm danger" onclick="openCancelTripModal('${t.id}','${(t.client_name||'').replace(/'/g,'\\&apos;')}')">&#x274C; Cancelar</button>`
              : (t.status === 'cancelled' && t.cancel_reason
                ? `<span style="font-size:11px;color:var(--red);max-width:120px;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${t.cancel_reason}">&#x26D4; ${t.cancel_reason}</span>`
                : '')}
          </div>
        </td>
      </tr>`);
    };

    document.getElementById('tripsBodyPending').innerHTML = renderRows(pending, 'No hay viajes pendientes');
    document.getElementById('tripsBodyActive').innerHTML = renderRows(active, 'No hay viajes en proceso');
    document.getElementById('tripsBodyCompleted').innerHTML = renderRows(completed, 'No hay viajes finalizados');

  } catch (e) { toast(e.message, 'error'); }
}

async function assignDriver(tripId) {
  try {
    const { data } = await api(`/trips/${tripId}/assign`, { method: 'POST' });
    toast(`Conductor asignado: ${data.driver.full_name} (Unidad ${data.driver.unit_number})`);
    loadTrips();
    loadDashboard();
  } catch (e) { toast(e.message, 'error'); }
}

// ─── Cancelar viaje (admin) ──────────────────────────────────────────────────────────────────────────────
let _cancelTripId = null;

function openCancelTripModal(tripId, clientName) {
  _cancelTripId = tripId;
  document.getElementById('cancelTripClientName').textContent = clientName || tripId.slice(0,8);
  document.getElementById('cancelReasonInput').value = '';
  openModal('modalCancelTrip');
}

async function submitCancelTrip() {
  if (!_cancelTripId) return;
  const reason = document.getElementById('cancelReasonInput').value.trim();
  if (!reason) { toast('Por favor ingresa el motivo de cancelación', 'error'); return; }
  const btn = document.getElementById('confirmCancelBtn');
  btn.disabled = true;
  btn.textContent = 'Cancelando...';
  try {
    await api(`/trips/${_cancelTripId}/cancel`, {
      method: 'PATCH',
      body: JSON.stringify({ reason })
    });
    toast('Viaje cancelado correctamente');
    closeModal('modalCancelTrip');
    _cancelTripId = null;
    loadTrips();
    loadDashboard();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Confirmar cancelación';
  }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function tripBadge(status) {
  const map = {
    pending:              ['badge-yellow', 'Pendiente'],
    accepted:             ['badge-blue',   'Aceptado'],
    arrived:              ['badge-blue',   'Llegó'],
    on_route:             ['badge-blue',   'En ruta'],
    in_progress:          ['badge-green',  'En curso'],
    completed:            ['badge-gray',   'Completado'],
    cancelled:            ['badge-red',    'Cancelado'],
    cancelled_no_drivers: ['badge-red',    'Sin conductor'],
  };
  const [cls, label] = map[status] || ['badge-gray', status];
  return `<span class="badge ${cls}">${label}</span>`;
}

function vehicleEmoji(type) {
  return { sedan:'🚗', suv:'🚙', van:'🚐', pickup:'🛻' }[type] || '🚕';
}

function openModal(id)  { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

// ─── SOPORTE ──────────────────────────────────────────────────────────────────
let currentSupportUserId = null;
let currentSupportUser   = null;
let supportPollInterval  = null;

async function loadSupportTickets() {
  try {
    const { data } = await api('/support/tickets');
    const el = document.getElementById('supportTicketList');

    // Actualizar badge global de no leídos
    const totalUnread = data.reduce((s, t) => s + parseInt(t.unread_count || 0), 0);
    const badge = document.getElementById('supportUnreadBadge');
    if (totalUnread > 0) { badge.style.display = 'inline'; badge.textContent = totalUnread; }
    else badge.style.display = 'none';

    if (!data.length) {
      el.innerHTML = '<div class="empty-state">💬 No hay conversaciones de soporte aún</div>';
      return;
    }

    el.innerHTML = data.map(t => {
      const typeLabel = { sos: '🆘 SOS', cancel: '❌ Cancelación', support: '💬 Soporte' }[t.type] || '💬 Soporte';
      const unread = parseInt(t.unread_count || 0);
      return `
        <div class="support-ticket-item ${currentSupportUserId === t.user_id ? 'active' : ''}"
             onclick="openSupportChat('${t.user_id}','${t.full_name || ''}','${t.phone || ''}')">
          <div class="support-ticket-name">
            ${t.full_name || 'Usuario'}
            ${unread > 0 ? `<span class="support-unread">${unread}</span>` : ''}
          </div>
          <div class="support-ticket-preview">${t.last_message || '—'}</div>
          <div style="display:flex;justify-content:space-between;margin-top:4px">
            <span class="support-ticket-type ${t.type}">${typeLabel}</span>
            <span style="font-size:10px;color:var(--sub)">${new Date(t.created_at).toLocaleString('es')}</span>
          </div>
        </div>`;
    }).join('');
  } catch (e) { console.error('loadSupportTickets:', e); }
}

async function openSupportChat(userId, userName, userPhone) {
  currentSupportUserId = userId;
  currentSupportUser   = { name: userName, phone: userPhone };

  document.getElementById('supportChatPanel').style.display = 'flex';
  document.getElementById('supportChatUserName').textContent = userName || userId;
  document.getElementById('supportChatUserPhone').textContent = userPhone ? `📞 ${userPhone}` : '';

  await loadSupportChatMessages();

  // Polling automático cada 4s
  clearInterval(supportPollInterval);
  supportPollInterval = setInterval(loadSupportChatMessages, 4000);
  loadSupportTickets(); // Actualizar badges
}

async function loadSupportChatMessages() {
  if (!currentSupportUserId) return;
  try {
    const { data } = await api(`/support/${currentSupportUserId}/messages`);
    const el = document.getElementById('supportChatMessages');
    el.innerHTML = data.map(m => {
      const isAdmin = m.sender_role === 'admin';
      const typeChip = m.type !== 'support'
        ? `<span class="${m.type === 'sos' ? 'sos-badge' : ''}" style="font-size:10px;margin-right:6px">${m.type === 'sos' ? '🆘 SOS' : '❌ Cancelación'}</span>`
        : '';
      return `
        <div class="support-msg ${isAdmin ? 'from-admin' : 'from-user'}">
          ${!isAdmin ? typeChip : ''}
          ${m.message}
          <div class="support-msg-meta">${isAdmin ? '👨‍💼 Admin' : m.user_name || 'Usuario'} · ${new Date(m.created_at).toLocaleTimeString('es')}</div>
        </div>`;
    }).join('');
    el.scrollTop = el.scrollHeight;
  } catch (e) { console.error('loadSupportChatMessages:', e); }
}

async function sendAdminSupportMessage() {
  const input = document.getElementById('supportAdminInput');
  const msg = input.value.trim();
  if (!msg || !currentSupportUserId) return;
  input.value = '';
  try {
    await api(`/support/${currentSupportUserId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ message: msg, sender_role: 'admin' })
    });
    await loadSupportChatMessages();
    loadSupportTickets();
  } catch (e) { toast('Error enviando mensaje', 'error'); }
}

// ─── Init ─────────────────────────────────────────────────────────────────────
checkStatus();
setInterval(checkStatus, 30000);
setInterval(loadSupportTickets, 15000); // Polling de tickets nuevos
loadDashboard();

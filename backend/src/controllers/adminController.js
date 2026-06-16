import { query, withTransaction } from '../db/pool.js';
import bcrypt from 'bcryptjs';

// ──────────────────────────────────────────────────────────────────────────────
// DASHBOARD — estadísticas generales
// ──────────────────────────────────────────────────────────────────────────────
export const getDashboard = async (_req, res) => {
  try {
    const [drivers, clients, trips, queue, activeTrips] = await Promise.all([
      query("SELECT COUNT(*) FROM users WHERE role_id=(SELECT id FROM roles WHERE name='driver')"),
      query("SELECT COUNT(*) FROM users WHERE role_id=(SELECT id FROM roles WHERE name='client')"),
      query("SELECT COUNT(*) FROM trips"),
      query("SELECT COUNT(*) FROM driver_queue WHERE is_active=TRUE"),
      query("SELECT COUNT(*) FROM trips WHERE status IN ('pending','accepted','on_route','in_progress')"),
    ]);

    res.json({
      success: true,
      data: {
        total_drivers:  parseInt(drivers.rows[0].count),
        total_clients:  parseInt(clients.rows[0].count),
        total_trips:    parseInt(trips.rows[0].count),
        queue_size:     parseInt(queue.rows[0].count),
        active_trips:   parseInt(activeTrips.rows[0].count),
      },
    });
  } catch (err) {
    console.error('getDashboard:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTORES — listar
// ──────────────────────────────────────────────────────────────────────────────
export const listDrivers = async (_req, res) => {
  try {
    const result = await query(`
      SELECT u.id, u.full_name, u.phone, u.email, u.avatar_url,
             u.is_active, u.is_verified, u.created_at,
             dp.unit_number, dp.vehicle_make, dp.vehicle_model, dp.vehicle_year,
             dp.vehicle_plate, dp.vehicle_color, dp.vehicle_type, dp.vehicle_photo_url,
             dp.license_number, dp.is_available, dp.is_approved,
             dp.rating, dp.total_trips,
             CASE WHEN dq.id IS NOT NULL AND dq.is_active THEN TRUE ELSE FALSE END AS in_queue,
             dq.queue_position
      FROM users u
      JOIN roles r ON u.role_id = r.id AND r.name = 'driver'
      LEFT JOIN driver_profiles dp ON dp.user_id = u.id
      LEFT JOIN driver_queue dq ON dq.driver_id = u.id AND dq.is_active = TRUE
      ORDER BY u.created_at DESC
    `);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('listDrivers:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTORES — registrar
// ──────────────────────────────────────────────────────────────────────────────
export const registerDriver = async (req, res) => {
  const {
    full_name, phone, email, password = '123456',
    unit_number, vehicle_make, vehicle_model, vehicle_year,
    vehicle_plate, vehicle_color, vehicle_type = 'sedan',
    license_number, avatar_url,
  } = req.body;

  if (!full_name || !phone || !unit_number || !vehicle_plate) {
    return res.status(400).json({
      success: false,
      message: 'Campos requeridos: full_name, phone, unit_number, vehicle_plate',
    });
  }

  try {
    // Verificar duplicados
    const dup = await query('SELECT id FROM users WHERE phone=$1', [phone]);
    if (dup.rows.length > 0)
      return res.status(409).json({ success: false, message: 'Ya existe un conductor con ese teléfono.' });

    const dupPlate = await query('SELECT id FROM driver_profiles WHERE vehicle_plate=$1', [vehicle_plate]);
    if (dupPlate.rows.length > 0)
      return res.status(409).json({ success: false, message: 'Ya existe un conductor con esa placa.' });

    const dupUnit = await query('SELECT id FROM driver_profiles WHERE unit_number=$1', [unit_number]);
    if (dupUnit.rows.length > 0)
      return res.status(409).json({ success: false, message: 'El número de unidad ya está en uso.' });

    const result = await withTransaction(async (client) => {
      const roleRes = await client.query("SELECT id FROM roles WHERE name='driver'");
      const roleId  = roleRes.rows[0].id;
      const pwHash  = await bcrypt.hash(password, 10);

      const userRes = await client.query(
        `INSERT INTO users (role_id, full_name, phone, email, password_hash, avatar_url,
           is_active, is_verified, phone_verified_at, kyc_status)
         VALUES ($1,$2,$3,$4,$5,$6,TRUE,TRUE,NOW(),'approved')
         RETURNING id, full_name, phone, email, avatar_url`,
        [roleId, full_name, phone, email || null, pwHash, avatar_url || null]
      );
      const user = userRes.rows[0];

      await client.query(
        `INSERT INTO driver_profiles (user_id, unit_number, vehicle_make, vehicle_model,
           vehicle_year, vehicle_plate, vehicle_color, vehicle_type, license_number, is_approved)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,TRUE)`,
        [user.id, unit_number, vehicle_make, vehicle_model,
         vehicle_year, vehicle_plate.toUpperCase(), vehicle_color, vehicle_type, license_number]
      );

      return user;
    });

    res.status(201).json({
      success: true,
      message: 'Conductor registrado exitosamente.',
      data: result,
    });
  } catch (err) {
    console.error('registerDriver:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTOR — actualizar
// ──────────────────────────────────────────────────────────────────────────────
export const updateDriver = async (req, res) => {
  const { id } = req.params;
  const { is_active, is_approved, is_available, avatar_url,
          vehicle_make, vehicle_model, vehicle_color, unit_number, vehicle_photo_url } = req.body;

  try {
    if (is_active !== undefined)
      await query('UPDATE users SET is_active=$1 WHERE id=$2', [is_active, id]);

    await query(
      `UPDATE driver_profiles
       SET is_approved=COALESCE($1,is_approved),
           is_available=COALESCE($2,is_available),
           vehicle_make=COALESCE($3,vehicle_make),
           vehicle_model=COALESCE($4,vehicle_model),
           vehicle_color=COALESCE($5,vehicle_color),
           unit_number=COALESCE($6,unit_number),
           vehicle_photo_url=COALESCE($7,vehicle_photo_url),
           updated_at=NOW()
       WHERE user_id=$8`,
      [is_approved, is_available, vehicle_make, vehicle_model, vehicle_color, unit_number, vehicle_photo_url, id]
    );

    if (avatar_url)
      await query('UPDATE users SET avatar_url=$1 WHERE id=$2', [avatar_url, id]);

    res.json({ success: true, message: 'Conductor actualizado.' });
  } catch (err) {
    console.error('updateDriver:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// COLA — ver cola actual
// ──────────────────────────────────────────────────────────────────────────────
export const getQueue = async (_req, res) => {
  try {
    const result = await query(`
      SELECT dq.id AS queue_id, dq.queue_position, dq.added_at,
             u.id AS driver_id, u.full_name, u.phone, u.avatar_url,
             dp.unit_number, dp.vehicle_make, dp.vehicle_model,
             dp.vehicle_plate, dp.vehicle_color, dp.vehicle_type,
             dp.rating, dp.total_trips
      FROM driver_queue dq
      JOIN users u ON dq.driver_id = u.id
      JOIN driver_profiles dp ON dp.user_id = u.id
      WHERE dq.is_active = TRUE
      ORDER BY dq.queue_position ASC
    `);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getQueue:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// COLA — agregar conductor
// ──────────────────────────────────────────────────────────────────────────────
export const addToQueue = async (req, res) => {
  const { driver_id } = req.params;
  try {
    // Verificar que no esté ya en la cola
    const existing = await query(
      'SELECT id FROM driver_queue WHERE driver_id=$1 AND is_active=TRUE', [driver_id]
    );
    if (existing.rows.length > 0)
      return res.status(409).json({ success: false, message: 'El conductor ya está en la cola.' });

    // Obtener la posición máxima actual
    const maxPos = await query(
      'SELECT COALESCE(MAX(queue_position),0) AS max_pos FROM driver_queue WHERE is_active=TRUE'
    );
    const nextPos = parseInt(maxPos.rows[0].max_pos) + 1;

    await query(
      'INSERT INTO driver_queue (driver_id, queue_position) VALUES ($1,$2)',
      [driver_id, nextPos]
    );

    // Actualizar disponibilidad del conductor
    await query('UPDATE driver_profiles SET is_available=TRUE WHERE user_id=$1', [driver_id]);

    res.json({ success: true, message: 'Conductor agregado a la cola.', position: nextPos });
  } catch (err) {
    console.error('addToQueue:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// COLA — quitar conductor
// ──────────────────────────────────────────────────────────────────────────────
export const removeFromQueue = async (req, res) => {
  const { driver_id } = req.params;
  try {
    await query(
      'UPDATE driver_queue SET is_active=FALSE WHERE driver_id=$1 AND is_active=TRUE',
      [driver_id]
    );
    await query('UPDATE driver_profiles SET is_available=FALSE WHERE user_id=$1', [driver_id]);
    // Reordenar posiciones
    await reorderQueue();
    res.json({ success: true, message: 'Conductor removido de la cola.' });
  } catch (err) {
    console.error('removeFromQueue:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// COLA — reordenar (mover arriba/abajo)
// ──────────────────────────────────────────────────────────────────────────────
export const moveInQueue = async (req, res) => {
  const { queue_id } = req.params;
  const { direction } = req.body; // 'up' | 'down'
  try {
    const current = await query(
      'SELECT queue_position FROM driver_queue WHERE id=$1 AND is_active=TRUE', [queue_id]
    );
    if (current.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Entrada de cola no encontrada.' });

    const pos = current.rows[0].queue_position;
    const swapPos = direction === 'up' ? pos - 1 : pos + 1;

    const swap = await query(
      'SELECT id FROM driver_queue WHERE queue_position=$1 AND is_active=TRUE', [swapPos]
    );
    if (swap.rows.length === 0)
      return res.status(400).json({ success: false, message: 'No se puede mover más en esa dirección.' });

    // Intercambiar posiciones
    await query('UPDATE driver_queue SET queue_position=$1 WHERE id=$2', [swapPos, queue_id]);
    await query('UPDATE driver_queue SET queue_position=$1 WHERE id=$2', [pos, swap.rows[0].id]);

    res.json({ success: true, message: 'Posición actualizada.' });
  } catch (err) {
    console.error('moveInQueue:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — listar con detalle
// ──────────────────────────────────────────────────────────────────────────────
export const listTrips = async (_req, res) => {
  try {
    const result = await query(`
      SELECT t.*,
             uc.full_name AS client_name, uc.phone AS client_phone,
             ud.full_name AS driver_name, ud.phone AS driver_phone,
             dp.unit_number, dp.vehicle_plate, dp.vehicle_make, dp.vehicle_model
      FROM trips t
      LEFT JOIN users uc ON t.client_id = uc.id
      LEFT JOIN users ud ON t.driver_id = ud.id
      LEFT JOIN driver_profiles dp ON dp.user_id = t.driver_id
      ORDER BY t.created_at DESC
      LIMIT 100
    `);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('listTrips:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTOR — obtener ubicación (para tracking en tiempo real)
// ──────────────────────────────────────────────────────────────────────────────
export const getDriverLocation = async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query(
      'SELECT current_lat, current_lng FROM driver_profiles WHERE user_id=$1',
      [id]
    );
    if (result.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Conductor no encontrado' });
    
    const { current_lat, current_lng } = result.rows[0];
    res.json({
      success: true,
      data: { lat: parseFloat(current_lat) || 0, lng: parseFloat(current_lng) || 0 }
    });
  } catch (err) {
    console.error('getDriverLocation:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — asignar conductor (próximo en cola)
// ──────────────────────────────────────────────────────────────────────────────
export const assignNextDriver = async (req, res) => {
  const { trip_id } = req.params;
  try {
    // Obtener primer conductor de la cola
    const queueRes = await query(`
      SELECT dq.id AS queue_id, dq.driver_id
      FROM driver_queue dq
      JOIN driver_profiles dp ON dp.user_id = dq.driver_id
      WHERE dq.is_active = TRUE AND dp.is_approved = TRUE
      ORDER BY dq.queue_position ASC
      LIMIT 1
    `);

    if (queueRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'No hay conductores disponibles en la cola.' });

    const { queue_id, driver_id } = queueRes.rows[0];

    await withTransaction(async (client) => {
      // Asignar al viaje
      await client.query(
        `UPDATE trips SET driver_id=$1, status='accepted', accepted_at=NOW() WHERE id=$2`,
        [driver_id, trip_id]
      );
      // Sacar de la cola (va al final cuando termine el viaje)
      await client.query(
        'UPDATE driver_queue SET is_active=FALSE WHERE id=$1', [queue_id]
      );
      await client.query(
        'UPDATE driver_profiles SET is_available=FALSE WHERE user_id=$1', [driver_id]
      );
    });

    await reorderQueue();

    // Obtener datos completos del conductor para respuesta
    const driverData = await query(`
      SELECT u.id, u.full_name, u.phone, u.avatar_url,
             dp.unit_number, dp.vehicle_make, dp.vehicle_model,
             dp.vehicle_year, dp.vehicle_plate, dp.vehicle_color, dp.vehicle_type, dp.vehicle_photo_url,
             dp.rating, dp.total_trips
      FROM users u
      JOIN driver_profiles dp ON dp.user_id = u.id
      WHERE u.id = $1
    `, [driver_id]);

    res.json({
      success: true,
      message: 'Conductor asignado exitosamente.',
      data: { driver: driverData.rows[0] },
    });
  } catch (err) {
    console.error('assignNextDriver:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — crear viaje y asignar conductor automáticamente
// ──────────────────────────────────────────────────────────────────────────────
export const createTrip = async (req, res) => {
  const {
    client_id, origin_address, origin_lat, origin_lng,
    dest_address, dest_lat, dest_lng,
    estimated_fare, distance_km, payment_method = 'cash',
  } = req.body;

  try {
    // Crear el viaje
    const tripRes = await query(
      `INSERT INTO trips (client_id, origin_address, origin_lat, origin_lng,
         dest_address, dest_lat, dest_lng, estimated_fare, distance_km, payment_method)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       RETURNING id`,
      [client_id, origin_address, origin_lat, origin_lng,
       dest_address, dest_lat, dest_lng, estimated_fare, distance_km, payment_method]
    );
    const tripId = tripRes.rows[0].id;

    // Buscar primer conductor en la cola
    const queueRes = await query(`
      SELECT dq.id AS queue_id, dq.driver_id
      FROM driver_queue dq
      JOIN driver_profiles dp ON dp.user_id = dq.driver_id
      WHERE dq.is_active = TRUE AND dp.is_approved = TRUE
      ORDER BY dq.queue_position ASC
      LIMIT 1
    `);

    let driverData = null;

    if (queueRes.rows.length > 0) {
      const { queue_id, driver_id } = queueRes.rows[0];
      await withTransaction(async (client) => {
        await client.query(
          `UPDATE trips SET driver_id=$1, status='pending' WHERE id=$2`,
          [driver_id, tripId]
        );
        await client.query('UPDATE driver_queue SET is_active=FALSE WHERE id=$1', [queue_id]);
        await client.query('UPDATE driver_profiles SET is_available=FALSE WHERE user_id=$1', [driver_id]);
      });
      await reorderQueue();
    }

    res.status(201).json({
      success: true,
      message: queueRes.rows.length > 0 ? 'Viaje creado. Esperando que el conductor acepte.' : 'Viaje creado. Sin conductores disponibles.',
      data: { trip_id: tripId, driver: null },
    });
  } catch (err) {
    console.error('createTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// Helper: reordenar la cola
// ──────────────────────────────────────────────────────────────────────────────
async function reorderQueue() {
  const rows = await query(
    'SELECT id FROM driver_queue WHERE is_active=TRUE ORDER BY queue_position ASC'
  );
  for (let i = 0; i < rows.rows.length; i++) {
    await query('UPDATE driver_queue SET queue_position=$1 WHERE id=$2', [i + 1, rows.rows[i].id]);
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTOR — estado en cola (para la app del conductor)
// ──────────────────────────────────────────────────────────────────────────────
export const getDriverQueueStatus = async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query(
      'SELECT is_active, queue_position FROM driver_queue WHERE driver_id=$1 AND is_active=TRUE',
      [id]
    );
    const inQueue = result.rows.length > 0;
    res.json({
      success: true,
      data: {
        in_queue:       inQueue,
        queue_position: inQueue ? result.rows[0].queue_position : null,
        is_active:      inQueue,
      }
    });
  } catch (err) {
    console.error('getDriverQueueStatus:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTOR — actualizar ubicación (desde la app)
// ──────────────────────────────────────────────────────────────────────────────
export const updateDriverLocation = async (req, res) => {
  const { id } = req.params;
  const { lat, lng } = req.body;
  try {
    await query(
      'UPDATE driver_profiles SET current_lat=$1, current_lng=$2 WHERE user_id=$3',
      [lat, lng, id]
    );
    res.json({ success: true, message: 'Ubicación actualizada.' });
  } catch (err) {
    console.error('updateDriverLocation:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTOR — viaje pendiente asignado a él (polling desde la app)
// ──────────────────────────────────────────────────────────────────────────────
export const getPendingTrip = async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query(`
      SELECT t.id AS trip_id, t.origin_address, t.dest_address,
             t.origin_lat, t.origin_lng, t.dest_lat, t.dest_lng,
             t.distance_km, t.estimated_fare, t.status, t.payment_method,
             uc.full_name AS client_name
      FROM trips t
      LEFT JOIN users uc ON t.client_id = uc.id
      WHERE t.driver_id = $1 AND t.status IN ('pending', 'accepted', 'arrived', 'on_route', 'in_progress')
      ORDER BY t.created_at DESC
      LIMIT 1
    `, [id]);

    if (result.rows.length === 0)
      return res.json({ success: true, data: null });

    res.json({ success: true, data: result.rows[0] });
  } catch (err) {
    console.error('getPendingTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

export const getActiveTripForClient = async (req, res) => {
  const { clientId } = req.params;
  try {
    const result = await query(`
      SELECT t.id AS trip_id, t.origin_address, t.dest_address,
             t.origin_lat, t.origin_lng, t.dest_lat, t.dest_lng,
             t.distance_km, t.estimated_fare, t.status, t.payment_method,
             t.driver_id
      FROM trips t
      WHERE t.client_id = $1 AND t.status IN ('pending', 'accepted', 'arrived', 'on_route', 'in_progress')
      ORDER BY t.created_at DESC
      LIMIT 1
    `, [clientId]);

    if (result.rows.length === 0)
      return res.json({ success: true, data: null });

    res.json({ success: true, data: result.rows[0] });
  } catch (err) {
    console.error('getActiveTripForClient:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — aceptar viaje (conductor lo acepta)
// ──────────────────────────────────────────────────────────────────────────────
export const acceptTrip = async (req, res) => {
  const { tripId } = req.params;
  try {
    const tripRes = await query(
      `UPDATE trips SET status='accepted', accepted_at=NOW() WHERE id=$1 RETURNING id`,
      [tripId]
    );
    if (tripRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado.' });

    res.json({ success: true, message: 'Viaje aceptado.' });
  } catch (err) {
    console.error('acceptTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — rechazar viaje (conductor lo rechaza)
// ──────────────────────────────────────────────────────────────────────────────
export const rejectTrip = async (req, res) => {
  const { tripId } = req.params;
  try {
    // Buscar próximo conductor en la cola
    const queueRes = await query(`
      SELECT dq.id AS queue_id, dq.driver_id
      FROM driver_queue dq
      JOIN driver_profiles dp ON dp.user_id = dq.driver_id
      WHERE dq.is_active = TRUE AND dp.is_approved = TRUE
      ORDER BY dq.queue_position ASC
      LIMIT 1
    `);

    if (queueRes.rows.length > 0) {
      const { queue_id, driver_id } = queueRes.rows[0];
      await withTransaction(async (client) => {
        await client.query(
          `UPDATE trips SET driver_id=$1 WHERE id=$2`,
          [driver_id, tripId]
        );
        await client.query('UPDATE driver_queue SET is_active=FALSE WHERE id=$1', [queue_id]);
        await client.query('UPDATE driver_profiles SET is_available=FALSE WHERE user_id=$1', [driver_id]);
      });
      await reorderQueue();
      res.json({ success: true, message: 'Viaje asignado al siguiente conductor.' });
    } else {
      await query(`UPDATE trips SET status='cancelled_no_drivers', driver_id=NULL WHERE id=$1`, [tripId]);
      res.json({ success: true, message: 'No hay más conductores disponibles.' });
    }
  } catch (err) {
    console.error('rejectTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — finalizar viaje (conductor lo completa)
// ──────────────────────────────────────────────────────────────────────────────
export const finishTrip = async (req, res) => {
  const { tripId } = req.params;
  try {
    const tripRes = await query(
      `UPDATE trips SET status='completed', completed_at=NOW() WHERE id=$1 RETURNING driver_id`,
      [tripId]
    );
    if (tripRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado.' });

    const driverId = tripRes.rows[0].driver_id;
    if (driverId) {
      await query('UPDATE driver_profiles SET total_trips=total_trips+1, is_available=TRUE WHERE user_id=$1', [driverId]);
    }
    res.json({ success: true, message: 'Viaje finalizado. ¡Buen trabajo!' });
  } catch (err) {
    console.error('finishTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — cancelar viaje (admin)
// ──────────────────────────────────────────────────────────────────────────────
export const cancelTrip = async (req, res) => {
  const { tripId } = req.params;
  const { reason = 'Cancelado por administrador' } = req.body;
  try {
    const tripRes = await query(
      `UPDATE trips
         SET status='cancelled', cancel_reason=$1, cancelled_at=NOW()
       WHERE id=$2
         AND status IN ('pending','accepted','arrived','on_route','in_progress')
       RETURNING id, driver_id`,
      [reason, tripId]
    );
    if (tripRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado o ya finalizado.' });

    const driverId = tripRes.rows[0].driver_id;

    // Si había conductor asignado, restaurarlo al final de la cola
    if (driverId) {
      await query('UPDATE driver_profiles SET is_available=TRUE WHERE user_id=$1', [driverId]);
      const existing = await query(
        'SELECT id FROM driver_queue WHERE driver_id=$1 AND is_active=TRUE', [driverId]
      );
      if (existing.rows.length === 0) {
        const maxPos = await query(
          'SELECT COALESCE(MAX(queue_position),0) AS max_pos FROM driver_queue WHERE is_active=TRUE'
        );
        const nextPos = parseInt(maxPos.rows[0].max_pos) + 1;
        await query(
          'INSERT INTO driver_queue (driver_id, queue_position) VALUES ($1,$2)',
          [driverId, nextPos]
        );
      }
    }

    res.json({ success: true, message: 'Viaje cancelado correctamente.', cancel_reason: reason });
  } catch (err) {
    console.error('cancelTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — solicitar cancelación (cliente)
// ──────────────────────────────────────────────────────────────────────────────
export const requestCancelTrip = async (req, res) => {
  const { tripId } = req.params;
  try {
    const result = await query(
      `UPDATE trips SET cancel_request_status='pending' WHERE id=$1 AND status IN ('pending', 'accepted', 'arrived', 'on_route', 'in_progress') RETURNING id`,
      [tripId]
    );
    if (result.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado o ya finalizado.' });
    
    res.json({ success: true, message: 'Solicitud de cancelación enviada al conductor.' });
  } catch (err) {
    console.error('requestCancelTrip:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — rechazar solicitud de cancelación (conductor)
// ──────────────────────────────────────────────────────────────────────────────
export const rejectCancelRequest = async (req, res) => {
  const { tripId } = req.params;
  try {
    const result = await query(
      `UPDATE trips SET cancel_request_status='rejected' WHERE id=$1 AND cancel_request_status='pending' RETURNING id`,
      [tripId]
    );
    if (result.rows.length === 0)
      return res.status(404).json({ success: false, message: 'No hay solicitud pendiente.' });

    res.json({ success: true, message: 'Solicitud rechazada.' });
  } catch (err) {
    console.error('rejectCancelRequest:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// VIAJES — obtener estado (cliente hace polling para detectar finalización)
// ──────────────────────────────────────────────────────────────────────────────
export const getTripStatus = async (req, res) => {
  const { tripId } = req.params;
  try {
    const result = await query(
      `SELECT t.id, t.status, t.driver_id, t.cancel_reason, t.cancel_request_status
       FROM trips t
       WHERE t.id = $1`,
      [tripId]
    );
    if (result.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado.' });

    const tripData = result.rows[0];

    if (tripData.driver_id) {
      const dr = await query(`
        SELECT u.id, u.full_name, u.phone, u.avatar_url,
               dp.unit_number, dp.vehicle_make, dp.vehicle_model,
               dp.vehicle_year, dp.vehicle_plate, dp.vehicle_color, dp.vehicle_type, dp.vehicle_photo_url,
               dp.rating, dp.total_trips
        FROM users u JOIN driver_profiles dp ON dp.user_id = u.id WHERE u.id=$1
      `, [tripData.driver_id]);
      if (dr.rows.length > 0) {
        tripData.driver = dr.rows[0];
        tripData.driver_name = dr.rows[0].full_name;
        tripData.driver_rating = dr.rows[0].rating;
      }
    }

    res.json({ success: true, data: tripData });
  } catch (err) {
    console.error('getTripStatus:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

export const notifyArrival = async (req, res) => {
  const { tripId } = req.params;
  try {
    const tripRes = await query(
      `UPDATE trips SET status='arrived' WHERE id=$1 RETURNING id`,
      [tripId]
    );
    if (tripRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado.' });

    res.json({ success: true, message: 'Se notificó la llegada al cliente.' });
  } catch (err) {
    console.error('notifyArrival:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CALIFICACIÓN — cliente califica al conductor
// ──────────────────────────────────────────────────────────────────────────────
export const rateDriver = async (req, res) => {
  const { tripId } = req.params;
  const { rating, comment } = req.body;

  if (!rating || rating < 1 || rating > 5)
    return res.status(400).json({ success: false, message: 'La calificación debe ser entre 1 y 5.' });

  try {
    // Guardar rating en el viaje
    const tripRes = await query(
      `UPDATE trips SET client_rating=$1, client_comment=$2 WHERE id=$3 RETURNING driver_id`,
      [rating, comment || null, tripId]
    );
    if (tripRes.rows.length === 0)
      return res.status(404).json({ success: false, message: 'Viaje no encontrado.' });

    // Actualizar promedio del conductor
    const driverId = tripRes.rows[0].driver_id;
    if (driverId) {
      await query(
        `UPDATE driver_profiles
         SET rating = (
           SELECT ROUND(AVG(client_rating)::numeric, 2)
           FROM trips WHERE driver_id=$1 AND client_rating IS NOT NULL
         )
         WHERE user_id=$1`,
        [driverId]
      );
    }

    res.json({ success: true, message: '¡Gracias por tu calificación!' });
  } catch (err) {
    console.error('rateDriver:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};


// ──────────────────────────────────────────────────────────────────────────────
// CLIENTES — historial de viajes
// ──────────────────────────────────────────────────────────────────────────────
export const getClientTrips = async (req, res) => {
  const { clientId } = req.params;
  try {
    const result = await query(`
      SELECT t.id AS trip_id, t.origin_address, t.dest_address,
             t.distance_km, t.estimated_fare, t.payment_method, t.status,
             t.created_at, t.completed_at,
             u.full_name AS driver_name,
             dp.vehicle_model, dp.vehicle_plate
      FROM trips t
      LEFT JOIN users u ON t.driver_id = u.id
      LEFT JOIN driver_profiles dp ON t.driver_id = dp.user_id
      WHERE t.client_id = $1
      ORDER BY t.created_at DESC
    `, [clientId]);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getClientTrips:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CONDUCTORES — historial de viajes
// ──────────────────────────────────────────────────────────────────────────────
export const getDriverTrips = async (req, res) => {
  const { driverId } = req.params;
  try {
    const result = await query(`
      SELECT t.id AS trip_id, t.origin_address, t.dest_address,
             t.distance_km, t.estimated_fare, t.payment_method, t.status,
             t.created_at, t.completed_at,
             u.full_name AS client_name, u.phone AS client_phone
      FROM trips t
      LEFT JOIN users u ON t.client_id = u.id
      WHERE t.driver_id = $1
      ORDER BY t.created_at DESC
    `, [driverId]);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getDriverTrips:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// CHAT DE VIAJES — obtener y enviar mensajes
// ──────────────────────────────────────────────────────────────────────────────
export const getTripMessages = async (req, res) => {
  const { tripId } = req.params;
  try {
    const result = await query(`
      SELECT m.id, m.trip_id, m.sender_id, m.message, m.created_at,
             u.full_name AS sender_name, r.name AS sender_role
      FROM trip_messages m
      JOIN users u ON m.sender_id = u.id
      JOIN roles r ON u.role_id = r.id
      WHERE m.trip_id = $1
      ORDER BY m.created_at ASC
    `, [tripId]);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getTripMessages:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

export const addTripMessage = async (req, res) => {
  const { tripId } = req.params;
  const { sender_id, message } = req.body;
  if (!sender_id || !message) {
    return res.status(400).json({ success: false, message: 'sender_id y message son requeridos' });
  }
  try {
    const result = await query(`
      INSERT INTO trip_messages (trip_id, sender_id, message)
      VALUES ($1, $2, $3)
      RETURNING id, trip_id, sender_id, message, created_at
    `, [tripId, sender_id, message]);
    res.status(201).json({ success: true, data: result.rows[0] });
  } catch (err) {
    console.error('addTripMessage:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// SOPORTE — obtener mensajes de un usuario con admin
// ──────────────────────────────────────────────────────────────────────────────
export const getSupportMessages = async (req, res) => {
  const { userId } = req.params;
  try {
    await query(`UPDATE support_messages SET is_read = TRUE WHERE user_id = $1 AND sender_role != 'admin'`, [userId]);
    const result = await query(`
      SELECT sm.id, sm.user_id, sm.sender_role, sm.trip_id, sm.message, sm.type, sm.is_read, sm.created_at,
             u.full_name AS user_name
      FROM support_messages sm
      JOIN users u ON sm.user_id = u.id
      WHERE sm.user_id = $1
      ORDER BY sm.created_at ASC
    `, [userId]);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getSupportMessages:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// SOPORTE — enviar mensaje
// ──────────────────────────────────────────────────────────────────────────────
export const sendSupportMessage = async (req, res) => {
  const { userId } = req.params;
  const { message, sender_role = 'client', trip_id = null, type = 'support' } = req.body;
  if (!message) return res.status(400).json({ success: false, message: 'message es requerido' });
  try {
    const result = await query(`
      INSERT INTO support_messages (user_id, sender_role, trip_id, message, type)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING *
    `, [userId, sender_role, trip_id, message, type]);
    res.status(201).json({ success: true, data: result.rows[0] });
  } catch (err) {
    console.error('sendSupportMessage:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

// ──────────────────────────────────────────────────────────────────────────────
// SOPORTE ADMIN — listar todos los tickets (conversaciones únicas por usuario)
// ──────────────────────────────────────────────────────────────────────────────
export const getSupportTickets = async (_req, res) => {
  try {
    const result = await query(`
      SELECT DISTINCT ON (sm.user_id)
             sm.user_id, u.full_name, u.phone, sm.message AS last_message,
             sm.type, sm.created_at,
             COUNT(*) FILTER (WHERE sm.is_read = FALSE AND sm.sender_role != 'admin')
               OVER (PARTITION BY sm.user_id) AS unread_count
      FROM support_messages sm
      JOIN users u ON sm.user_id = u.id
      ORDER BY sm.user_id, sm.created_at DESC
    `);
    res.json({ success: true, data: result.rows });
  } catch (err) {
    console.error('getSupportTickets:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

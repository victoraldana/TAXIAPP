import express from 'express';
import {
  getDashboard, listDrivers, registerDriver, updateDriver, getDriverLocation,
  getQueue, addToQueue, removeFromQueue, moveInQueue,
  listTrips, assignNextDriver, createTrip,
} from '../controllers/adminController.js';

const router = express.Router();

// Dashboard
router.get('/dashboard', getDashboard);

// Conductores
router.get('/drivers',               listDrivers);
router.post('/drivers',              registerDriver);
router.patch('/drivers/:id',         updateDriver);
router.get('/drivers/:id/location',  getDriverLocation);

// Cola de turnos
router.get('/queue',                       getQueue);
router.post('/queue/add/:driver_id',       addToQueue);
router.delete('/queue/remove/:driver_id',  removeFromQueue);
router.patch('/queue/:queue_id/move',      moveInQueue);

// Viajes
router.get('/trips',                       listTrips);
router.post('/trips',                      createTrip);
router.post('/trips/:trip_id/assign',      assignNextDriver);

export default router;

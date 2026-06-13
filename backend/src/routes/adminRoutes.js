import express from 'express';
import {
  getDashboard, listDrivers, registerDriver, updateDriver, getDriverLocation,
  getDriverQueueStatus, updateDriverLocation, getPendingTrip,
  getQueue, addToQueue, removeFromQueue, moveInQueue,
  listTrips, assignNextDriver, createTrip, rejectTrip, finishTrip,
  getTripStatus, rateDriver, notifyArrival, acceptTrip
} from '../controllers/adminController.js';

const router = express.Router();

// Dashboard
router.get('/dashboard', getDashboard);

// Conductores
router.get('/drivers',                        listDrivers);
router.post('/drivers',                       registerDriver);
router.patch('/drivers/:id',                  updateDriver);
router.get('/drivers/:id/location',           getDriverLocation);
router.patch('/drivers/:id/location',         updateDriverLocation);   // app conductor
router.get('/drivers/:id/queue-status',       getDriverQueueStatus);   // app conductor
router.get('/drivers/:id/pending-trip',       getPendingTrip);         // app conductor

// Cola de turnos
router.get('/queue',                          getQueue);
router.post('/queue/:driver_id',              addToQueue);             // app conductor
router.delete('/queue/:driver_id',            removeFromQueue);        // app conductor
router.post('/queue/add/:driver_id',          addToQueue);             // admin panel
router.delete('/queue/remove/:driver_id',     removeFromQueue);        // admin panel
router.patch('/queue/:queue_id/move',         moveInQueue);

// Viajes
router.get('/trips',                          listTrips);
router.post('/trips',                         createTrip);
router.post('/trips/:trip_id/assign',         assignNextDriver);
router.patch('/trips/:tripId/reject',         rejectTrip);             // conductor rechaza
router.patch('/trips/:tripId/finish',         finishTrip);             // conductor finaliza
router.patch('/trips/:tripId/arrive',         notifyArrival);          // conductor avisa que llegó
router.get('/trips/:tripId/status',           getTripStatus);          // cliente polling
router.post('/trips/:tripId/rate',            rateDriver);             // cliente califica
router.patch('/trips/:tripId/accept',         acceptTrip);             // conductor acepta

export default router;


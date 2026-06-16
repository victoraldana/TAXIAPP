import express from 'express';
import {
  getDashboard, listDrivers, registerDriver, updateDriver, getDriverLocation,
  getDriverQueueStatus, updateDriverLocation, getPendingTrip, getActiveTripForClient,
  getQueue, addToQueue, removeFromQueue, moveInQueue,
  listTrips, assignNextDriver, createTrip, rejectTrip, finishTrip, cancelTrip,
  getTripStatus, rateDriver, notifyArrival, acceptTrip,
  getClientTrips, getDriverTrips, getTripMessages, addTripMessage,
  getSupportMessages, sendSupportMessage, getSupportTickets
} from '../controllers/adminController.js';

const router = express.Router();

// Dashboard
router.get('/dashboard', getDashboard);

// Historiales & Active trips
router.get('/trips/client/:clientId/active', getActiveTripForClient);
router.get('/trips/client/:clientId', getClientTrips);
router.get('/trips/driver/:driverId', getDriverTrips);

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
router.patch('/trips/:tripId/cancel',         cancelTrip);             // admin cancela
router.get('/trips/:tripId/status',           getTripStatus);          // cliente polling
router.post('/trips/:tripId/rate',            rateDriver);             // cliente califica
router.patch('/trips/:tripId/accept',         acceptTrip);             // conductor acepta

// Chat de viajes
router.get('/trips/:tripId/messages',         getTripMessages);
router.post('/trips/:tripId/messages',        addTripMessage);

// Chat de soporte (SOS / cancelación / soporte general)
router.get('/support/tickets',                getSupportTickets);         // admin: todos los tickets
router.get('/support/:userId/messages',       getSupportMessages);        // user/admin: historial
router.post('/support/:userId/messages',      sendSupportMessage);        // user/admin: enviar msg

export default router;

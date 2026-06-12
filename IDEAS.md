# 🚀 TaxiApp - Backlog e Ideas de Implementación

Este documento sirve como un repositorio central para anotar ideas, mejoras, características futuras y optimizaciones que podemos implementar en TaxiApp.

## 📱 App Cliente (Android)
- [ ] **Animaciones más fluidas**: Añadir transiciones entre pantallas, como cuando el teclado aparece/desaparece o cuando el bottom sheet cambia de tamaño.
- [ ] **Guardar lugares favoritos**: Permitir a los usuarios guardar "Casa" y "Trabajo" para pedir viajes en 1 clic.
- [ ] **Historial de viajes**: Pantalla para ver viajes pasados, cuánto costaron y quién fue el conductor.
- [ ] **Calificación y Reseñas**: Sistema para dejar estrellas (1-5) y comentarios al conductor al finalizar el viaje.
- [ ] **Chat / Llamadas In-App**: En lugar de mostrar el número telefónico, abrir un chat integrado usando Firebase o WebSockets.
- [ ] **Múltiples métodos de pago**: Integrar Stripe, PayPal, o pasarelas de pago locales en lugar de solo "efectivo".
- [ ] **Soporte para Múltiples Servicios**: Además de taxi, incorporar botones funcionales para "Comida" y "Envíos" en la Home.

## 🚕 App Conductor
- [ ] **Pantalla de Viaje Activo**: Navegación giro a giro (turn-by-turn) nativa o abrir Google Maps/Waze automáticamente al aceptar un viaje.
- [ ] **Ganancias Diarias**: Un dashboard para el conductor donde pueda ver cuánto ha ganado hoy, esta semana y este mes.
- [ ] **Botón SOS**: Un botón de emergencia visible durante el viaje en caso de peligro.
- [ ] **Finalizar Viaje**: Opción en la app para marcar el viaje como completado (y automáticamente volver a entrar a la `driver_queue` si está disponible).

## 💻 Backend y Panel de Administración (Web)
- [ ] **Autenticación con SMS Real**: Cambiar el `DEV_MODE` por Twilio o Firebase OTP para producción.
- [ ] **WebSockets (Socket.io)**: Migrar el sistema de asignación y tracking (coordenadas) a WebSockets para evitar el polling manual y reducir la carga en el servidor.
- [ ] **Almacenamiento en la Nube (AWS S3 / Cloudinary)**: Subir las fotos de perfil, cédulas y documentos del KYC a la nube en lugar de solo guardar URLs o Base64.
- [ ] **Heatmaps (Mapas de Calor)**: Ver en el admin panel las zonas con más demanda de taxis para mandar conductores hacia allá.
- [ ] **Despacho Manual**: Permitir al administrador saltarse la cola FIFO y asignar un viaje a un conductor específico por motivos de emergencia o cercanía extrema.
- [ ] **Manejo de Estados de Viaje**: Crear un flujo completo (`buscando` -> `asignado` -> `en_camino` -> `en_viaje` -> `completado` / `cancelado`).

## 🎨 Diseño / UI / UX
- [ ] **Modo Claro/Oscuro Automático**: Que la app detecte el tema del sistema operativo.
- [ ] **Micro-animaciones**: Botones con efectos "ripple" dinámicos, splash screen animada.
- [ ] **Banner de Publicidad Funcional**: Los banners de la Home deben conectarse al backend para mostrar promociones reales o anuncios pagados.

---
*Agrega aquí cualquier otra idea que se te ocurra en el futuro.*

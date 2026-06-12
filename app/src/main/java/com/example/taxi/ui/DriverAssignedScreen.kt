package com.example.taxi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.*

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val AssignDark   = Color(0xFF0F1923)
private val AssignCard   = Color(0xFF1A2535)
private val AssignBorder = Color(0xFF253348)
private val AssignYellow = Color(0xFFFFC107)
private val AssignText   = Color(0xFFF0F6FF)
private val AssignSub    = Color(0xFF7A90B0)
private val AssignGreen  = Color(0xFF4CAF50)

data class AssignedDriverInfo(
    val driverId: String,
    val fullName: String,
    val phone: String,
    val avatarUrl: String?,
    val unitNumber: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleYear: Int?,
    val vehiclePlate: String,
    val vehicleColor: String,
    val vehicleType: String,
    val rating: Float,
    val totalTrips: Int
)

@Composable
fun DriverAssignedScreen(
    driver: AssignedDriverInfo,
    originAddress: String,
    destAddress: String,
    onCancel: () -> Unit,
    onContact: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        0.95f, 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val rotation by infiniteTransition.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "bg_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AssignDark)
            .drawBehind { drawAssignBg(rotation) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("¡Tu taxi está en camino!", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = AssignText, fontSize = 20.sp)
                    Text("Conductor asignado exitosamente", style = MaterialTheme.typography.bodySmall,
                        color = AssignSub)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AssignGreen.copy(alpha = 0.15f))
                        .border(1.dp, AssignGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("EN CAMINO", color = AssignGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── CARD VEHÍCULO 3D ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A2A3A), Color(0xFF0D1825)),
                                Angle = 135f
                            )
                        )
                        .border(1.dp, AssignYellow.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Número de unidad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("UNIDAD", fontSize = 10.sp, color = AssignSub,
                                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Text(
                                    text = "N° ${driver.unitNumber}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AssignYellow
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PLACA", fontSize = 10.sp, color = AssignSub,
                                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Text(
                                    text = driver.vehiclePlate,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AssignText,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 3D Vehicle Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Vehicle3DView(
                                vehicleType = driver.vehicleType,
                                color = driver.vehicleColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Info vehículo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${driver.vehicleMake} ${driver.vehicleModel}" +
                                        (driver.vehicleYear?.let { " ($it)" } ?: ""),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AssignText
                            )
                        }
                        Text(
                            text = driver.vehicleColor.replaceFirstChar { it.uppercaseChar() },
                            fontSize = 12.sp,
                            color = AssignSub
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── CARD CONDUCTOR ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AssignCard),
                border = BorderStroke(1.dp, AssignBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar con pulso
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((68 * pulse).dp)
                                .clip(CircleShape)
                                .background(AssignYellow.copy(alpha = 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(listOf(AssignYellow, Color(0xFFE6A800)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = driver.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0D0D0D)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(driver.fullName, fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp, color = AssignText)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Star, null, tint = AssignYellow, modifier = Modifier.size(14.dp))
                            Text("${driver.rating}", fontSize = 13.sp, color = AssignText, fontWeight = FontWeight.SemiBold)
                            Text("·", color = AssignSub)
                            Text("${driver.totalTrips} viajes", fontSize = 13.sp, color = AssignSub)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(driver.phone, fontSize = 13.sp, color = AssignSub)
                    }

                    // Botón llamar
                    FloatingActionButton(
                        onClick = onContact,
                        modifier = Modifier.size(48.dp),
                        containerColor = AssignGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Ruta resumida ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AssignCard),
                border = BorderStroke(1.dp, AssignBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteRow(Icons.Filled.RadioButtonChecked, Color(0xFF4CAF50), "Origen", originAddress)
                    Box(modifier = Modifier.padding(start = 11.dp).width(2.dp).height(16.dp).background(AssignBorder))
                    RouteRow(Icons.Filled.LocationOn, Color(0xFFEF5350), "Destino", destAddress)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Botón cancelar ────────────────────────────────────────────────
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AssignBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AssignSub)
            ) {
                Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cancelar viaje", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RouteRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, address: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(label, fontSize = 10.sp, color = AssignSub, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(address, fontSize = 13.sp, color = AssignText, maxLines = 2)
        }
    }
}

// ─── Vista 3D del Vehículo (Canvas) ──────────────────────────────────────────
@Composable
fun Vehicle3DView(vehicleType: String, color: String, modifier: Modifier = Modifier) {
    val bodyColor = parseVehicleColor(color)
    val darkBody  = bodyColor.copy(red = bodyColor.red * 0.7f, green = bodyColor.green * 0.7f, blue = bodyColor.blue * 0.7f)
    val shineColor = Color.White.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        when (vehicleType.lowercase()) {
            "suv", "van" -> drawSUV(cx, h, bodyColor, darkBody, shineColor)
            else         -> drawSedan(cx, h, bodyColor, darkBody, shineColor)
        }
    }
}

private fun DrawScope.drawSedan(cx: Float, h: Float, body: Color, dark: Color, shine: Color) {
    val bw = size.width * 0.75f
    val bh = h * 0.28f
    val by = h * 0.52f
    val topW = bw * 0.55f
    val topH = h * 0.22f
    val topY = by - topH

    // Sombra suelo
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - bw*0.4f, by + bh*0.6f),
        androidx.compose.ui.geometry.Size(bw*0.8f, h*0.06f))

    // Carrocería inferior (perspectiva 3D)
    val path = Path().apply {
        moveTo(cx - bw/2f, by + bh * 0.15f)
        lineTo(cx + bw/2f, by + bh * 0.15f)
        lineTo(cx + bw/2f + bw*0.06f, by + bh)
        lineTo(cx - bw/2f - bw*0.06f, by + bh)
        close()
    }
    drawPath(path, dark)
    val bodyPath = Path().apply {
        moveTo(cx - bw/2f, by)
        lineTo(cx + bw/2f, by)
        lineTo(cx + bw/2f, by + bh * 0.15f)
        lineTo(cx - bw/2f, by + bh * 0.15f)
        close()
    }
    drawPath(bodyPath, body)

    // Cabina
    val cabinPath = Path().apply {
        moveTo(cx - topW/2f, by)
        lineTo(cx - topW*0.65f, topY)
        lineTo(cx + topW*0.65f, topY)
        lineTo(cx + topW/2f, by)
        close()
    }
    drawPath(cabinPath, body)
    drawPath(cabinPath.apply { }, Brush.verticalGradient(listOf(shine, Color.Transparent)),
        alpha = 0.4f)

    // Ventanas
    val wMargin = topW * 0.12f
    val wTop = topY + topH * 0.15f
    val wBot = by - h * 0.02f
    drawRect(Color(0xFF1A3A5C).copy(alpha = 0.8f),
        Offset(cx - topW/2f + wMargin, wTop),
        androidx.compose.ui.geometry.Size(topW*0.4f, wBot - wTop))
    drawRect(Color(0xFF1A3A5C).copy(alpha = 0.8f),
        Offset(cx + topW*0.05f, wTop),
        androidx.compose.ui.geometry.Size(topW*0.4f, wBot - wTop))

    // Ruedas
    val wheelR = h * 0.11f
    val wheelY = by + bh * 0.9f
    listOf(cx - bw*0.3f, cx + bw*0.3f).forEach { wx ->
        drawCircle(Color(0xFF1A1A1A), wheelR + 4.dp.toPx(), Offset(wx, wheelY))
        drawCircle(Color(0xFF2A2A2A), wheelR, Offset(wx, wheelY))
        drawCircle(Color(0xFF9E9E9E), wheelR * 0.5f, Offset(wx, wheelY))
    }

    // Faros
    drawCircle(Color(0xFFFFF176).copy(alpha = 0.9f), h * 0.04f, Offset(cx - bw/2f + h*0.04f, by + bh*0.08f))
    drawCircle(Color(0xFFEF9A9A).copy(alpha = 0.9f), h * 0.035f, Offset(cx + bw/2f - h*0.04f, by + bh*0.08f))
}

private fun DrawScope.drawSUV(cx: Float, h: Float, body: Color, dark: Color, shine: Color) {
    val bw = size.width * 0.8f
    val bh = h * 0.32f
    val by = h * 0.48f
    val topW = bw * 0.85f
    val topH = h * 0.28f
    val topY = by - topH

    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - bw*0.42f, by + bh*0.65f),
        androidx.compose.ui.geometry.Size(bw*0.84f, h*0.06f))
    val bodyPath = Path().apply {
        moveTo(cx - bw/2f, by); lineTo(cx + bw/2f, by)
        lineTo(cx + bw/2f + bw*0.05f, by + bh); lineTo(cx - bw/2f - bw*0.05f, by + bh); close()
    }
    drawPath(bodyPath, body)
    val topPath = Path().apply {
        moveTo(cx - topW/2f, by); lineTo(cx + topW/2f, by)
        lineTo(cx + topW/2f, topY); lineTo(cx - topW/2f, topY); close()
    }
    drawPath(topPath, body)
    drawRect(shine, Offset(cx - topW/2f, topY), androidx.compose.ui.geometry.Size(topW, topH * 0.3f))
    val wH = topH * 0.6f
    val wY = topY + topH * 0.2f
    listOf(cx - topW*0.35f, cx + topW*0.05f).forEach { wx ->
        drawRect(Color(0xFF1A3A5C).copy(alpha = 0.85f), Offset(wx, wY),
            androidx.compose.ui.geometry.Size(topW*0.28f, wH))
    }
    val wheelR = h * 0.13f
    val wheelY = by + bh * 0.88f
    listOf(cx - bw*0.33f, cx + bw*0.33f).forEach { wx ->
        drawCircle(Color(0xFF1A1A1A), wheelR + 4.dp.toPx(), Offset(wx, wheelY))
        drawCircle(Color(0xFF2A2A2A), wheelR, Offset(wx, wheelY))
        drawCircle(Color(0xFF9E9E9E), wheelR * 0.5f, Offset(wx, wheelY))
    }
}

private fun Brush.Companion.verticalGradient(colors: List<Color>) =
    verticalGradient(colors = colors)

private fun Path.apply(block: Path.() -> Unit = {}): Path { block(); return this }

private fun DrawScope.drawPath(path: Path, brush: Brush, alpha: Float = 1f) {
    drawPath(path = path, brush = brush, alpha = alpha)
}

private fun parseVehicleColor(color: String): Color = when (color.lowercase().trim()) {
    "blanco", "white"           -> Color(0xFFF5F5F5)
    "negro", "black"            -> Color(0xFF263238)
    "rojo", "red"               -> Color(0xFFE53935)
    "azul", "blue"              -> Color(0xFF1E88E5)
    "gris", "gray", "grey"      -> Color(0xFF78909C)
    "plata", "silver"           -> Color(0xFFB0BEC5)
    "verde", "green"            -> Color(0xFF43A047)
    "amarillo", "yellow"        -> Color(0xFFFDD835)
    "naranja", "orange"         -> Color(0xFFE65100)
    "marron", "brown"           -> Color(0xFF6D4C41)
    "beige"                     -> Color(0xFFD7CCC8)
    else                        -> Color(0xFF78909C)
}

private fun DrawScope.drawAssignBg(angleDeg: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    drawCircle(Color(0xFFFFC107).copy(alpha = 0.05f), size.width * 0.6f,
        Offset(size.width * 0.9f + (size.width*0.05f*cos(a)).toFloat(), size.height*0.1f))
    drawCircle(Color(0xFF4FC3F7).copy(alpha = 0.04f), size.width * 0.4f, Offset(0f, size.height*0.7f))
}

// Canvas helper
@Composable
private fun Canvas(modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}

private fun Brush.Companion.linearGradient(colors: List<Color>, Angle: Float): Brush =
    linearGradient(colors = colors,
        start = Offset(0f, 0f),
        end = Offset(cos(Math.toRadians(Angle.toDouble())).toFloat() * 1000f,
                     sin(Math.toRadians(Angle.toDouble())).toFloat() * 1000f))

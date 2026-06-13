package com.example.taxi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.cos
import kotlin.math.sin

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val RateDark   = Color(0xFF0A1628)
private val RateCard   = Color(0xFF132033)
private val RateBorder = Color(0xFF1E3050)
private val RateYellow = Color(0xFFFFC107)
private val RateText   = Color(0xFFF0F6FF)
private val RateSub    = Color(0xFF7A90B0)
private val RateGreen  = Color(0xFF00C853)

@Composable
fun RatingScreen(
    driverName: String?,
    tripId: String,
    onRatingSubmitted: (rating: Int, comment: String) -> Unit,
    onSkip: () -> Unit
) {
    var selectedRating by remember { mutableStateOf(0) }
    var comment        by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val angle by infiniteTransition.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)),
        label = "bg_rot"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RateDark)
            .drawBehind {
                val a = Math.toRadians(angle.toDouble())
                drawCircle(RateYellow.copy(alpha = 0.05f), size.width * 0.55f,
                    androidx.compose.ui.geometry.Offset(
                        (size.width * 0.9f + size.width * 0.05f * cos(a)).toFloat(),
                        size.height * 0.1f
                    )
                )
                drawCircle(Color(0xFF4FC3F7).copy(alpha = 0.04f), size.width * 0.38f,
                    androidx.compose.ui.geometry.Offset(0f, size.height * 0.75f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Ícono viaje completado ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(RateGreen.copy(0.3f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(RateGreen.copy(0.18f))
                        .border(1.dp, RateGreen.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", fontSize = 36.sp, color = RateGreen, fontWeight = FontWeight.ExtraBold)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "¡Llegaste a tu destino!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = RateText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "¿Cómo fue tu experiencia con ${driverName ?: "tu conductor"}?",
                    fontSize = 14.sp,
                    color = RateSub,
                    textAlign = TextAlign.Center
                )
            }

            // ── Estrellas ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RateCard)
                    .border(1.dp, RateBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when (selectedRating) {
                        0    -> "Toca para calificar"
                        1    -> "😤 Muy malo"
                        2    -> "😕 Malo"
                        3    -> "😐 Regular"
                        4    -> "😊 Bueno"
                        else -> "🌟 ¡Excelente!"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedRating == 0) RateSub else RateText
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 1..5) {
                        val isSelected = i <= selectedRating
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.2f else 1f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                            label = "star_scale_$i"
                        )
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) RateYellow else RateSub.copy(0.4f),
                            animationSpec = tween(200),
                            label = "star_tint_$i"
                        )
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "$i estrella",
                            tint = tint,
                            modifier = Modifier
                                .size(48.dp)
                                .scale(scale)
                                .clickable { selectedRating = i }
                        )
                    }
                }
            }

            // ── Comentario opcional ───────────────────────────────────────────
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Comentario opcional...", color = RateSub) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = RateBorder,
                    focusedBorderColor   = RateYellow.copy(0.6f),
                    unfocusedTextColor   = RateText,
                    focusedTextColor     = RateText,
                    cursorColor          = RateYellow,
                    unfocusedContainerColor = RateCard,
                    focusedContainerColor   = RateCard
                )
            )

            // ── Botón enviar ──────────────────────────────────────────────────
            Button(
                onClick = { if (selectedRating > 0) onRatingSubmitted(selectedRating, comment) },
                enabled = selectedRating > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor    = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            if (selectedRating > 0)
                                Brush.horizontalGradient(listOf(RateYellow, Color(0xFFE6A800)))
                            else
                                Brush.horizontalGradient(listOf(RateBorder, RateBorder)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Enviar calificación",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selectedRating > 0) Color(0xFF1A0A00) else RateSub,
                        fontSize = 16.sp
                    )
                }
            }

            TextButton(onClick = onSkip) {
                Text("Omitir por ahora", color = RateSub, fontSize = 13.sp)
            }
        }
    }
}

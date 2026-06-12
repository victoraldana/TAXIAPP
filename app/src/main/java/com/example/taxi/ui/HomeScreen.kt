package com.example.taxi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val HomeYellow      = Color(0xFFFFC107)
private val HomeYellowDark  = Color(0xFFE6A800)
private val HomeYellowSoft  = Color(0xFFFFD54F)
private val HomeDark        = Color(0xFF0F1923)
private val HomeMid         = Color(0xFF1A2535)
private val HomeCard        = Color(0xFF1E2D40)
private val HomeCardLight   = Color(0xFF253348)
private val HomeText        = Color(0xFFF0F6FF)
private val HomeSubText     = Color(0xFF7A90B0)
private val HomeAccentBlue  = Color(0xFF4FC3F7)
private val HomeAccentGreen = Color(0xFF66BB6A)
private val HomeAccentOrange= Color(0xFFFF7043)

// ─── Datos de servicios ───────────────────────────────────────────────────────
data class ServiceItem(
    val id: String,
    val label: String,
    val sublabel: String,
    val icon: ImageVector,
    val color: Color,
    val gradientEnd: Color,
    val available: Boolean = true
)

private val services = listOf(
    ServiceItem("trip",     "Viaje",   "Lleva a donde quieras",  Icons.Filled.DirectionsCar,  HomeYellow,       HomeYellowDark,   true),
    ServiceItem("food",     "Comida",  "Pide a domicilio",        Icons.Filled.Fastfood,       HomeAccentOrange, Color(0xFFBF360C), false),
    ServiceItem("delivery", "Envíos",  "Envía tus paquetes",      Icons.Filled.LocalShipping,  HomeAccentBlue,   Color(0xFF0277BD), false),
)

// ─── Datos de banners ─────────────────────────────────────────────────────────
data class BannerItem(
    val title: String,
    val subtitle: String,
    val badge: String?,
    val gradientStart: Color,
    val gradientEnd: Color,
    val icon: ImageVector
)

private val banners = listOf(
    BannerItem("¡Tu primer viaje gratis!",   "Usa el código TAXI2025",      "PROMO",  Color(0xFFFF6F00), Color(0xFFE65100), Icons.Filled.LocalOffer),
    BannerItem("Viaja con seguridad",         "Conductores verificados 24/7", null,     Color(0xFF1565C0), Color(0xFF0D47A1), Icons.Filled.VerifiedUser),
    BannerItem("Gana conduciendo",            "Únete como conductor hoy",     "NUEVO",  Color(0xFF2E7D32), Color(0xFF1B5E20), Icons.Filled.DirectionsCar),
    BannerItem("Paga con tarjeta o efectivo", "Múltiples métodos de pago",    null,     Color(0xFF6A1B9A), Color(0xFF4A148C), Icons.Filled.CreditCard),
)

// ─── HomeScreen ───────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    userName: String = "Usuario",
    onServiceSelected: (serviceId: String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { banners.size })

    // Auto-scroll banners cada 3.5 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeDark)
    ) {
        HomeBgDecor()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            HomeTopBar(userName = userName)

            Spacer(Modifier.height(8.dp))

            // ── Búsqueda rápida ───────────────────────────────────────────────
            QuickSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                onClick = { onServiceSelected("trip") }
            )

            Spacer(Modifier.height(28.dp))

            // ── Título servicios ──────────────────────────────────────────────
            Text(
                text = "¿Qué necesitas hoy?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HomeText,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(14.dp))

            // ── Servicios ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                services.forEach { service ->
                    ServiceCard(
                        service = service,
                        modifier = Modifier.weight(1f),
                        onClick = { if (service.available) onServiceSelected(service.id) }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Título banners ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Promociones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HomeText
                )
                Text(
                    text = "Ver todas",
                    style = MaterialTheme.typography.labelMedium,
                    color = HomeYellow,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Banners con pager ─────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) { page ->
                BannerCard(banner = banners[page])
            }

            Spacer(Modifier.height(12.dp))

            // ── Indicadores del pager ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(banners.size) { idx ->
                    val isSelected = pagerState.currentPage == idx
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "dot_w"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) HomeYellow else HomeCardLight
                            )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Sección: Accesos rápidos ──────────────────────────────────────
            Text(
                text = "Accesos rápidos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HomeText,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAccessChip(
                    icon = Icons.Outlined.Home,
                    label = "Casa",
                    modifier = Modifier.weight(1f),
                    onClick = { onServiceSelected("trip") }
                )
                QuickAccessChip(
                    icon = Icons.Outlined.Work,
                    label = "Trabajo",
                    modifier = Modifier.weight(1f),
                    onClick = { onServiceSelected("trip") }
                )
                QuickAccessChip(
                    icon = Icons.Outlined.StarOutline,
                    label = "Favoritos",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────
@Composable
private fun HomeTopBar(userName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hola, ${userName.split(" ").firstOrNull() ?: "Usuario"} 👋",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = HomeText,
                fontSize = 22.sp
            )
            Text(
                text = "¿A dónde vamos hoy?",
                style = MaterialTheme.typography.bodySmall,
                color = HomeSubText
            )
        }

        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(HomeYellow, HomeYellowDark))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0D0D0D),
                fontSize = 18.sp
            )
        }
    }
}

// ─── Barra de búsqueda ────────────────────────────────────────────────────────
@Composable
private fun QuickSearchBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = HomeYellow,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "¿A dónde quieres ir?",
                color = HomeSubText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HomeYellow.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Buscar",
                    color = HomeYellow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Tarjeta de servicio ──────────────────────────────────────────────────────
@Composable
private fun ServiceCard(
    service: ServiceItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.85f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(
            1.dp,
            if (service.available) service.color.copy(alpha = 0.3f) else HomeCardLight
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradiente de fondo sutil
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                service.color.copy(alpha = if (service.available) 0.12f else 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icono
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    service.color.copy(alpha = if (service.available) 0.3f else 0.1f),
                                    service.gradientEnd.copy(alpha = if (service.available) 0.2f else 0.06f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = service.icon,
                        contentDescription = service.label,
                        tint = if (service.available) service.color else HomeSubText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = service.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (service.available) HomeText else HomeSubText,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )

                Text(
                    text = service.sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (service.available) HomeSubText else HomeSubText.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )

                if (!service.available) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HomeCardLight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Próximamente",
                            color = HomeSubText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Banner de publicidad ─────────────────────────────────────────────────────
@Composable
private fun BannerCard(banner: BannerItem) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(banner.gradientStart, banner.gradientEnd),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Círculo decorativo fondo
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (banner.badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = banner.badge,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = banner.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = banner.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ver más →",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    imageVector = banner.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

// ─── Acceso rápido ────────────────────────────────────────────────────────────
@Composable
private fun QuickAccessChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
        border = BorderStroke(1.dp, HomeCardLight)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = HomeYellow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeSubText,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Fondo decorativo ─────────────────────────────────────────────────────────
@Composable
private fun HomeBgDecor() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawHomeBg(angle) })
}

private fun DrawScope.drawHomeBg(angleDeg: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    drawCircle(
        color = Color(0xFFFFC107).copy(alpha = 0.04f),
        radius = size.width * 0.7f,
        center = Offset(
            size.width * 0.1f + (size.width * 0.04f * cos(a)).toFloat(),
            size.height * 0.05f + (size.height * 0.03f * sin(a)).toFloat()
        )
    )
    drawCircle(
        color = Color(0xFF4FC3F7).copy(alpha = 0.04f),
        radius = size.width * 0.5f,
        center = Offset(size.width * 0.95f, size.height * 0.3f)
    )
}

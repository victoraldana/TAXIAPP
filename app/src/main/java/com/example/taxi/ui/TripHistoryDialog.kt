package com.example.taxi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.taxi.model.TripHistoryItem
import com.example.taxi.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripHistoryDialog(
    userId: String,
    isDriver: Boolean,
    onDismiss: () -> Unit
) {
    var history by remember { mutableStateOf<List<TripHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        scope.launch {
            try {
                val res = if (isDriver) {
                    RetrofitClient.apiService.getDriverTrips(userId)
                } else {
                    RetrofitClient.apiService.getClientTrips(userId)
                }
                if (res.isSuccessful) {
                    history = res.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFFF0F6FF)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = Color(0xFF0A1628))
                        Text(
                            "Historial de Viajes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF0A1628)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Divider(color = Color(0xFFDDE5ED))

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFC107))
                    }
                } else if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tienes viajes registrados.", color = Color(0xFF7A90B0))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(history) { trip ->
                            TripHistoryCard(trip, isDriver)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripHistoryCard(trip: TripHistoryItem, isDriver: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(trip.createdAt),
                    fontSize = 12.sp,
                    color = Color(0xFF7A90B0),
                    fontWeight = FontWeight.Bold
                )
                TripStatusBadge(trip.status)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDriver) "Cliente: ${trip.clientName ?: "—"}" else "Conductor: ${trip.driverName ?: "—"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0A1628)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("De: ${trip.originAddress}", fontSize = 12.sp, color = Color(0xFF333333))
                    Text("A: ${trip.destAddress}", fontSize = 12.sp, color = Color(0xFF333333))
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Bs ${trip.estimatedFare}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF00C853)
                    )
                    Text(
                        text = "${trip.distanceKm} km",
                        fontSize = 12.sp,
                        color = Color(0xFF7A90B0)
                    )
                }
            }
        }
    }
}

@Composable
fun TripStatusBadge(status: String) {
    val (color, text) = when (status) {
        "pending" -> Color(0xFFFFC107) to "Pendiente"
        "accepted", "arrived", "on_route" -> Color(0xFF4FC3F7) to "En proceso"
        "in_progress" -> Color(0xFF00C853) to "En curso"
        "completed" -> Color(0xFF7A90B0) to "Completado"
        "cancelled_no_drivers", "cancelled" -> Color(0xFFFF5252) to "Cancelado"
        else -> Color.Gray to status
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatDate(isoString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(isoString)
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("es", "VE"))
        formatter.timeZone = TimeZone.getDefault()
        date?.let { formatter.format(it) } ?: isoString
    } catch (e: Exception) {
        isoString
    }
}

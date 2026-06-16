package com.example.taxi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.taxi.model.SupportMessageItem
import com.example.taxi.model.SupportMessageRequest
import com.example.taxi.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChatDark = Color(0xFF132033)
private val ChatBubbleMe = Color(0xFF4FC3F7)
private val ChatBubbleOther = Color(0xFF253348)
private val ChatTextMe = Color(0xFF0F1923)
private val ChatTextOther = Color(0xFFF0F6FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatDialog(
    userId: String,
    tripId: String? = null,
    onDismiss: () -> Unit
) {
    var messages by remember { mutableStateOf<List<SupportMessageItem>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Polling messages
    LaunchedEffect(userId) {
        var lastCount = 0
        while (true) {
            try {
                val response = RetrofitClient.apiService.getSupportMessages(userId)
                if (response.isSuccessful) {
                    val newMessages = response.body()?.data ?: emptyList()
                    if (newMessages.size > lastCount && lastCount > 0) {
                        // Reproducir sonido si hay mensajes nuevos y no es la primera carga
                        val lastMsg = newMessages.last()
                        if (lastMsg.senderRole == "admin") {
                            com.example.taxi.utils.SoundUtils.playNotificationSound(context)
                        }
                    }
                    lastCount = newMessages.size
                    messages = newMessages
                }
            } catch (e: Exception) {
                // Ignore error for polling
            }
            delay(3000)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 40.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = ChatDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Chat de Soporte", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.DarkGray)

                // Messages List — tiene su propio scroll, NO se desplaza con el teclado
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        val isMe = msg.senderRole != "admin"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isMe) 16.dp else 0.dp,
                                                bottomEnd = if (isMe) 0.dp else 16.dp
                                            )
                                        )
                                        .background(if (isMe) ChatBubbleMe else ChatBubbleOther)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = msg.message,
                                        color = if (isMe) ChatTextMe else ChatTextOther,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = if (isMe) "Yo" else "Soporte (Admin)",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Input Box — imePadding() SOLO aquí para que suba con el teclado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1923))
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ChatBubbleMe,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val txt = inputText
                                inputText = ""
                                scope.launch {
                                    try {
                                        val req = SupportMessageRequest(
                                            message = txt,
                                            senderRole = "client",
                                            tripId = tripId,
                                            type = "support"
                                        )
                                        RetrofitClient.apiService.sendSupportMessage(userId, req)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(ChatBubbleMe)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Enviar", tint = ChatTextMe)
                    }
                }
            }
        }
    }
}

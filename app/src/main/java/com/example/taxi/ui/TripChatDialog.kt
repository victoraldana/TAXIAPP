package com.example.taxi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.taxi.model.ChatMessageItem
import com.example.taxi.model.ChatMessageRequest
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
fun TripChatDialog(
    tripId: String,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    var messages by remember { mutableStateOf<List<ChatMessageItem>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Polling messages
    LaunchedEffect(tripId) {
        while (true) {
            try {
                val response = RetrofitClient.apiService.getTripMessages(tripId)
                if (response.isSuccessful) {
                    messages = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore error for polling
            }
            delay(3000)
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
                    Text("Chat del Viaje", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.DarkGray)

                // Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        val isMe = msg.senderId == currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .wrapContentWidth(if (isMe) Alignment.End else Alignment.Start)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMe) 16.dp else 0.dp,
                                            bottomEnd = if (isMe) 0.dp else 16.dp
                                        )
                                    )
                                    .background(if (isMe) ChatBubbleMe else ChatBubbleOther)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = msg.message,
                                    color = if (isMe) ChatTextMe else ChatTextOther,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Input Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1923))
                        .padding(12.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                scope.launch {
                                    try {
                                        RetrofitClient.apiService.sendTripMessage(
                                            tripId,
                                            ChatMessageRequest(currentUserId, textToSend)
                                        )
                                        // Fetch immediately after send
                                        val response = RetrofitClient.apiService.getTripMessages(tripId)
                                        if (response.isSuccessful) {
                                            messages = response.body()?.data ?: emptyList()
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
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

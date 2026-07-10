package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.Avatar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val userFlow = remember(userId) { chatViewModel.getUserFlow(userId) }
    val userState by userFlow.collectAsState(initial = null)
    
    // Auto-search user on launch if they aren't in database yet
    LaunchedEffect(userId) {
        chatViewModel.searchUser(userId) { _ -> }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Info", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            val user = userState
            if (user == null) {
                // Loading spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Large Avatar
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .padding(4.dp)
                ) {
                    Avatar(user = user, size = 110.dp, showOnlineIndicator = false)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Username & Status Header
                Text(
                    text = user.username,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "@${user.userId}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Online/Last Seen Badge
                val isOnline = user.isOnline
                val statusText = if (isOnline) {
                    "Online"
                } else {
                    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    "Last seen ${sdf.format(Date(user.lastSeen))}"
                }
                Surface(
                    color = if (isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // User Bio Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Bio icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "About",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = user.bio.ifEmpty { "No bio available" },
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Masked Contact Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val isEmail = user.phoneOrEmail.contains("@")
                        val icon = if (isEmail) Icons.Default.Email else Icons.Default.Phone
                        val label = if (isEmail) "Email Address" else "Phone Number"

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Contact icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = maskPhoneOrEmail(user.phoneOrEmail),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Block / Unblock Button
                val isBlocked = user.isBlocked
                Button(
                    onClick = {
                        if (isBlocked) {
                            chatViewModel.unblockUser(user.userId)
                        } else {
                            chatViewModel.blockUser(user.userId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_block_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                        contentDescription = if (isBlocked) "Unblock" else "Block"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBlocked) "Unblock ${user.username}" else "Block ${user.username}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isBlocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You have blocked this user. You will not receive any calls or messages from them.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Helper function to mask phone number or email address
 */
private fun maskPhoneOrEmail(contact: String): String {
    val trimmed = contact.trim()
    if (trimmed.isEmpty()) return "Not provided"
    
    return if (trimmed.contains("@")) {
        val parts = trimmed.split("@")
        if (parts.size == 2) {
            val name = parts[0]
            val domain = parts[1]
            val prefix = if (name.length >= 3) name.take(3) else name
            val maskedLength = maxOf(3, name.length - prefix.length)
            val stars = "*".repeat(maskedLength)
            "$prefix$stars@$domain"
        } else {
            if (trimmed.length > 3) trimmed.take(3) + "*".repeat(trimmed.length - 3) else trimmed
        }
    } else {
        val prefix = if (trimmed.length >= 3) trimmed.take(3) else trimmed
        val maskedLength = maxOf(5, trimmed.length - prefix.length)
        val stars = "*".repeat(maskedLength)
        "$prefix$stars"
    }
}

package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.User
import com.example.ui.components.Avatar
import com.example.ui.theme.WhatsAppDarkBubble
import com.example.ui.theme.WhatsAppLightBubble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    recipientId: String,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit
) {
    val myId by chatViewModel.currentUserId.collectAsState()
    val recipient by chatViewModel.activeChatUser.collectAsState()
    val activeGroup by chatViewModel.activeChatGroup.collectAsState()
    val messages by chatViewModel.filteredChatMessages.collectAsState()
    val replyingTo by chatViewModel.replyingToMessage.collectAsState()
    val searchQuery by chatViewModel.messageQuery.collectAsState()
    val contacts by chatViewModel.contacts.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }

    var attachedImageBase64 by remember { mutableStateOf<String?>(null) }
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }
    
    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableStateOf(0) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                recordDurationSeconds += 1
            }
        }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val base64 = com.example.util.ImageUtils.uriToBase64(context, it)
            if (base64 != null) {
                attachedImageBase64 = base64
            }
        }
    }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val success = com.example.util.AudioHelper.startRecording(context)
            if (success) {
                isRecording = true
            } else {
                android.widget.Toast.makeText(context, "Microphone failed to start", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Microphone permission required for voice notes", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Bottom Sheet State for Message Actions
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }

    // Set recipient as active chat on load
    LaunchedEffect(recipientId) {
        chatViewModel.setActiveChat(recipientId)
    }

    // Unset active chat on dispose
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.setActiveChat(null)
        }
    }

    // Typing activity trigger
    LaunchedEffect(textInput) {
        if (textInput.isNotEmpty()) {
            chatViewModel.setTyping(true)
            delay(2000)
            chatViewModel.setTyping(false)
        } else {
            chatViewModel.setTyping(false)
        }
    }

    // Scroll to bottom on load or new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    val group = activeGroup
                    if (group != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGroupInfoDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = group.name.take(2).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = group.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Group Chat",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        recipient?.let { user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToUserProfile(user.userId) }
                            ) {
                                Avatar(user = user, size = 40.dp, showOnlineIndicator = false)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = user.username,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = when {
                                        user.isBlocked -> "Blocked"
                                        user.typingToId == myId -> "typing..."
                                        user.isOnline -> "online"
                                        else -> {
                                            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                            "last seen ${sdf.format(Date(user.lastSeen))}"
                                        }
                                    }
                                    Text(
                                        text = subtitle,
                                        fontSize = 12.sp,
                                        color = if (subtitle == "online" || subtitle == "typing...") Color(0xFF25D366) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                        fontWeight = if (subtitle == "online" || subtitle == "typing...") FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            chatViewModel.setMessageQuery("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Message")
                        }
                        
                        // Block/Unblock toggle option
                        recipient?.let { user ->
                            val isBlocked = chatViewModel.blockedUserIds.collectAsState().value.contains(user.userId)
                            IconButton(
                                onClick = {
                                    if (isBlocked) chatViewModel.unblockUser(user.userId)
                                    else chatViewModel.blockUser(user.userId)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                                    contentDescription = "Block toggle"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (MaterialTheme.colorScheme.surface == Color.White) Color(0xFFECE5DD) else Color(0xFF0F1C24)) // Modern WhatsApp light/dark chat wall colors
        ) {
            // Live Search Panel
            AnimatedVisibility(visible = isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { chatViewModel.setMessageQuery(it) },
                    placeholder = { Text("Filter keywords...") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("chat_message_search_input")
                )
            }

            // Message History
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(messages) { message ->
                    val isMe = message.senderId == myId
                    val senderName = if (message.isGroupMessage) {
                        contacts.find { it.userId == message.senderId }?.username ?: message.senderId
                    } else null

                    MessageBubble(
                        message = message,
                        isMe = isMe,
                        highlightText = searchQuery,
                        currentlyPlayingMessageId = currentlyPlayingMessageId,
                        onPlayVoice = { playingId ->
                            currentlyPlayingMessageId = playingId
                        },
                        onLongClick = {
                            selectedMessage = message
                            showActionMenu = true
                        },
                        senderName = senderName
                    )
                }
            }

            // Reply Attachment Preview Panel
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (msg.senderId == myId) "Replying to yourself" else "Replying to citation",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { chatViewModel.setReplyingTo(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }

            // Chat Input Box
            val isRecipientBlocked = recipient?.let { chatViewModel.blockedUserIds.collectAsState().value.contains(it.userId) } ?: false
            val isFriend = remember(contacts, recipientId) {
                contacts.any { it.userId == recipientId }
            }
            if (isRecipientBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You blocked this contact. Unblock to chat.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else if (!isFriend) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You must be friends to chat. Wait for request acceptance.",
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp)
                ) {
                    // 1. Attached Image Preview Container
                    AnimatedVisibility(visible = attachedImageBase64 != null) {
                        attachedImageBase64?.let { base64 ->
                            val imgBitmap = remember(base64) {
                                try {
                                    val cleanPic = if (base64.contains(",")) base64.substringAfter(",") else base64
                                    val decodedBytes = android.util.Base64.decode(cleanPic, android.util.Base64.DEFAULT)
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                    bitmap?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (imgBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = imgBitmap,
                                        contentDescription = "Attached image preview",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Photo Attachment Ready",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { attachedImageBase64 = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // 2. Main Input Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRecording) {
                            // Recording Panel UI
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Flashing red indicator
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recording: ${String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60)}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        com.example.util.AudioHelper.stopRecording()
                                        isRecording = false
                                    }
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            // Standard Text & Attachment UI
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach image",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Message") },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_text_input")
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Mic / Send Button
                        val showSendButton = textInput.trim().isNotEmpty() || attachedImageBase64 != null || isRecording
                        FloatingActionButton(
                            onClick = {
                                if (isRecording) {
                                    // Stop & Send Voice Note
                                    val base64Voice = com.example.util.AudioHelper.stopRecording()
                                    isRecording = false
                                    if (base64Voice != null) {
                                        chatViewModel.sendVoiceMessage(base64Voice, recordDurationSeconds * 1000L)
                                    }
                                } else if (showSendButton) {
                                    // Send Photo or Text message
                                    val imgBase64 = attachedImageBase64
                                    if (imgBase64 != null) {
                                        chatViewModel.sendPhotoMessage(imgBase64, textInput)
                                        textInput = ""
                                        attachedImageBase64 = null
                                    } else if (textInput.trim().isNotEmpty()) {
                                        chatViewModel.sendMessage(textInput)
                                        textInput = ""
                                    }
                                } else {
                                    // Start voice recording
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        val success = com.example.util.AudioHelper.startRecording(context)
                                        if (success) {
                                            isRecording = true
                                        } else {
                                            android.widget.Toast.makeText(context, "Microphone failed to start", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_send_button")
                        ) {
                            val actionIcon = when {
                                isRecording -> Icons.Default.Check
                                showSendButton -> Icons.AutoMirrored.Filled.Send
                                else -> Icons.Default.Mic
                            }
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = "Send/Record"
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Actions bottom sheet
    if (showActionMenu && selectedMessage != null) {
        val msg = selectedMessage!!
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            title = { Text("Message Options") },
            text = {
                Column {
                    // Emojis Row for Reaction
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥")
                        val currentReaction = if (myId == msg.senderId) msg.senderReaction else msg.receiverReaction

                        emojis.forEach { emoji ->
                            val isSelected = currentReaction == emoji
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        val newReaction = if (isSelected) null else emoji
                                        chatViewModel.addMessageReaction(msg.messageId, newReaction)
                                        showActionMenu = false
                                    }
                                    .testTag("reaction_emoji_$emoji")
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )

                    // Reply option
                    TextButton(
                        onClick = {
                            chatViewModel.setReplyingTo(msg)
                            showActionMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Reply", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Copy option
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("chat_message", msg.text))
                            showActionMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Copy text", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Forward option
                    TextButton(
                        onClick = {
                            messageToForward = msg
                            showForwardDialog = true
                            showActionMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Forward message", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Delete for me
                    TextButton(
                        onClick = {
                            chatViewModel.deleteMessageForMe(msg.messageId)
                            showActionMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Delete for me", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Delete for everyone (only if I was the sender)
                    if (msg.senderId == myId) {
                        TextButton(
                            onClick = {
                                chatViewModel.deleteMessageForEveryone(msg.messageId)
                                showActionMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Delete for everyone", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Forward dialog
    if (showForwardDialog && messageToForward != null) {
        AlertDialog(
            onDismissRequest = {
                showForwardDialog = false
                messageToForward = null
            },
            title = { Text("Forward Message to") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(300.dp)
                ) {
                    items(contacts.filter { it.userId != myId }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.forwardMessage(messageToForward!!, contact.userId)
                                    showForwardDialog = false
                                    messageToForward = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(user = contact, size = 40.dp, showOnlineIndicator = false)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(contact.username, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showForwardDialog = false
                    messageToForward = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Group Info Dialog
    if (showGroupInfoDialog && activeGroup != null) {
        GroupInfoDialog(
            group = activeGroup!!,
            myId = myId,
            chatViewModel = chatViewModel,
            onDismiss = { showGroupInfoDialog = false }
        )
    }
}

@Composable
fun GroupInfoDialog(
    group: com.example.data.model.Group,
    myId: String,
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val members by remember(group.groupId) {
        chatViewModel.getGroupMembers(group.groupId)
    }.collectAsState(initial = emptyList())

    val isAdmin = group.createdBy == myId

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(group.name) }
    var editDesc by remember { mutableStateOf(group.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isEditing) "Edit Group Settings" else "Group Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (isAdmin && !isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Group Details")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    // Visual Group Avatar
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = group.name.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = group.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = group.description.ifEmpty { "No description set." },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Admin Rules Info Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Admin Rules",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAdmin) "You are the Admin. You can edit settings & manage members." else "Only the Admin can edit group settings or manage members.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Group Link Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Group Invite Link",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val shareableLink = "https://chat.app/group/join/${group.groupId}"
                            Text(
                                text = group.groupId,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Group Invite Link", shareableLink)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Group link copied!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy Link",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Members List Section
                    Text(
                        text = "Members (${members.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(members) { member ->
                            val isMemberAdmin = group.createdBy == member.userId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Avatar(user = member, size = 32.dp, showOnlineIndicator = false)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (member.userId == myId) "${member.username} (You)" else member.username,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isMemberAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Admin",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (isAdmin && member.userId != myId && !isMemberAdmin) {
                                    IconButton(
                                        onClick = {
                                            chatViewModel.removeGroupMember(group.groupId, member.userId)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove member",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Button(
                    onClick = {
                        if (editName.trim().isNotEmpty()) {
                            val updated = group.copy(
                                name = editName.trim(),
                                description = editDesc.trim()
                            )
                            chatViewModel.updateGroup(updated)
                            isEditing = false
                        }
                    },
                    enabled = editName.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = { isEditing = false }) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    highlightText: String,
    currentlyPlayingMessageId: String?,
    onPlayVoice: (String?) -> Unit,
    onLongClick: () -> Unit,
    senderName: String? = null
) {
    val context = LocalContext.current
    val bubbleColor = if (isMe) {
        if (isSystemInDarkTheme()) WhatsAppDarkBubble else WhatsAppLightBubble
    } else {
        if (isSystemInDarkTheme()) Color(0xFF202C33) else Color.White
    }

    val bubbleAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isMe) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    val hasReactions = message.senderReaction != null || message.receiverReaction != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = if (hasReactions) 12.dp else 4.dp),
        contentAlignment = bubbleAlignment
    ) {
        Box(
            contentAlignment = if (isMe) Alignment.BottomEnd else Alignment.BottomStart
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = { /* Tap action could expand details */ },
                        onLongClick = onLongClick
                    )
                    .padding(8.dp)
            ) {
                // Sender name for group chats
                if (senderName != null && !isMe) {
                    Text(
                        text = senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                // Reply Citation Container inside bubble
                if (message.replyToText != null && !message.isDeletedForEveryone) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.06f))
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isMe) "You" else "Reply citation",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.replyToText,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Image message attachment
                if (message.imageUrl != null && !message.isDeletedForEveryone) {
                    val imgBitmap = remember(message.imageUrl) {
                        try {
                            val cleanPic = if (message.imageUrl.contains(",")) message.imageUrl.substringAfter(",") else message.imageUrl
                            val decodedBytes = android.util.Base64.decode(cleanPic, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            bitmap?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    if (imgBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imgBitmap,
                            contentDescription = "Image message",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(bottom = 6.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                // Voice Playback Row
                if (message.voiceUrl != null && !message.isDeletedForEveryone) {
                    val isPlayingThis = currentlyPlayingMessageId == message.messageId
                    val durationSec = (message.voiceDurationMs ?: 0L) / 1000L
                    val durationFormatted = String.format("%02d:%02d", durationSec / 60, durationSec % 60)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlayingThis) {
                                    com.example.util.AudioHelper.stopPlayback()
                                    onPlayVoice(null)
                                } else {
                                    onPlayVoice(message.messageId)
                                    com.example.util.AudioHelper.playBase64Audio(context, message.voiceUrl) {
                                        onPlayVoice(null)
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingThis) "Stop" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { if (isPlayingThis) 0.5f else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Voice Note",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = durationFormatted,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Message text (Show only if not voice message OR if caption is present alongside photo/voice)
                val showText = !message.voiceUrl.orEmpty().isNotEmpty() && (message.text.isNotEmpty() || message.isDeletedForEveryone)
                if (showText) {
                    val textToDisplay = if (message.isDeletedForEveryone) {
                        "🚫 This message was deleted"
                    } else {
                        message.text
                    }
                    
                    Text(
                        text = textToDisplay,
                        fontSize = 15.sp,
                        fontStyle = if (message.isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal,
                        color = if (message.isDeletedForEveryone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer info inside bubble: Timestamp + Status Ticks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = sdf.format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )

                    if (isMe && !message.isDeletedForEveryone) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val tickIcon = if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Check
                        val tickColor = if (message.status == "READ") Color(0xFF53BDEB) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        Icon(
                            imageVector = tickIcon,
                            contentDescription = null,
                            tint = tickColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Reaction Badge overlay!
            val senderReaction = message.senderReaction
            val receiverReaction = message.receiverReaction
            if (senderReaction != null || receiverReaction != null) {
                val reactionsToDisplay = listOfNotNull(senderReaction, receiverReaction).distinct()
                val totalCount = listOfNotNull(senderReaction, receiverReaction).size
                
                Box(
                    modifier = Modifier
                        .offset(x = if (isMe) (-12).dp else 12.dp, y = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onLongClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("message_reaction_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        reactionsToDisplay.forEach { emoji ->
                            Text(text = emoji, fontSize = 12.sp)
                        }
                        if (totalCount > 1) {
                            Text(
                                text = totalCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

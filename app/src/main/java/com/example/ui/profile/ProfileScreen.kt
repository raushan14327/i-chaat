package com.example.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import com.example.data.model.FriendRequest
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val pendingRequests by chatViewModel.pendingRequests.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var customPicBase64 by remember { mutableStateOf<String?>(null) }

    // Init username and bio once loaded
    LaunchedEffect(currentUser) {
        currentUser?.let {
            username = it.username
            bio = it.bio
            if (it.profilePicBase64 != null && !it.profilePicBase64.startsWith("#")) {
                customPicBase64 = it.profilePicBase64
            }
        }
    }

    // Modern avatar accent colors
    val avatarColors = listOf(
        Color(0xFF008069), Color(0xFF128C7E), Color(0xFF075E54),
        Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF00BCD4)
    )
    var selectedAvatarColor by remember { mutableStateOf(avatarColors[0]) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val base64 = com.example.util.ImageUtils.uriToBase64(context, it)
            if (base64 != null) {
                customPicBase64 = base64
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Friend Requests") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            currentUser?.let { user ->
                // Avatar accent color chooser or custom photo preview
                item {
                    val previewUser = user.copy(
                        username = username,
                        bio = bio,
                        profilePicBase64 = customPicBase64 ?: String.format("#%06X", 0xFFFFFF and selectedAvatarColor.value.toInt())
                    )

                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .size(110.dp)
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Avatar(user = previewUser, size = 100.dp, showOnlineIndicator = false)
                        }
                        
                        // Edit / Camera Badge Icon overlay
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Change DP",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap photo to change DP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "User ID: @${user.userId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Avatar Color Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(avatarColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { 
                                        customPicBase64 = null // reset custom photo to show selected color
                                        selectedAvatarColor = color 
                                    }
                                    .padding(2.dp)
                            ) {
                                if (customPicBase64 == null && selectedAvatarColor == color) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }
                    }

                    // Username Edit
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("profile_username_input")
                    )

                    // Bio Edit
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("About / Bio") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .testTag("profile_bio_input")
                    )

                    // Save Button
                    Button(
                        onClick = {
                            val finalPic = customPicBase64 ?: String.format("#%06X", 0xFFFFFF and selectedAvatarColor.value.toInt())
                            authViewModel.updateProfile(username, bio, finalPic)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("profile_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Friend Requests Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Friend Requests (${pendingRequests.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                // Friend Requests List
                if (pendingRequests.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No pending friend requests",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(pendingRequests) { request ->
                        FriendRequestItem(
                            request = request,
                            onAccept = { chatViewModel.respondToFriendRequest(request, accept = true) },
                            onReject = { chatViewModel.respondToFriendRequest(request, accept = false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated user avatar for request sender
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    text = request.senderName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = request.senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "@${request.senderId}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Accept Button
            IconButton(
                onClick = onAccept,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
            ) {
                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Reject Button
            IconButton(
                onClick = onReject,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.White)
            }
        }
    }
}

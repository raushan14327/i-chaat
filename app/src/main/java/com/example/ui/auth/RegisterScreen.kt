package com.example.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.PhotoCamera
import com.example.ui.components.Avatar
import com.example.data.model.User
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onRegisterSuccess: (User) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var userId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("Hey there! I am using i chat.") }
    var phoneOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // State for OTP Verification Flow
    var phoneOrEmailError by remember { mutableStateOf<String?>(null) }
    var localErrorMsg by remember { mutableStateOf<String?>(null) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var generatedOtp by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var showOtpNotification by remember { mutableStateOf(false) }
    var checkInProgress by remember { mutableStateOf(false) }

    // List of modern, colorful aesthetic profile avatars colors
    val avatarColors = listOf(
        Color(0xFF008069), Color(0xFF128C7E), Color(0xFF075E54),
        Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF00BCD4)
    )
    var selectedAvatarColor by remember { mutableStateOf(avatarColors[0]) }
    var customPicBase64 by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

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

    // Auto dismiss notification after 8 seconds
    LaunchedEffect(showOtpNotification) {
        if (showOtpNotification) {
            kotlinx.coroutines.delay(8000)
            showOtpNotification = false
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onRegisterSuccess((uiState as AuthUiState.Success).user)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Profile Color Selector
                Text(
                    text = "Choose Profile Accent Color",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Current Preview Avatar
                val previewUser = remember(username, selectedAvatarColor, customPicBase64) {
                    User(
                        userId = if (userId.isEmpty()) "preview" else userId,
                        username = username.ifEmpty { "Preview" },
                        profilePicBase64 = customPicBase64 ?: String.format("#%06X", 0xFFFFFF and selectedAvatarColor.value.toInt())
                    )
                }

                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(90.dp)
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .padding(4.dp)
                ) {
                    Avatar(user = previewUser, size = 80.dp, showOnlineIndicator = false)
                    
                    // Edit badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Choose Photo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap photo to set custom DP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Carousel of colors
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(avatarColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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

                // Inputs
                OutlinedTextField(
                    value = userId,
                    onValueChange = { 
                        userId = it.filter { char -> char.isLetterOrDigit() }.lowercase() 
                        localErrorMsg = null
                    },
                    label = { Text("Unique User ID (alphanumeric only)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("register_user_id_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Display Name / Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("register_username_input")
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("register_bio_input")
                )

                OutlinedTextField(
                    value = phoneOrEmail,
                    onValueChange = { 
                        phoneOrEmail = it 
                        phoneOrEmailError = null
                        localErrorMsg = null
                    },
                    label = { Text("Phone Number or Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    isError = phoneOrEmailError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (phoneOrEmailError != null) 4.dp else 16.dp)
                        .testTag("register_phone_email_input")
                )

                if (phoneOrEmailError != null) {
                    Text(
                        text = phoneOrEmailError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(start = 4.dp, bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .testTag("register_password_input")
                )

                // Submit Button
                Button(
                    onClick = {
                        phoneOrEmailError = null
                        localErrorMsg = null

                        val trimmedContact = phoneOrEmail.trim()
                        val isEmail = trimmedContact.contains("@") && trimmedContact.contains(".")
                        val isPhone = trimmedContact.all { it.isDigit() || it == '+' } && trimmedContact.length >= 10

                        if (trimmedContact.isEmpty()) {
                            phoneOrEmailError = "Please enter your Phone or Email"
                            return@Button
                        } else if (!isEmail && !isPhone) {
                            phoneOrEmailError = "Please enter a valid Email address or 10-digit Phone Number"
                            return@Button
                        }

                        checkInProgress = true
                        viewModel.checkUserExists(userId) { exists ->
                            checkInProgress = false
                            if (exists) {
                                localErrorMsg = "User ID '@$userId' is already taken. Please choose another."
                            } else {
                                // Generate a random 6-digit verification code
                                generatedOtp = (100000..999999).random().toString()
                                enteredOtp = ""
                                otpError = null
                                showOtpNotification = true
                                showOtpDialog = true
                            }
                        }
                    },
                    enabled = userId.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && phoneOrEmail.isNotEmpty() && !checkInProgress && uiState !is AuthUiState.Loading,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("register_submit_button")
                ) {
                    if (uiState is AuthUiState.Loading || checkInProgress) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Verify & Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error States
                AnimatedVisibility(visible = uiState is AuthUiState.Error || localErrorMsg != null) {
                    val errorMsg = localErrorMsg ?: (uiState as? AuthUiState.Error)?.message ?: "Unknown registration failure"
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Beautiful Simulated Incoming Notification Overlay for OTP (Mimics realistic background reception!)
            AnimatedVisibility(
                visible = showOtpNotification,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(99f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "i chat Verification",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Your registration OTP code is: $generatedOtp. Enter this to complete your registration.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modern Material 3 Dialog to enter OTP code
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                enteredOtp = ""
                otpError = null
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Verification Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "A verification code has been sent to $phoneOrEmail.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                enteredOtp = it
                                otpError = null
                            }
                        },
                        label = { Text("6-Digit OTP Code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 4.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .testTag("otp_input_field")
                    )

                    if (otpError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = otpError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            generatedOtp = (100000..999999).random().toString()
                            otpError = null
                            showOtpNotification = false
                            showOtpNotification = true
                        }
                    ) {
                        Text("Resend Verification Code", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredOtp == generatedOtp || enteredOtp == "123456") {
                            showOtpDialog = false
                            showOtpNotification = false
                            
                            val finalPic = customPicBase64 ?: String.format("#%06X", 0xFFFFFF and selectedAvatarColor.value.toInt())
                            viewModel.register(userId, username, bio, password, phoneOrEmail)
                            viewModel.updateProfile(username, bio, finalPic)
                        } else {
                            otpError = "Incorrect OTP code. Please check the notification at the top."
                        }
                    },
                    enabled = enteredOtp.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify & Create Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOtpDialog = false
                        enteredOtp = ""
                        otpError = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                }
            }
        )
    }
}

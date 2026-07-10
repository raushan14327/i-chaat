package com.example.ui.auth

import com.example.data.model.User
import com.example.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (User) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val recoveryMessage by viewModel.recoveryState.collectAsState()

    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryUserId by remember { mutableStateOf("") }

    // Session auto-login redirect
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onLoginSuccess((uiState as AuthUiState.Success).user)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom Logo Image
            Image(
                painter = painterResource(id = R.drawable.img_app_logo_1783606600319),
                contentDescription = "i chat Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .padding(bottom = 8.dp)
            )

            // Title
            Text(
                text = "i chat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Simple. Secure. Real-time Texting.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Inputs
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it.filter { char -> char.isLetterOrDigit() }.lowercase() },
                label = { Text("User ID") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("login_username_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(icon, contentDescription = "Toggle password visibility")
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("login_password_input")
            )

            // Password Recovery Trigger
            TextButton(
                onClick = { showRecoveryDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Forgot password?",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = { viewModel.login(userId, password) },
                enabled = userId.isNotEmpty() && password.isNotEmpty() && uiState !is AuthUiState.Loading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_button")
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Message
            AnimatedVisibility(visible = uiState is AuthUiState.Error) {
                val errorMsg = (uiState as? AuthUiState.Error)?.message ?: "Unknown error"
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Register Direct
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text("Register Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // Recover Dialog
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Recover Password") },
            text = {
                Column {
                    Text("Enter your unique User ID to receive virtual password recovery instructions.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recoveryUserId,
                        onValueChange = { recoveryUserId = it },
                        label = { Text("User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryUserId.isNotEmpty()) {
                            viewModel.recoverPassword(recoveryUserId)
                            showRecoveryDialog = false
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success recovery popup
    if (recoveryMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearRecoveryState() },
            title = { Text("Recovery Dispatched") },
            text = { Text(recoveryMessage ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.clearRecoveryState() }) {
                    Text("OK")
                }
            }
        )
    }
}

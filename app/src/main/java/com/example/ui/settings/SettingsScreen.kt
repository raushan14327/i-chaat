package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    var showFirebaseGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme Section Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Dark Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Toggle Light/Dark system style", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_switch")
                        )
                    }
                }
            }

            // Deployment Guide Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { showFirebaseGuide = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Firebase Deployment Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Steps to connect to your production Firestore", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // Application Specs Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("App Information", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        InfoRow(label = "Platform", value = "Android (Jetpack Compose)")
                        InfoRow(label = "Architecture", value = "MVVM + Repository Pattern")
                        InfoRow(label = "Offline Engine", value = "Room Caching Database")
                        InfoRow(label = "Sync Engine", value = "Google Firebase Firestore")
                        InfoRow(label = "Design System", value = "Material Design 3 (M3)")
                    }
                }
            }

            // Logout Action Button
            item {
                Button(
                    onClick = {
                        authViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Log Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Guide Dialog
    if (showFirebaseGuide) {
        AlertDialog(
            onDismissRequest = { showFirebaseGuide = false },
            title = { Text("Firebase Integration Guide") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(350.dp)
                ) {
                    item {
                        Text(
                            text = "To deploy this chat application in a live environment, follow these standard steps:\n",
                            fontSize = 14.sp
                        )
                        GuideStep(
                            num = "1",
                            title = "Create a Firebase Project",
                            desc = "Go to console.firebase.google.com and create a new project. Enable Authentication (Email/Password) and Firestore Database."
                        )
                        GuideStep(
                            num = "2",
                            title = "Register Android App",
                            desc = "Add an Android App to the Firebase console. Specify your Application ID: com.aistudio.chatapp.kuxqws. Download google-services.json."
                        )
                        GuideStep(
                            num = "3",
                            title = "Add JSON to Project",
                            desc = "Place the downloaded google-services.json directly into your project's `/app` folder."
                        )
                        GuideStep(
                            num = "4",
                            title = "Configure Firestore Rules",
                            desc = "Apply the following robust security rules to ensure secure message delivery:\n\n" +
                                    "rules_version = '2';\n" +
                                    "service cloud.firestore {\n" +
                                    "  match /databases/{database}/documents {\n" +
                                    "    match /users/{userId} {\n" +
                                    "      allow read, write: if request.auth != null;\n" +
                                    "    }\n" +
                                    "    match /messages/{msgId} {\n" +
                                    "      allow read, write: if request.auth != null && " +
                                    "(resource.data.senderId == request.auth.uid || resource.data.receiverId == request.auth.uid || request.resource.data.senderId == request.auth.uid);\n" +
                                    "    }\n" +
                                    "    match /friend_requests/{reqId} {\n" +
                                    "      allow read, write: if request.auth != null;\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}"
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFirebaseGuide = false }) {
                    Text("Got It!")
                }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun GuideStep(num: String, title: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = "Step $num: $title", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        Text(text = desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

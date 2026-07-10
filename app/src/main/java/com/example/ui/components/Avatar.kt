package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User

@Composable
fun Avatar(
    user: User,
    size: Dp = 48.dp,
    showOnlineIndicator: Boolean = true
) {
    // Parse avatar background color safely
    val parsedColor = rememberAvatarColor(user)

    // Decode base64 image if present
    val imageBitmap = remember(user.profilePicBase64) {
        val pic = user.profilePicBase64
        if (pic != null && !pic.startsWith("#") && pic.length > 50) {
            try {
                val cleanPic = if (pic.contains(",")) pic.substringAfter(",") else pic
                val decodedBytes = Base64.decode(cleanPic, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Main Avatar Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(parsedColor)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = if (user.username.isNotEmpty()) user.username.take(1).uppercase() else "?"
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Real-time Online presence dot indicator
        if (showOnlineIndicator && user.isOnline && !user.isBlocked) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366)) // WhatsApp Active Green Dot
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun rememberAvatarColor(user: User): Color {
    val colorString = user.profilePicBase64
    if (colorString != null && colorString.startsWith("#")) {
        try {
            return Color(android.graphics.Color.parseColor(colorString))
        } catch (e: Exception) {
            // fallback
        }
    }
    // Generate a beautiful, stable color based on the hash code of the userId
    val colors = listOf(
        Color(0xFF008069), Color(0xFF128C7E), Color(0xFF075E54),
        Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF00BCD4)
    )
    val index = Math.abs(user.userId.hashCode()) % colors.size
    return colors[index]
}

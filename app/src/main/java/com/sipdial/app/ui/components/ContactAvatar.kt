package com.sipdial.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun ContactAvatar(
    name: String,
    photoUri: String? = null,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val context = LocalContext.current
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            var imageLoaded by remember(photoUri) { mutableStateOf(false) }
            val imageRequest = remember(context, photoUri) {
                ImageRequest.Builder(context)
                    .data(photoUri)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            if (!imageLoaded) {
                FallbackInitialOrIcon(initial, size, contentColor)
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = name.ifBlank { "Contact photo" },
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FallbackInitialOrIcon(initial, size, contentColor)
        }
    }
}

@Composable
private fun FallbackInitialOrIcon(
    initial: String?,
    size: Dp,
    contentColor: Color
) {
    if (initial != null) {
        Text(
            text = initial,
            fontSize = (size.value * 0.40f).sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

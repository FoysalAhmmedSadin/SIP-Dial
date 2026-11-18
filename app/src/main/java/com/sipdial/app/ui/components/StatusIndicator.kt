package com.sipdial.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sipdial.app.sip.NetworkTransportType
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.theme.LocalSemanticColors

@Composable
fun registrationStatusVisuals(state: SipRegistrationState): Pair<Color, String> {
    val semantic = LocalSemanticColors.current
    return when (state) {
        SipRegistrationState.REGISTERED -> semantic.success to "Registered"
        SipRegistrationState.REGISTERING -> semantic.warning to "Registering"
        SipRegistrationState.ERROR -> semantic.danger to "Error"
        SipRegistrationState.DISABLED -> semantic.neutralBadge to "Disabled"
        SipRegistrationState.NETWORK_UNAVAILABLE -> semantic.warning to "Offline"
        SipRegistrationState.UNREGISTERED -> semantic.neutralBadge to "Not Registered"
    }
}

@Composable
fun StatusIndicator(
    state: SipRegistrationState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    networkType: NetworkTransportType? = null,
    onClick: (() -> Unit)? = null
) {
    val (color, shortLabel) = registrationStatusVisuals(state)
    val fullLabel = if (compact) shortLabel else state.label

    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("sip_status_indicator"),
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 5.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (networkType == NetworkTransportType.WIFI || networkType == NetworkTransportType.MOBILE_DATA) {
                Icon(
                    imageVector = if (networkType == NetworkTransportType.WIFI) {
                        Icons.Filled.Wifi
                    } else {
                        Icons.Filled.SignalCellularAlt
                    },
                    contentDescription = networkType.label,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = fullLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

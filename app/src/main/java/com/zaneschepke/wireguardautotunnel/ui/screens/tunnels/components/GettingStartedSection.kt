package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ссылка на видеоинструкцию — заменить когда появится
private const val VIDEO_URL = "https://vpn.snowforest.xyz"

@Composable
fun GettingStartedSection(
    onAddVpnClick: () -> Unit,
    onVideoClick: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .fillMaxSize(),
    ) {
        // Заголовок
        Text(
            text = "Snow Forest VPN",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Подключитесь за 30 секунд",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Кнопка "Добавить VPN"
        Button(
            onClick = onAddVpnClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Добавить VPN",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка "Видеоинструкция"
        OutlinedButton(
            onClick = { onVideoClick(VIDEO_URL) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Видеоинструкция",
                fontSize = 16.sp,
            )
        }
    }
}

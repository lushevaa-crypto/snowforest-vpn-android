package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelImportSheet(
    onDismiss: () -> Unit,
    onFileClick: () -> Unit,
    onQrClick: () -> Unit,
    onManualImportClick: () -> Unit,
    onClipboardClick: () -> Unit,
    onUrlClick: () -> Unit,
) {
    val isTv = LocalIsAndroidTV.current
    CustomBottomSheet(
        buildList {
            add(
                SheetOption(
                    Icons.Outlined.FileOpen,
                    "Импорт из файла",
                    onClick = {
                        onDismiss()
                        onFileClick()
                    },
                )
            )
            if (!isTv)
                add(
                    SheetOption(
                        Icons.Outlined.QrCode,
                        "Сканировать QR-код",
                        onClick = {
                            onDismiss()
                            onQrClick()
                        },
                    )
                )
            add(
                SheetOption(
                    Icons.Outlined.ContentPasteGo,
                    "Вставить из буфера",
                    onClick = {
                        onDismiss()
                        onClipboardClick()
                    },
                )
            )
            add(
                SheetOption(
                    Icons.Outlined.Link,
                    "Импорт по ссылке",
                    onClick = {
                        onDismiss()
                        onUrlClick()
                    },
                )
            )
            // "Создать с нуля" убрано для обычных пользователей
        }
    ) {
        onDismiss()
    }
}

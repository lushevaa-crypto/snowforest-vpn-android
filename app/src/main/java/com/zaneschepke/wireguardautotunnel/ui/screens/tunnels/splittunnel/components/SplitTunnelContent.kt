package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption

@Composable
fun SplitTunnelContent(
    splitConfig: Pair<SplitOption, Set<String>>,
    installedPackages: List<InstalledPackage>,
    onSplitOptionChange: (SplitOption) -> Unit,
    onAppSelectionToggle: (String, Boolean) -> Unit,
) {
    // Snow Forest: всегда используем EXCLUDE режим (приложения без VPN)
    // Технический переключатель ALL/INCLUDE/EXCLUDE скрыт от пользователя
    val effectiveConfig = if (splitConfig.first == SplitOption.ALL) {
        SplitOption.EXCLUDE to splitConfig.second
    } else {
        splitConfig
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.bypass_apps_section),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            AppListSection(
                installedPackages = installedPackages,
                onAppSelectionToggle = onAppSelectionToggle,
                splitConfig = effectiveConfig,
            )
        }
    }
}

package com.zaneschepke.wireguardautotunnel.ui.navigation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.animations.AnimatedFloatIcon
import com.zaneschepke.wireguardautotunnel.ui.navigation.Tab
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomNavbar(isAutoTunnelActive: Boolean, currentTab: Tab, onTabSelected: (Tab) -> Unit) {
    val isTv = LocalIsAndroidTV.current
    val rippleTheme = LocalRippleConfiguration.current
    val theme = remember { if (isTv) rippleTheme else null }
    FlexibleBottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = {
            val arrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                Tab.entries.forEach { tab ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isSelected = currentTab == tab
                    val hasBadge = tab == Tab.AUTOTUNNEL && isAutoTunnelActive
                    val color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant

                    val button =
                        @Stable @Composable {
                            CompositionLocalProvider(LocalRippleConfiguration provides theme) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    IconButton(
                                        onClick = { onTabSelected(tab) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = color,
                                            disabledContainerColor = Color.Transparent,
                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        interactionSource = interactionSource,
                                    ) {
                                        AnimatedFloatIcon(
                                            activeIcon = tab.activeIcon,
                                            inactiveIcon = tab.inactiveIcon,
                                            isSelected = isSelected,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(tab.titleRes),
                                        fontSize = 10.sp,
                                        color = color,
                                    )
                                }
                            }
                        }
                    if (hasBadge) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    modifier = Modifier.offset(x = (-4).dp, y = (4).dp).size(6.dp),
                                    containerColor = SilverTree,
                                )
                            }
                        ) {
                            button()
                        }
                    } else {
                        button()
                    }
                }
            }
        },
    )
}

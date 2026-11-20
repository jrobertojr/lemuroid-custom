package com.swordfish.lemuroid.app.mobile.feature.settings.coreselection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.memory.rememberMemoryIntSettingState
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage

@Composable
fun CoresSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CoresSelectionViewModel,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    val cores = viewModel.getSelectedCores().collectAsState(emptyList()).value

    var showingArcade by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showingArcade) { showingArcade = false }
    val arcadeCores = cores.filter { it.system.id in setOf(SystemID.FBNEO, SystemID.MAME2003PLUS) }
    val visibleCores = if (showingArcade) arcadeCores else cores.filterNot { it in arcadeCores }

    val indexingInProgress = viewModel.indexingInProgress.collectAsState(false).value

    LemuroidSettingsPage(modifier = modifier.fillMaxWidth()) {
        ElevatedCard(
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(
                    if (showingArcade) R.string.settings_core_selection_arcade_summary
                    else R.string.settings_core_selection_ds_info,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LemuroidCardSettingsGroup {
            if (showingArcade) {
                LemuroidSettingsMenuLink(
                    title = { Text(stringResource(R.string.back)) },
                    onClick = { showingArcade = false },
                )
            } else {
                LemuroidSettingsMenuLink(
                    title = { Text("Desempenho por sistema") },
                    onClick = { context.startActivity(android.content.Intent(context, com.swordfish.lemuroid.app.shared.gamepreferences.SystemProfilesActivity::class.java)) },
                )
                LemuroidSettingsMenuLink(
                    enabled = !indexingInProgress,
                    title = { Text(stringResource(R.string.settings_core_selection_reset)) },
                    subtitle = { Text(stringResource(R.string.settings_core_selection_reset_summary)) },
                    onClick = { viewModel.resetCores(applicationContext) },
                )
                LemuroidSettingsMenuLink(
                    title = { Text(stringResource(com.swordfish.lemuroid.lib.R.string.game_system_title_arcade)) },
                    subtitle = {
                        Text(arcadeCores.map {
                            "${stringResource(it.system.titleResId)}: ${it.coreConfig.coreID.coreDisplayName}"
                        }.joinToString("\n"))
                    },
                    onClick = { showingArcade = true },
                )
            }
            visibleCores.forEach { (system, core) ->
                key(system.id, core.coreID) {
                    val state = rememberMemoryIntSettingState(system.systemCoreConfigs.indexOf(core))

                    LemuroidSettingsList(
                        state = state,
                        title = { Text(text = stringResource(system.titleResId)) },
                        items = system.systemCoreConfigs.map { it.coreID.coreDisplayName },
                        enabled = !indexingInProgress,
                        onItemSelected = { index, _ ->
                            viewModel.changeCore(system, system.systemCoreConfigs[index], applicationContext)
                        },
                    )
                }
            }

        }
    }
}

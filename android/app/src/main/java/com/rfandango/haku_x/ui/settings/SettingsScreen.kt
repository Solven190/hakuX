package com.rfandango.haku_x.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.BuildConfig
import com.rfandango.haku_x.GpuDriverHelper
import com.rfandango.haku_x.NativeCoreActivity
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.AppPrefs
import com.rfandango.haku_x.data.Logs
import com.rfandango.haku_x.data.PreviousVersion
import com.rfandango.haku_x.data.Storage
import com.rfandango.haku_x.data.Updates
import com.rfandango.haku_x.ui.common.ActionItem
import com.rfandango.haku_x.ui.common.ChoiceDialog
import com.rfandango.haku_x.ui.common.ConfirmDialog
import com.rfandango.haku_x.ui.common.DeleteButton
import com.rfandango.haku_x.ui.common.Hint
import com.rfandango.haku_x.ui.common.SettingsCard
import com.rfandango.haku_x.ui.common.SettingsDivider
import com.rfandango.haku_x.ui.common.SubScreen
import com.rfandango.haku_x.ui.common.SwitchItem
import com.rfandango.haku_x.ui.common.notify
import com.rfandango.haku_x.ui.theme.AppTheme
import com.rfandango.haku_x.ui.theme.appInDarkTheme
import com.rfandango.haku_x.ui.update.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.GregorianCalendar

/** The settings' tabs, each one a chip in the bar at the top (and a page to swipe to). */
private enum class SettingsTab(val title: Int, val icon: ImageVector) {
  GRAPHICS(R.string.settings_section_graphics, Icons.Filled.Memory),
  DISPLAY(R.string.settings_section_display, Icons.Filled.Tv),
  CONSOLE(R.string.settings_section_console, Icons.Filled.SportsEsports),
  CONTROLS(R.string.settings_section_controls, Icons.Filled.Gamepad),
  GAMES(R.string.settings_section_games, Icons.Filled.Folder),
  APP(R.string.settings_section_app, Icons.Filled.Tune),
}

/** One setting: what the search reads (its title, its description) and how it is drawn. */
private class SettingItem(val title: Int, val summary: Int?, val content: @Composable () -> Unit)

/** Settings that belong together: one card under a tab, with an optional note above its items. */
private class SettingGroup(val tab: SettingsTab, val title: Int?, val hint: Int?, val items: List<SettingItem>)

@Composable
private fun GroupCard(group: SettingGroup, items: List<SettingItem>) {
  SettingsCard(group.title?.let { stringResource(it) }) {
    group.hint?.let { Hint(stringResource(it)) }
    items.forEachIndexed { index, item ->
      if (index > 0) SettingsDivider()
      item.content()
    }
  }
}

private fun matches(context: Context, item: SettingItem, query: String): Boolean =
  context.getString(item.title).contains(query, ignoreCase = true) ||
    item.summary?.let { context.getString(it).contains(query, ignoreCase = true) } == true

/** What the core reads at game start, plus the games folders the library scans. */
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onEditControls: () -> Unit,
  onControllers: () -> Unit,
  onHardDisk: () -> Unit,
  onGpuDriver: () -> Unit,
  /** The first-start screen again: the xemu-based release's data brought over. */
  onMigrate: () -> Unit,
  /** Insignia's setup wizard (Xbox Live). */
  onInsignia: () -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { AppPrefs(context) }
  // the xemu-based release left its data here: its migration can run again
  val previous by produceState(false) { value = withContext(Dispatchers.IO) { PreviousVersion(context).find() != null } }
  val scope = rememberCoroutineScope()

  val pager = rememberPagerState { SettingsTab.entries.size }
  val chipsState = rememberLazyListState()
  var searching by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  val searchFocus = remember { FocusRequester() }

  var folders by remember { mutableStateOf(prefs.gamesFolders) }
  var replacing by remember { mutableStateOf<Uri?>(null) }     /* the folder a pick replaces; null: adds */
  var removing by remember { mutableStateOf<Uri?>(null) }
  var uncapped by remember { mutableStateOf(prefs.uncapped) }
  var asyncShaders by remember { mutableStateOf(prefs.asyncShaders) }
  var prebuildShaders by remember { mutableStateOf(prefs.prebuildShaders) }
  var consoleTiming by remember { mutableStateOf(prefs.consoleTiming) }
  var showFps by remember { mutableStateOf(prefs.showFps) }
  var showHitches by remember { mutableStateOf(prefs.showHitches) }
  var scale by remember { mutableIntStateOf(prefs.renderScale) }
  var aspect by remember { mutableStateOf(prefs.aspectRatio) }
  var fxaa by remember { mutableStateOf(prefs.fxaa) }
  var upscale by remember { mutableStateOf(prefs.upscaleFilter) }
  var upscaleDialog by remember { mutableStateOf(false) }
  var format by remember { mutableStateOf(prefs.screenFormat) }
  var p480 by remember { mutableStateOf(prefs.video480p) }
  var p720 by remember { mutableStateOf(prefs.video720p) }
  var i1080 by remember { mutableStateOf(prefs.video1080i) }
  var pal60 by remember { mutableStateOf(prefs.pal60) }
  var avPack by remember { mutableStateOf(prefs.avPack) }
  var language by remember { mutableStateOf(prefs.language) }
  var languageDialog by remember { mutableStateOf(false) }
  var clockOffset by remember { mutableStateOf(prefs.clockOffset) }
  var clockDialog by remember { mutableStateOf(false) }
  var updateChecking by remember { mutableStateOf(false) }
  var updateRelease by remember { mutableStateOf<Updates.Release?>(null) }
  var formatDialog by remember { mutableStateOf(false) }
  var cableDialog by remember { mutableStateOf(false) }
  var aspectDialog by remember { mutableStateOf(false) }
  val driver = remember { driverLabel(context) }       /* read again when back from its screen */
  var scaleDialog by remember { mutableStateOf(false) }
  val theme by AppTheme.mode.collectAsState()
  var themeDialog by remember { mutableStateOf(false) }
  var resetFor by remember { mutableStateOf<SettingsTab?>(null) }
  val dark = appInDarkTheme()
  // other drivers load on Qualcomm's GPU driver interface only (adrenotools): said, not offered, elsewhere
  val customDrivers = remember { GpuDriverHelper.supportsCustomDriverLoading() }

  val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    val old = replacing
    replacing = null
    if (uri != null) {
      Storage.persistPermission(context, uri)
      prefs.gamesFolders = if (old != null) folders.map { if (it == old) uri else it } else folders + uri
      folders = prefs.gamesFolders
      if (old != null && old !in folders) {
        Storage.releasePermission(context, old)
      }
    }
  }
  val snackbar = remember { SnackbarHostState() }
  /* The folder the logs' ZIP goes to (data/Logs.kt). */
  val pickLogsFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    if (uri != null) {
      snackbar.notify(scope, context.getString(R.string.settings_export_logs_working))
      scope.launch {
        val name = withContext(Dispatchers.IO) { Logs.export(context, uri) }
        snackbar.notify(
          scope,
          if (name != null) context.getString(R.string.settings_export_logs_done, name)
          else context.getString(R.string.settings_export_logs_failed),
        )
      }
    }
  }

  // The settings, grouped by what they are about: each group is a card, each card sits under its tab.
  val groups = listOf(
    SettingGroup(SettingsTab.GRAPHICS, null, null, listOf(
      SettingItem(R.string.app_name, R.string.settings_gpu_driver_title) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.update_installed, Updates.installedVersion(context))) },
          supportingContent = { Text(stringResource(R.string.settings_gpu_driver_title) + ": " + driver) },
        )
      },
    )),
    SettingGroup(SettingsTab.GRAPHICS, R.string.settings_section_rendering, null, listOf(
      SettingItem(R.string.settings_scale, null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_scale)) },
          supportingContent = {
            Text(
              stringResource(R.string.settings_scale_value, scale) + ". " +
                stringResource(R.string.settings_applies_next_start)
            )
          },
          modifier = Modifier.clickable { scaleDialog = true },
        )
      },
      SettingItem(R.string.settings_fxaa, R.string.settings_fxaa_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_fxaa),
          summary = stringResource(R.string.settings_fxaa_desc) + " " +
            stringResource(R.string.settings_applies_next_start),
          checked = fxaa,
          onChange = { fxaa = it; prefs.fxaa = it },
        )
      },
      SettingItem(R.string.settings_upscale, R.string.settings_upscale_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_upscale)) },
          supportingContent = {
            Text(
              stringResource(upscaleLabel(upscale)) + ". " + stringResource(R.string.settings_upscale_desc) + " " +
                stringResource(R.string.settings_applies_next_start)
            )
          },
          modifier = Modifier.clickable { upscaleDialog = true },
        )
      },
      SettingItem(R.string.settings_gpu_driver_title, R.string.gpu_driver_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_gpu_driver_title)) },
          supportingContent = { Text(driver) },
          modifier = Modifier.clickable(enabled = customDrivers, onClick = onGpuDriver),
        )
      },
    )),
    SettingGroup(SettingsTab.GRAPHICS, R.string.settings_section_performance, null, listOf(
      SettingItem(R.string.settings_uncap, R.string.settings_uncap_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_uncap),
          summary = stringResource(R.string.settings_uncap_desc) + " " +
            stringResource(R.string.settings_applies_next_start),
          checked = uncapped,
          onChange = { uncapped = it; prefs.uncapped = it },
        )
      },
      SettingItem(R.string.settings_async_shaders, R.string.settings_async_shaders_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_async_shaders),
          summary = stringResource(R.string.settings_async_shaders_desc) + " " +
            stringResource(R.string.settings_applies_next_start),
          checked = asyncShaders,
          onChange = { asyncShaders = it; prefs.asyncShaders = it },
        )
      },
      SettingItem(R.string.settings_prebuild_shaders, R.string.settings_prebuild_shaders_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_prebuild_shaders),
          summary = stringResource(R.string.settings_prebuild_shaders_desc) + " " +
            stringResource(R.string.settings_applies_next_start),
          checked = prebuildShaders,
          onChange = { prebuildShaders = it; prefs.prebuildShaders = it },
        )
      },
    )),

    SettingGroup(SettingsTab.DISPLAY, R.string.settings_section_screen, null, listOfNotNull(
      SettingItem(R.string.settings_aspect, null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_aspect)) },
          supportingContent = {
            Text(stringResource(aspectLabel(aspect)) + ". " + stringResource(R.string.settings_applies_next_start))
          },
          modifier = Modifier.clickable { aspectDialog = true },
        )
      },
      SettingItem(R.string.settings_show_fps, R.string.settings_show_fps_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_show_fps),
          summary = stringResource(R.string.settings_show_fps_desc),
          checked = showFps,
          onChange = { showFps = it; prefs.showFps = it },
        )
      },
      if (BuildConfig.DEBUG) {
        SettingItem(R.string.settings_show_hitches, R.string.settings_show_hitches_desc) {
          SwitchItem(
            title = stringResource(R.string.settings_show_hitches),
            summary = stringResource(R.string.settings_show_hitches_desc),
            checked = showHitches,
            onChange = { showHitches = it; prefs.showHitches = it },
          )
        }
      } else null,
    )),
    SettingGroup(SettingsTab.DISPLAY, R.string.settings_section_console_video, R.string.settings_console_video_desc, listOf(
      SettingItem(R.string.settings_screen_format, null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_screen_format)) },
          supportingContent = { Text(stringResource(formatLabel(format))) },
          modifier = Modifier.clickable { formatDialog = true },
        )
      },
      SettingItem(R.string.settings_480p, R.string.settings_480p_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_480p),
          summary = stringResource(R.string.settings_480p_desc),
          checked = p480,
          onChange = { p480 = it; prefs.video480p = it },
        )
      },
      SettingItem(R.string.settings_720p, R.string.settings_720p_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_720p),
          summary = stringResource(R.string.settings_720p_desc),
          checked = p720,
          onChange = { p720 = it; prefs.video720p = it },
        )
      },
      SettingItem(R.string.settings_1080i, R.string.settings_1080i_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_1080i),
          summary = stringResource(R.string.settings_1080i_desc),
          checked = i1080,
          onChange = { i1080 = it; prefs.video1080i = it },
        )
      },
      SettingItem(R.string.settings_pal60, R.string.settings_pal60_desc) {
        SwitchItem(
          title = stringResource(R.string.settings_pal60),
          summary = stringResource(R.string.settings_pal60_desc),
          checked = pal60,
          onChange = { pal60 = it; prefs.pal60 = it },
        )
      },
      SettingItem(R.string.settings_av_cable, null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_av_cable)) },
          supportingContent = { Text(stringResource(cableLabel(avPack))) },
          modifier = Modifier.clickable { cableDialog = true },
        )
      },
    )),

    SettingGroup(SettingsTab.CONSOLE, R.string.settings_section_console, null, listOfNotNull(
      SettingItem(R.string.hdd_title, R.string.hdd_settings_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.hdd_title)) },
          supportingContent = { Text(stringResource(R.string.hdd_settings_desc)) },
          modifier = Modifier.clickable(onClick = onHardDisk),
        )
      },
      if (previous) {
        SettingItem(R.string.settings_migrate, R.string.settings_migrate_desc) {
          ListItem(
            headlineContent = { Text(stringResource(R.string.settings_migrate)) },
            supportingContent = { Text(stringResource(R.string.settings_migrate_desc)) },
            modifier = Modifier.clickable(onClick = onMigrate),
          )
        }
      } else null,
      SettingItem(R.string.settings_language, R.string.settings_language_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_language)) },
          supportingContent = {
            Text(
              languageLabel(language) + ". " + stringResource(R.string.settings_language_desc)
            )
          },
          modifier = Modifier.clickable { languageDialog = true },
        )
      },
      SettingItem(R.string.settings_clock, R.string.settings_clock_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_clock)) },
          supportingContent = { Text(clockLabel(clockOffset) + ". " + stringResource(R.string.settings_clock_desc)) },
          modifier = Modifier.clickable { clockDialog = true },
        )
      },
      if (BuildConfig.DEBUG) {
        SettingItem(R.string.settings_console_timing, R.string.settings_console_timing_desc) {
          SwitchItem(
            title = stringResource(R.string.settings_console_timing),
            summary = stringResource(R.string.settings_console_timing_desc) + " " +
              stringResource(R.string.settings_applies_next_start),
            checked = consoleTiming,
            onChange = { consoleTiming = it; prefs.consoleTiming = it },
          )
        }
      } else null,
    )),
    SettingGroup(SettingsTab.CONSOLE, null, null, listOf(
      SettingItem(R.string.settings_section_network, R.string.settings_network_desc) {
        NetworkSettings(prefs, onInsignia)
      },
    )),

    SettingGroup(SettingsTab.CONTROLS, R.string.settings_section_controls, null, listOf(
      SettingItem(R.string.settings_overlay, R.string.settings_overlay_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_overlay)) },
          supportingContent = { Text(stringResource(R.string.settings_overlay_desc)) },
          modifier = Modifier.clickable(onClick = onEditControls),
        )
      },
      SettingItem(R.string.controllers_title, R.string.controllers_settings_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.controllers_title)) },
          supportingContent = { Text(stringResource(R.string.controllers_settings_desc)) },
          modifier = Modifier.clickable(onClick = onControllers),
        )
      },
    )),

    SettingGroup(SettingsTab.GAMES, R.string.settings_section_games, null, listOf(
      SettingItem(R.string.settings_games_folder_title, null) {
        Column {
          folders.forEachIndexed { index, f ->
            if (index > 0) SettingsDivider()
            ListItem(
              headlineContent = { Text(Storage.folderLabel(context, f)) },
              supportingContent = { Text(Storage.folderPlace(context, f)) },
              trailingContent = if (folders.size > 1) {
                { DeleteButton(onClick = { removing = f }, description = stringResource(R.string.settings_games_folder_remove)) }
              } else {
                null
              },
              modifier = Modifier.clickable {
                replacing = f
                pickFolder.launch(f)
              },
            )
          }
        }
      },
      SettingItem(R.string.settings_games_folder_add, R.string.settings_games_folder_add_desc) {
        ActionItem(
          title = stringResource(R.string.settings_games_folder_add),
          summary = stringResource(R.string.settings_games_folder_add_desc),
          icon = Icons.Filled.Add,
        ) {
          replacing = null
          pickFolder.launch(folders.lastOrNull())
        }
      },
    )),

    SettingGroup(SettingsTab.APP, R.string.settings_section_appearance, null, listOf(
      SettingItem(R.string.settings_theme, null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_theme)) },
          supportingContent = { Text(stringResource(themeLabel(theme))) },
          modifier = Modifier.clickable { themeDialog = true },
        )
      },
    )),
    SettingGroup(SettingsTab.APP, R.string.settings_section_updates, null, listOf(
      SettingItem(R.string.settings_update_check, R.string.settings_update_check_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_update_check)) },
          supportingContent = {
            Text(
              if (updateChecking) stringResource(R.string.settings_update_checking)
              else stringResource(R.string.settings_update_check_desc, Updates.installedVersion(context))
            )
          },
          modifier = Modifier.clickable(enabled = !updateChecking) {
            updateChecking = true
            scope.launch {
              val found = runCatching { Updates.check(context) }
              updateChecking = false
              found.onSuccess { release ->
                if (release != null) {
                  updateRelease = release
                } else {
                  snackbar.notify(scope, context.getString(R.string.update_none, Updates.installedVersion(context)))
                }
              }.onFailure {
                snackbar.notify(
                  scope, context.getString(R.string.update_check_failed, it.message ?: it.javaClass.simpleName),
                )
              }
            }
          },
        )
      },
    )),
    SettingGroup(SettingsTab.APP, R.string.settings_section_logs, null, listOf(
      SettingItem(R.string.settings_export_logs, R.string.settings_export_logs_desc) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_export_logs)) },
          supportingContent = { Text(stringResource(R.string.settings_export_logs_desc)) },
          modifier = Modifier.clickable { pickLogsFolder.launch(null) },
        )
      },
    )),
  )

  // The search closes with back before the screen does.
  BackHandler(enabled = searching) {
    searching = false
    query = ""
  }
  LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
  // A swipe to a tab brings its chip into view.
  LaunchedEffect(pager.currentPage) {
    val visible = chipsState.layoutInfo.visibleItemsInfo
    val page = pager.currentPage
    if (visible.isEmpty() || page <= visible.first().index || page >= visible.last().index) {
      chipsState.animateScrollToItem((page - 1).coerceAtLeast(0))
    }
  }

  SubScreen(
    title = stringResource(R.string.settings_title),
    onBack = onBack,
    snackbar = snackbar,
    actions = {
      IconButton(onClick = {
        if (searching) query = ""
        searching = !searching
      }) {
        Icon(
          if (searching) Icons.Filled.Close else Icons.Filled.Search,
          contentDescription = stringResource(if (searching) R.string.settings_search_close else R.string.settings_search),
        )
      }
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      if (searching) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(searchFocus),
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          placeholder = { Text(stringResource(R.string.settings_search_hint)) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
      }
      val q = query.trim()
      if (searching && q.isNotEmpty()) {
        val results = groups.mapNotNull { group ->
          val found = group.items.filter { matches(context, it, q) }
          if (found.isEmpty()) null else group to found
        }
        Column(
          Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (results.isEmpty()) {
            Text(
              stringResource(R.string.settings_search_none),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
          for ((group, items) in results) {
            GroupCard(group, items)
          }
        }
      } else {
        LazyRow(
          state = chipsState,
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          itemsIndexed(SettingsTab.entries) { index, tab ->
            FilterChip(
              selected = pager.currentPage == index,
              onClick = { scope.launch { pager.animateScrollToPage(index) } },
              label = { Text(stringResource(tab.title)) },
              leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
          }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
          val tab = SettingsTab.entries[page]
          Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            for (group in groups.filter { it.tab == tab }) {
              GroupCard(group, group.items)
            }
            if (tab == SettingsTab.GRAPHICS || tab == SettingsTab.DISPLAY || tab == SettingsTab.CONSOLE) {
              TextButton(onClick = { resetFor = tab }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.settings_reset_tab))
              }
            }
          }
        }
      }
    }
  }

  if (languageDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_language),
      options = LANGUAGES.map { it to languageLabel(it) },
      current = language,
      onPick = { language = it; prefs.language = it; languageDialog = false },
      onDismiss = { languageDialog = false },
    )
  }
  updateRelease?.let { UpdateDialog(it, onDismiss = { updateRelease = null }) }
  if (clockDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_clock),
      options = listOf(false to stringResource(R.string.settings_clock_phone),
                       true to stringResource(R.string.settings_clock_set_pick)),
      current = clockOffset != null,
      onPick = { set ->
        clockDialog = false
        if (set) {
          pickClock(context, dark, System.currentTimeMillis() + (clockOffset ?: 0L)) {
            clockOffset = it - System.currentTimeMillis()
            prefs.clockOffset = clockOffset
          }
        } else {
          clockOffset = null
          prefs.clockOffset = null
        }
      },
      onDismiss = { clockDialog = false },
    )
  }
  if (formatDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_screen_format),
      options = FORMATS.map { it to stringResource(formatLabel(it)) },
      current = format,
      onPick = { format = it; prefs.screenFormat = it; formatDialog = false },
      onDismiss = { formatDialog = false },
    )
  }
  if (cableDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_av_cable),
      options = CABLES.map { it to stringResource(cableLabel(it)) },
      current = avPack,
      onPick = { avPack = it; prefs.avPack = it; cableDialog = false },
      onDismiss = { cableDialog = false },
    )
  }
  if (aspectDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_aspect),
      options = ASPECTS.map { it to stringResource(aspectLabel(it)) },
      current = aspect,
      onPick = { aspect = it; prefs.aspectRatio = it; aspectDialog = false },
      onDismiss = { aspectDialog = false },
    )
  }
  if (upscaleDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_upscale),
      options = UPSCALERS.map { it to stringResource(upscaleLabel(it)) },
      current = upscale,
      onPick = { upscale = it; prefs.upscaleFilter = it; upscaleDialog = false },
      onDismiss = { upscaleDialog = false },
    )
  }
  if (scaleDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_scale),
      options = SCALES.map { it to scaleLabel(it) },
      current = scale,
      onPick = { scale = it; prefs.renderScale = it; scaleDialog = false },
      onDismiss = { scaleDialog = false },
    )
  }
  if (themeDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_theme),
      options = THEMES.map { it to stringResource(themeLabel(it)) },
      current = theme,
      onPick = { AppTheme.set(context, it); themeDialog = false },
      onDismiss = { themeDialog = false },
    )
  }
  resetFor?.let { tab ->
    ConfirmDialog(
      title = stringResource(R.string.settings_reset_title),
      text = stringResource(R.string.settings_reset_text, stringResource(tab.title)),
      confirm = stringResource(R.string.settings_reset_confirm),
      onConfirm = {
        when (tab) {
          SettingsTab.GRAPHICS -> {
            prefs.resetGraphics()
            scale = prefs.renderScale
            fxaa = prefs.fxaa
            upscale = prefs.upscaleFilter
            uncapped = prefs.uncapped
            asyncShaders = prefs.asyncShaders
            prebuildShaders = prefs.prebuildShaders
          }
          SettingsTab.DISPLAY -> {
            prefs.resetDisplay()
            aspect = prefs.aspectRatio
            showFps = prefs.showFps
            showHitches = prefs.showHitches
            format = prefs.screenFormat
            p480 = prefs.video480p
            p720 = prefs.video720p
            i1080 = prefs.video1080i
            pal60 = prefs.pal60
            avPack = prefs.avPack
          }
          SettingsTab.CONSOLE -> {
            prefs.resetConsole()
            language = prefs.language
            clockOffset = prefs.clockOffset
            consoleTiming = prefs.consoleTiming
          }
          else -> Unit
        }
        resetFor = null
      },
      onDismiss = { resetFor = null },
    )
  }
  removing?.let { f ->
    ConfirmDialog(
      title = stringResource(R.string.settings_games_folder_remove),
      text = stringResource(R.string.settings_games_folder_remove_text, Storage.folderPlace(context, f)),
      confirm = stringResource(R.string.settings_games_folder_remove_confirm),
      onConfirm = {
        prefs.gamesFolders = folders - f
        folders = prefs.gamesFolders
        Storage.releasePermission(context, f)
        removing = null
      },
      onDismiss = { removing = null },
    )
  }
}

/** The app's themes: the phone's, light, dark. */
private val THEMES = listOf(AppPrefs.THEME_SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK)

private fun themeLabel(value: String) = when (value) {
  AppPrefs.THEME_LIGHT -> R.string.settings_theme_light
  AppPrefs.THEME_DARK -> R.string.settings_theme_dark
  else -> R.string.settings_theme_system
}

/** The render scales: native, then 2x to 4x. */
internal val SCALES = (1..4).toList()

@Composable
internal fun scaleLabel(value: Int) = if (value == 1) stringResource(R.string.settings_scale_native) else "${value}x"

internal val ASPECTS = listOf("auto", "4:3", "16:9", "stretch")

internal val UPSCALERS = listOf(NativeCoreActivity.UPSCALE_BILINEAR, NativeCoreActivity.UPSCALE_SGSR)
internal val FORMATS = listOf(AppPrefs.FORMAT_NORMAL, AppPrefs.FORMAT_LETTERBOX, AppPrefs.FORMAT_WIDESCREEN)
internal val CABLES = listOf(AppPrefs.AV_HDTV, AppPrefs.AV_STANDARD, AppPrefs.AV_SVIDEO)
internal val LANGUAGES = listOf(AppPrefs.LANG_AUTO) + AppPrefs.XBOX_LANGUAGES

/* Each language in its own words, as the dashboard lists them. */
private val LANGUAGE_NAMES = mapOf(
  "en" to "English",
  "ja" to "日本語",
  "de" to "Deutsch",
  "fr" to "Français",
  "es" to "Español",
  "it" to "Italiano",
  "ko" to "한국어",
  "zh" to "中文（繁體）",
  "pt" to "Português",
)

@Composable
internal fun languageLabel(value: String) =
  if (value == AppPrefs.LANG_AUTO) {
    stringResource(R.string.settings_language_auto, LANGUAGE_NAMES.getValue(AppPrefs.autoLanguage()))
  } else {
    LANGUAGE_NAMES[value] ?: value
  }

/** The console's clock: the phone's time, or the time set by hand it shows now. */
@Composable
private fun clockLabel(offset: Long?) =
  if (offset == null) {
    stringResource(R.string.settings_clock_phone)
  } else {
    val now = System.currentTimeMillis() + offset
    stringResource(
      R.string.settings_clock_set,
      DateUtils.formatDateTime(
        LocalContext.current, now,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
      ),
    )
  }

/**
 * The console's date (2001-2099), then its time, from the given moment (ms), in the phone's own
 * pickers (they fit a landscape screen), light or dark as the app is; onSet gets the moment picked.
 */
private fun pickClock(context: Context, dark: Boolean, from: Long, onSet: (Long) -> Unit) {
  val c = Calendar.getInstance().apply { timeInMillis = from }
  val style = if (dark) R.style.Theme_HakuX_Picker_Dark else R.style.Theme_HakuX_Picker
  val date = DatePickerDialog(context, style, { _, year, month, day ->
    TimePickerDialog(context, style, { _, hour, minute ->
      c.set(year, month, day, hour, minute, 0)
      c.set(Calendar.MILLISECOND, 0)
      onSet(c.timeInMillis)
    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
  }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
  date.datePicker.minDate = GregorianCalendar(2001, Calendar.JANUARY, 1).timeInMillis
  date.datePicker.maxDate = GregorianCalendar(2099, Calendar.DECEMBER, 31).timeInMillis
  date.show()
}

internal fun formatLabel(value: String) = when (value) {
  AppPrefs.FORMAT_LETTERBOX -> R.string.settings_format_letterbox
  AppPrefs.FORMAT_WIDESCREEN -> R.string.settings_format_widescreen
  else -> R.string.settings_format_normal
}

internal fun cableLabel(value: String) = when (value) {
  AppPrefs.AV_STANDARD -> R.string.settings_av_standard
  AppPrefs.AV_SVIDEO -> R.string.settings_av_svideo
  else -> R.string.settings_av_hdtv
}

internal fun upscaleLabel(value: String) = when (value) {
  NativeCoreActivity.UPSCALE_SGSR -> R.string.settings_upscale_sgsr
  else -> R.string.settings_upscale_bilinear
}

internal fun aspectLabel(value: String) = when (value) {
  "4:3" -> R.string.settings_aspect_4_3
  "16:9" -> R.string.settings_aspect_16_9
  "stretch" -> R.string.settings_aspect_stretch
  else -> R.string.settings_aspect_auto
}

/** The driver in use, for the settings' GPU driver item (its version, when its package names one). */
private fun driverLabel(context: android.content.Context): String {
  GpuDriverHelper.init(context)
  if (!GpuDriverHelper.supportsCustomDriverLoading()) {
    return context.getString(R.string.settings_gpu_driver_not_supported)
  }
  val d = GpuDriverHelper.getActiveDriver() ?: return context.getString(R.string.settings_gpu_driver_system)
  val name = d.name ?: context.getString(R.string.settings_gpu_driver_custom)
  return d.driverVersion?.let { context.getString(R.string.settings_gpu_driver_active_version, name, it) } ?: name
}
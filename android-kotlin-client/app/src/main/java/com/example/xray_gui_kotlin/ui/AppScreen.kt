package com.example.xray_gui_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xray_gui_kotlin.model.Profile
import com.example.xray_gui_kotlin.model.RoutingPreset

private enum class SettingsSubPage {
    MAIN,
    LOGS,
}

@Composable
fun AppScreen(
    viewModel: HomeViewModel,
    onRequestVpnPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tabIndex by remember { mutableIntStateOf(0) }
    var settingsSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeUiEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                HomeUiEffect.RequestVpnPermission -> onRequestVpnPermission()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PillBottomBar(
                selectedIndex = tabIndex,
                onSelect = { index ->
                    tabIndex = index
                    if (index != 0) {
                        editingProfileId = null
                    }
                    if (index != 2) {
                        settingsSubPage = SettingsSubPage.MAIN
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tabIndex) {
                0 -> {
                    if (editingProfileId == null) {
                        HomeTab(
                            uiState = uiState,
                            onSelectProfile = viewModel::selectProfile,
                            onOpenProfileEditor = { profileId ->
                                viewModel.selectProfile(profileId)
                                editingProfileId = profileId
                            },
                            onSelectGlobalRoutingPreset = viewModel::setGlobalRoutingPreset,
                            onImportTextChanged = viewModel::onImportTextChanged,
                            onImport = viewModel::importCurrentText,
                            onStart = viewModel::startSelected,
                            onStop = viewModel::stopRuntime,
                        )
                    } else {
                        HomeProfileEditorPage(
                            selectedProfile = uiState.selectedProfile,
                            onBack = { editingProfileId = null },
                            onSaveProfileNode = viewModel::updateSelectedProfileNodeAdvanced,
                        )
                    }
                }

                1 -> ConfigTab(
                    uiState = uiState,
                    onRefreshGeoIp = viewModel::updateGeoData,
                )

                else -> SettingsTab(
                    uiState = uiState,
                    subPage = settingsSubPage,
                    onSubPageChange = { settingsSubPage = it },
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    uiState: HomeUiState,
    onSelectProfile: (String) -> Unit,
    onOpenProfileEditor: (String) -> Unit,
    onSelectGlobalRoutingPreset: (RoutingPreset) -> Unit,
    onImportTextChanged: (String) -> Unit,
    onImport: () -> Unit,
    onStart: (requireVpnPermission: Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val isRunning = uiState.runtimeState == "running" || uiState.runtimeState == "running-dry"
    val isStarting = uiState.runtimeState == "starting"
    val checked = isRunning || isStarting
    val canToggleOn = uiState.selectedProfile != null && !checked
    val canToggleOff = checked

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        item {
            PrimaryControlCard(
                selectedProfileName = uiState.selectedProfile?.name,
                runtimeState = uiState.runtimeState,
                globalRoutingPreset = uiState.globalRoutingPreset,
                checked = checked,
                switchEnabled = canToggleOn || canToggleOff,
                onSelectGlobalRoutingPreset = onSelectGlobalRoutingPreset,
                onToggleVpn = { enabled ->
                    if (enabled && canToggleOn) {
                        onStart(true)
                    }
                    if (!enabled && canToggleOff) {
                        onStop()
                    }
                },
            )
        }

        item {
            NodeListCard(
                profiles = uiState.profiles,
                selectedProfileId = uiState.selectedProfileId,
                onSelectProfile = onSelectProfile,
                onOpenProfileEditor = onOpenProfileEditor,
                importText = uiState.importText,
                onImportTextChanged = onImportTextChanged,
                onImport = onImport,
            )
        }
    }
}

@Composable
private fun PrimaryControlCard(
    selectedProfileName: String?,
    runtimeState: String,
    globalRoutingPreset: RoutingPreset,
    checked: Boolean,
    switchEnabled: Boolean,
    onSelectGlobalRoutingPreset: (RoutingPreset) -> Unit,
    onToggleVpn: (Boolean) -> Unit,
) {
    var showRoutingMenu by remember { mutableStateOf(false) }

    val runtimeLabel = when (runtimeState) {
        "running" -> "已连接"
        "running-dry" -> "演示模式运行中"
        "starting" -> "连接中"
        "stopping" -> "断开中"
        "error" -> "连接异常"
        else -> "未连接"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selectedProfileName ?: "未选择节点",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = runtimeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(0.75f),
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onToggleVpn,
                    enabled = switchEnabled,
                    modifier = Modifier.graphicsLayer(
                        scaleX = 1.2f,
                        scaleY = 1.2f,
                    ),
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { showRoutingMenu = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "分流",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${globalRoutingPreset.label} >",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                DropdownMenu(
                    expanded = showRoutingMenu,
                    onDismissRequest = { showRoutingMenu = false },
                ) {
                    RoutingPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(preset.label)
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.alpha(0.7f),
                                    )
                                }
                            },
                            onClick = {
                                onSelectGlobalRoutingPreset(preset)
                                showRoutingMenu = false
                            },
                        )
                    }
                }
            }

            if (runtimeState == "starting" || runtimeState == "stopping") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NodeListCard(
    profiles: List<Profile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onOpenProfileEditor: (String) -> Unit,
    importText: String,
    onImportTextChanged: (String) -> Unit,
    onImport: () -> Unit,
) {
    var showImportBox by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "节点",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { showImportBox = !showImportBox }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "导入节点",
                    )
                }
            }

            if (showImportBox) {
                OutlinedTextField(
                    value = importText,
                    onValueChange = onImportTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text("粘贴 vless:// 链接，或直接粘贴完整 outbound JSON 配置")
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = onImport) {
                        Text("导入")
                    }
                }
                HorizontalDivider(modifier = Modifier.alpha(0.35f))
            }

            if (profiles.isEmpty()) {
                Text(
                    text = "暂无节点，请先导入节点",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            } else {
                profiles.forEachIndexed { index, profile ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.alpha(0.35f))
                    }
                    NodeRow(
                        profile = profile,
                        selected = profile.id == selectedProfileId,
                        onClick = { onSelectProfile(profile.id) },
                        onInfoClick = { onOpenProfileEditor(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    profile: Profile,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val highlight = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(highlight)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = formatProtocolLabel(profile),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.78f),
            )
        }

        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "编辑节点",
            )
        }
    }
}

private fun formatProtocolLabel(profile: Profile): String {
    val transport = profile.node.network.uppercase()
    val tunnelSecurity = when (profile.node.security.lowercase()) {
        "", "none" -> "TCP"
        else -> profile.node.security.uppercase()
    }
    return "$transport / VLESS $tunnelSecurity"
}

@Composable
private fun ConfigTab(
    uiState: HomeUiState,
    onRefreshGeoIp: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "配置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    ConfigActionRow(
                        label = "GeoIP 数据日期",
                        value = uiState.geoIpVersionDate,
                        showRefreshIcon = true,
                        onActionClick = onRefreshGeoIp,
                    )
                    ConfigActionRow(
                        label = "Xray 版本",
                        value = uiState.xrayVersion,
                        showRefreshIcon = false,
                        onActionClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigActionRow(
    label: String,
    value: String,
    showRefreshIcon: Boolean,
    onActionClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.78f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showRefreshIcon && onActionClick != null) {
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新$label",
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    uiState: HomeUiState,
    subPage: SettingsSubPage,
    onSubPageChange: (SettingsSubPage) -> Unit,
) {
    when (subPage) {
        SettingsSubPage.MAIN -> SettingsMainPage(
            onOpenLogs = { onSubPageChange(SettingsSubPage.LOGS) },
        )

        SettingsSubPage.LOGS -> SettingsLogPage(
            logs = uiState.logs,
            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
        )
    }
}

@Composable
private fun SettingsMainPage(
    onOpenLogs: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )

                    SettingsMenuRow(
                        title = "查看日志",
                        subtitle = null,
                        enabled = true,
                        onClick = onOpenLogs,
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text("作者：中国人", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.74f),
                )
            }
        }
        Text(
            text = ">",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsLogPage(
    logs: List<String>,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryPageHeader(
            title = "日志",
            onBack = onBack,
        )
        LogList(logs = logs)
    }
}

@Composable
private fun HomeProfileEditorPage(
    selectedProfile: Profile?,
    onBack: () -> Unit,
    onSaveProfileNode: (
        profileName: String,
        address: String,
        port: Int,
        uuid: String,
        network: String,
        security: String,
        serverName: String,
        host: String,
        path: String,
        flow: String,
        fingerprint: String,
        mode: String,
        alpnCsv: String,
        xhttpDownloadAddress: String,
        xhttpDownloadPort: Int?,
        xhttpDownloadHost: String,
        xhttpDownloadPath: String,
        xhttpDownloadMode: String,
    ) -> Unit,
) {
    if (selectedProfile == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryPageHeader(
                title = "节点详情",
                onBack = onBack,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("请先在首页选择节点")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryPageHeader(
            title = "节点详情",
            onBack = onBack,
        )
        ProfileEditorContent(
            selected = selectedProfile,
            onSaveProfileNode = onSaveProfileNode,
        )
    }
}

@Composable
private fun SecondaryPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "< 返回",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileEditorContent(
    selected: Profile,
    onSaveProfileNode: (
        profileName: String,
        address: String,
        port: Int,
        uuid: String,
        network: String,
        security: String,
        serverName: String,
        host: String,
        path: String,
        flow: String,
        fingerprint: String,
        mode: String,
        alpnCsv: String,
        xhttpDownloadAddress: String,
        xhttpDownloadPort: Int?,
        xhttpDownloadHost: String,
        xhttpDownloadPath: String,
        xhttpDownloadMode: String,
    ) -> Unit,
) {
    var profileName by remember(selected.id) { mutableStateOf(selected.name) }
    var address by remember(selected.id) { mutableStateOf(selected.node.address) }
    var portText by remember(selected.id) { mutableStateOf(selected.node.port.toString()) }
    var uuid by remember(selected.id) { mutableStateOf(selected.node.id) }
    var network by remember(selected.id) { mutableStateOf(selected.node.network) }
    var security by remember(selected.id) { mutableStateOf(selected.node.security) }
    var serverName by remember(selected.id) { mutableStateOf(selected.node.serverName) }
    var host by remember(selected.id) { mutableStateOf(selected.node.host) }
    var path by remember(selected.id) { mutableStateOf(selected.node.path) }
    var flow by remember(selected.id) { mutableStateOf(selected.node.flow) }
    var fingerprint by remember(selected.id) { mutableStateOf(selected.node.fingerprint) }
    var mode by remember(selected.id) { mutableStateOf(selected.node.mode) }
    var alpnCsv by remember(selected.id) { mutableStateOf(selected.node.alpn.joinToString(",")) }
    var xhttpDownloadAddress by remember(selected.id) { mutableStateOf(selected.node.downloadSettings?.address.orEmpty()) }
    var xhttpDownloadPortText by remember(selected.id) { mutableStateOf(selected.node.downloadSettings?.port?.toString().orEmpty()) }
    var xhttpDownloadHost by remember(selected.id) { mutableStateOf(selected.node.downloadSettings?.host.orEmpty()) }
    var xhttpDownloadPath by remember(selected.id) { mutableStateOf(selected.node.downloadSettings?.path.orEmpty()) }
    var xhttpDownloadMode by remember(selected.id) { mutableStateOf(selected.node.downloadSettings?.mode.orEmpty()) }

    var profileNameError by remember(selected.id) { mutableStateOf<String?>(null) }
    var addressError by remember(selected.id) { mutableStateOf<String?>(null) }
    var portError by remember(selected.id) { mutableStateOf<String?>(null) }
    var uuidError by remember(selected.id) { mutableStateOf<String?>(null) }
    var networkError by remember(selected.id) { mutableStateOf<String?>(null) }
    var securityError by remember(selected.id) { mutableStateOf<String?>(null) }
    var xhttpDownloadError by remember(selected.id) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("节点编辑", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            profileNameError = null
                        },
                        label = { Text("节点名称") },
                        isError = profileNameError != null,
                        supportingText = { profileNameError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            addressError = null
                        },
                        label = { Text("服务器地址") },
                        isError = addressError != null,
                        supportingText = { addressError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it
                            portError = null
                        },
                        label = { Text("端口") },
                        isError = portError != null,
                        supportingText = { portError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uuid,
                        onValueChange = {
                            uuid = it
                            uuidError = null
                        },
                        label = { Text("UUID") },
                        isError = uuidError != null,
                        supportingText = { uuidError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = network,
                        onValueChange = {
                            network = it
                            networkError = null
                        },
                        label = { Text("传输类型 (network)") },
                        isError = networkError != null,
                        supportingText = { networkError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = security,
                        onValueChange = {
                            security = it
                            securityError = null
                        },
                        label = { Text("安全类型 (security)") },
                        isError = securityError != null,
                        supportingText = { securityError?.let { msg -> Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("SNI / Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Path") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = mode,
                        onValueChange = { mode = it },
                        label = { Text("XHTTP Mode") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = flow,
                        onValueChange = { flow = it },
                        label = { Text("Flow") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = fingerprint,
                        onValueChange = { fingerprint = it },
                        label = { Text("Fingerprint") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = alpnCsv,
                        onValueChange = { alpnCsv = it },
                        label = { Text("ALPN (逗号分隔)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(modifier = Modifier.alpha(0.35f))
                    Text("XHTTP 下载通道（可选）", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = xhttpDownloadAddress,
                        onValueChange = {
                            xhttpDownloadAddress = it
                            xhttpDownloadError = null
                        },
                        label = { Text("下载 address") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = xhttpDownloadPortText,
                        onValueChange = {
                            xhttpDownloadPortText = it
                            xhttpDownloadError = null
                        },
                        label = { Text("下载 port") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = xhttpDownloadHost,
                        onValueChange = {
                            xhttpDownloadHost = it
                            xhttpDownloadError = null
                        },
                        label = { Text("下载 host") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = xhttpDownloadPath,
                        onValueChange = {
                            xhttpDownloadPath = it
                            xhttpDownloadError = null
                        },
                        label = { Text("下载 path") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = xhttpDownloadMode,
                        onValueChange = {
                            xhttpDownloadMode = it
                            xhttpDownloadError = null
                        },
                        label = { Text("下载 mode") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (xhttpDownloadError != null) {
                        Text(
                            text = xhttpDownloadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = {
                            val port = portText.trim().toIntOrNull()
                            val downloadPort = xhttpDownloadPortText.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
                            val hasDownloadInputs = listOf(
                                xhttpDownloadAddress,
                                xhttpDownloadPortText,
                                xhttpDownloadHost,
                                xhttpDownloadPath,
                                xhttpDownloadMode,
                            ).any { it.trim().isNotEmpty() }
                            val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                            profileNameError = if (profileName.trim().isEmpty()) "节点名称不能为空" else null
                            addressError = if (address.trim().isEmpty()) "服务器地址不能为空" else null
                            portError = if (port == null || port !in 1..65535) "端口必须是 1-65535 的数字" else null
                            uuidError = if (!uuidPattern.matches(uuid.trim())) "UUID 格式无效" else null
                            networkError = if (network.trim().isEmpty()) "network 不能为空" else null
                            securityError = if (security.trim().isEmpty()) "security 不能为空" else null
                            xhttpDownloadError = when {
                                !hasDownloadInputs -> null
                                xhttpDownloadAddress.trim().isEmpty() -> "下载通道 address 不能为空"
                                downloadPort == null || downloadPort !in 1..65535 -> "下载通道端口必须是 1-65535 的数字"
                                xhttpDownloadPath.trim().isEmpty() -> "下载通道 path 不能为空"
                                else -> null
                            }

                            if (profileNameError == null && addressError == null && portError == null && uuidError == null && networkError == null && securityError == null && xhttpDownloadError == null && port != null) {
                                onSaveProfileNode(
                                    profileName,
                                    address,
                                    port,
                                    uuid,
                                    network,
                                    security,
                                    serverName,
                                    host,
                                    path,
                                    flow,
                                    fingerprint,
                                    mode,
                                    alpnCsv,
                                    xhttpDownloadAddress,
                                    if (hasDownloadInputs) downloadPort else null,
                                    xhttpDownloadHost,
                                    xhttpDownloadPath,
                                    xhttpDownloadMode,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存节点配置")
                    }
                }
            }
        }
    }
}

@Composable
private fun LogList(logs: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (logs.isEmpty()) {
            item {
                Text("日志将在 runtime 启动后显示")
            }
        } else {
            items(logs) { line ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PillBottomBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val items = listOf("首页", "配置", "设置")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

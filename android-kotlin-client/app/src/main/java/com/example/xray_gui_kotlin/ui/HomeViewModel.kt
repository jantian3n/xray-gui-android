package com.example.xray_gui_kotlin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xray_gui_kotlin.config.XrayConfigCompiler
import com.example.xray_gui_kotlin.data.ProfileSnapshot
import com.example.xray_gui_kotlin.data.ProfileStore
import com.example.xray_gui_kotlin.model.Profile
import com.example.xray_gui_kotlin.model.RoutingPreset
import com.example.xray_gui_kotlin.model.RuntimeMode
import com.example.xray_gui_kotlin.model.XhttpDownloadSettings
import com.example.xray_gui_kotlin.parser.NodeImporter
import com.example.xray_gui_kotlin.runtime.GeoDataUpdater
import com.example.xray_gui_kotlin.runtime.GomobileXrayBridge
import com.example.xray_gui_kotlin.runtime.RuntimeLogBus
import com.example.xray_gui_kotlin.runtime.XrayRuntimeController
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class HomeUiState(
    val importText: String = "",
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val globalRoutingPreset: RoutingPreset = RoutingPreset.CN_DIRECT,
    val runtimeState: String = "idle",
    val geoIpVersionDate: String = "未更新",
    val xrayVersion: String = "未检测到",
    val xrayVersionDate: String = "未知",
    val logs: List<String> = emptyList(),
) {
    val selectedProfile: Profile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }
}

sealed interface HomeUiEffect {
    data class Message(val text: String) : HomeUiEffect
    data object RequestVpnPermission : HomeUiEffect
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val profileStore = ProfileStore(application)
    private val nodeImporter = NodeImporter()
    private val compiler = XrayConfigCompiler()
    private val runtimeController = XrayRuntimeController(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HomeUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<HomeUiEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            profileStore.observe().collect { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        profiles = snapshot.profiles,
                        selectedProfileId = snapshot.selectedProfileId,
                        globalRoutingPreset = snapshot.globalRoutingPreset,
                    )
                }
            }
        }

        viewModelScope.launch {
            RuntimeLogBus.state.collect { state ->
                _uiState.update { it.copy(runtimeState = state) }
            }
        }

        viewModelScope.launch {
            RuntimeLogBus.logs.collect { line ->
                _uiState.update { state ->
                    val merged = listOf(line) + state.logs
                    state.copy(logs = merged.take(200))
                }
            }
        }

        refreshConfigInfo()
    }

    fun onImportTextChanged(value: String) {
        _uiState.update { it.copy(importText = value) }
    }

    fun importCurrentText() {
        val raw = _uiState.value.importText.trim()
        if (raw.isEmpty()) {
            emitMessage("请输入 vless:// 链接")
            return
        }

        if (nodeImporter.looksLikePatch(raw)) {
            val selected = _uiState.value.selectedProfile
            if (selected == null) {
                emitMessage("请先选中一个节点，再应用 split patch")
                return
            }

            runCatching {
                val patchedNode = nodeImporter.applyPatch(selected.node, raw)
                selected.copy(node = patchedNode)
            }.onSuccess { patchedProfile ->
                viewModelScope.launch {
                    val updated = _uiState.value.profiles.map {
                        if (it.id == patchedProfile.id) patchedProfile else it
                    }
                    saveSnapshot(
                        profiles = updated,
                        selectedProfileId = patchedProfile.id,
                    )
                    _uiState.update { it.copy(importText = "") }
                    emitMessage("split patch 已应用")
                }
            }.onFailure { error ->
                emitMessage(error.message ?: "补丁应用失败")
            }
            return
        }

        runCatching {
            val node = nodeImporter.parseNode(raw)
            Profile.fromNode(node)
        }.onSuccess { profile ->
            viewModelScope.launch {
                val existing = _uiState.value.profiles.toMutableList()
                existing.removeAll { it.id == profile.id }
                existing.add(0, profile)
                saveSnapshot(
                    profiles = existing,
                    selectedProfileId = profile.id,
                )
                _uiState.update { it.copy(importText = "") }
                emitMessage("节点导入成功")
            }
        }.onFailure { error ->
            emitMessage(error.message ?: "导入失败")
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            saveSnapshot(
                profiles = _uiState.value.profiles,
                selectedProfileId = profileId,
            )
        }
    }

    fun cycleGlobalRoutingPreset() {
        val entries = RoutingPreset.entries
        val current = _uiState.value.globalRoutingPreset
        val nextIndex = (entries.indexOf(current) + 1) % entries.size
        val nextPreset = entries[nextIndex]
        setGlobalRoutingPreset(nextPreset)
    }

    fun setGlobalRoutingPreset(preset: RoutingPreset) {
        if (preset == _uiState.value.globalRoutingPreset) {
            return
        }
        _uiState.update { it.copy(globalRoutingPreset = preset) }

        viewModelScope.launch {
            saveSnapshot(
                profiles = _uiState.value.profiles,
                selectedProfileId = _uiState.value.selectedProfileId,
                globalRoutingPreset = preset,
            )
        }
        emitMessage("全局分流已切换为 ${preset.label}")
    }

    fun updateSelectedRoutingPreset(preset: RoutingPreset) {
        val selected = _uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            val updated = _uiState.value.profiles.map {
                if (it.id == selected.id) it.copy(routingPreset = preset) else it
            }
            saveSnapshot(
                profiles = updated,
                selectedProfileId = selected.id,
            )
            emitMessage("路由策略已更新为 ${preset.label}")
        }
    }

    fun updateSelectedRuntimeMode(mode: RuntimeMode) {
        val selected = _uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            val updated = _uiState.value.profiles.map {
                if (it.id == selected.id) it.copy(runtimeMode = mode) else it
            }
            saveSnapshot(
                profiles = updated,
                selectedProfileId = selected.id,
            )
            emitMessage("运行模式已更新为 ${mode.label}")
        }
    }

    fun updateSelectedProfileNode(
        profileName: String,
        address: String,
        port: Int,
        uuid: String,
        network: String,
        security: String,
        serverName: String,
        host: String,
        path: String,
    ) {
        updateSelectedProfileNodeAdvanced(
            profileName = profileName,
            address = address,
            port = port,
            uuid = uuid,
            network = network,
            security = security,
            serverName = serverName,
            host = host,
            path = path,
            flow = "",
            fingerprint = "",
            mode = "",
            alpnCsv = "",
            xhttpDownloadAddress = "",
            xhttpDownloadPort = null,
            xhttpDownloadHost = "",
            xhttpDownloadPath = "",
            xhttpDownloadMode = "",
        )
    }

    fun updateSelectedProfileNodeAdvanced(
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
    ) {
        val selected = _uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            val alpn = alpnCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val hasDownloadInputs = listOf(
                xhttpDownloadAddress,
                xhttpDownloadHost,
                xhttpDownloadPath,
                xhttpDownloadMode,
            ).any { it.trim().isNotEmpty() } || xhttpDownloadPort != null

            val updatedDownloadSettings = if (hasDownloadInputs) {
                val base = selected.node.downloadSettings ?: XhttpDownloadSettings(
                    address = xhttpDownloadAddress.trim(),
                    port = xhttpDownloadPort ?: 443,
                )
                base.copy(
                    address = xhttpDownloadAddress.trim(),
                    port = xhttpDownloadPort ?: base.port,
                    host = xhttpDownloadHost.trim(),
                    path = xhttpDownloadPath.trim(),
                    mode = xhttpDownloadMode.trim(),
                )
            } else {
                null
            }

            val updatedProfile = selected.copy(
                name = profileName.trim(),
                node = selected.node.copy(
                    name = profileName.trim(),
                    address = address.trim(),
                    port = port,
                    id = uuid.trim(),
                    network = network.trim(),
                    security = security.trim(),
                    serverName = serverName.trim(),
                    host = host.trim(),
                    path = path.trim(),
                    flow = flow.trim(),
                    fingerprint = fingerprint.trim(),
                    mode = mode.trim(),
                    alpn = alpn,
                    downloadSettings = updatedDownloadSettings,
                ),
            )

            val updatedProfiles = _uiState.value.profiles.map {
                if (it.id == selected.id) updatedProfile else it
            }

            saveSnapshot(
                profiles = updatedProfiles,
                selectedProfileId = selected.id,
            )
            emitMessage("节点配置已保存")
        }
    }

    fun startSelected(requireVpnPermission: Boolean) {
        val profile = _uiState.value.selectedProfile
        if (profile == null) {
            emitMessage("请先选择一个节点")
            return
        }

        if (profile.runtimeMode.name == "VPN" && requireVpnPermission) {
            _effects.tryEmit(HomeUiEffect.RequestVpnPermission)
            return
        }

        runCatching {
            val hasGeoData = GeoDataUpdater.missingFiles(getApplication()).isEmpty()
            val profileForRuntime = profile.copy(routingPreset = _uiState.value.globalRoutingPreset)
            val config = compiler.compile(profileForRuntime, hasGeoData = hasGeoData)
            runtimeController.start(profileForRuntime, config)
            if (!hasGeoData) {
                emitMessage("首次启动使用 bootstrap 联网规则；geodata 将在后台刷新")
            }
        }.onFailure { error ->
            emitMessage(error.message ?: "启动失败")
        }
    }

    fun stopRuntime() {
        runtimeController.stop()
    }

    fun updateGeoData() {
        runtimeController.updateGeoData()
        refreshConfigInfo()
        emitMessage("已触发 geodata 更新")
    }

    fun updateXrayCoreToLatest() {
        viewModelScope.launch {
            val currentVersion = GomobileXrayBridge.createOrNull()?.version().orEmpty()
            val latestVersion = fetchLatestXrayVersionTag()

            when {
                latestVersion.isNullOrBlank() -> {
                    emitMessage("未能获取 Xray 最新版本信息")
                }
                currentVersion.contains(latestVersion, ignoreCase = true) -> {
                    emitMessage("当前已是最新 Xray 版本: $latestVersion")
                }
                else -> {
                    emitMessage("检测到最新版本 $latestVersion。当前核心随 APK 内置，请更新应用包完成升级")
                }
            }

            refreshConfigInfo()
        }
    }

    private fun emitMessage(message: String) {
        _effects.tryEmit(HomeUiEffect.Message(message))
    }

    private suspend fun saveSnapshot(
        profiles: List<Profile>,
        selectedProfileId: String?,
        globalRoutingPreset: RoutingPreset = _uiState.value.globalRoutingPreset,
    ) {
        profileStore.save(
            ProfileSnapshot(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                globalRoutingPreset = globalRoutingPreset,
            ),
        )
    }

    private fun refreshConfigInfo() {
        val app = getApplication<Application>()
        val geoIpDate = formatGeoIpDate(GeoDataUpdater.lastUpdateMillis(app))

        val xrayVersionRaw = GomobileXrayBridge.createOrNull()?.version().orEmpty()
        val xrayVersion = xrayVersionRaw.ifBlank { "未检测到" }
        val xrayVersionDate = parseDateFromVersion(xrayVersionRaw) ?: "未知"

        _uiState.update {
            it.copy(
                geoIpVersionDate = geoIpDate,
                xrayVersion = xrayVersion,
                xrayVersionDate = xrayVersionDate,
            )
        }
    }

    private fun formatGeoIpDate(timestamp: Long?): String {
        if (timestamp == null) {
            return "未更新"
        }
        if (timestamp <= 0L) {
            return "内置 bootstrap"
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun parseDateFromVersion(version: String): String? {
        if (version.isBlank()) {
            return null
        }

        val withSeparator = Regex("""(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})""").find(version)
        if (withSeparator != null) {
            val year = withSeparator.groupValues[1]
            val month = withSeparator.groupValues[2].padStart(2, '0')
            val day = withSeparator.groupValues[3].padStart(2, '0')
            return "$year-$month-$day"
        }

        val compact = Regex("""(20\d{2})(\d{2})(\d{2})""").find(version)
        if (compact != null) {
            val year = compact.groupValues[1]
            val month = compact.groupValues[2]
            val day = compact.groupValues[3]
            return "$year-$month-$day"
        }

        return null
    }

    private suspend fun fetchLatestXrayVersionTag(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("https://api.github.com/repos/XTLS/Xray-core/releases/latest")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("tag_name").ifBlank { null }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

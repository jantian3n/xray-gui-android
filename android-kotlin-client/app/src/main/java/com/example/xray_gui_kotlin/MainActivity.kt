package com.example.xray_gui_kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.xray_gui_kotlin.ui.AppScreen
import com.example.xray_gui_kotlin.ui.HomeViewModel

class MainActivity : ComponentActivity() {
    private val appTypography = Typography()

    private val appShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

    private val appLightColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF0057D9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE1FF),
        onPrimaryContainer = Color(0xFF00174A),
        secondary = Color(0xFF2D5B87),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD1E4FF),
        onSecondaryContainer = Color(0xFF001C35),
        tertiary = Color(0xFF476443),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC9EAC0),
        onTertiaryContainer = Color(0xFF052108),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        background = Color(0xFFF8F9FF),
        onBackground = Color(0xFF171B24),
        surface = Color(0xFFF8F9FF),
        onSurface = Color(0xFF171B24),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
    )

    private val appDarkColorScheme: ColorScheme = darkColorScheme(
        primary = Color(0xFFB8C4FF),
        onPrimary = Color(0xFF002A77),
        primaryContainer = Color(0xFF003EA8),
        onPrimaryContainer = Color(0xFFDCE1FF),
        secondary = Color(0xFFA2C9F9),
        onSecondary = Color(0xFF003257),
        secondaryContainer = Color(0xFF114A6F),
        onSecondaryContainer = Color(0xFFD1E4FF),
        tertiary = Color(0xFFADD5A4),
        onTertiary = Color(0xFF19361A),
        tertiaryContainer = Color(0xFF304D2F),
        onTertiaryContainer = Color(0xFFC9EAC0),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        background = Color(0xFF0F131B),
        onBackground = Color(0xFFE1E2EC),
        surface = Color(0xFF0F131B),
        onSurface = Color(0xFFE1E2EC),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
    )

    private val viewModel: HomeViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = VpnService.prepare(this) == null
        if (granted) {
            viewModel.startSelected(requireVpnPermission = false)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(this)
                darkTheme -> appDarkColorScheme
                else -> appLightColorScheme
            }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = appTypography,
                shapes = appShapes,
            ) {
                Surface {
                    AppScreen(
                        viewModel = viewModel,
                        onRequestVpnPermission = {
                            val intent = VpnService.prepare(this)
                            if (intent == null) {
                                viewModel.startSelected(requireVpnPermission = false)
                            } else {
                                vpnPermissionLauncher.launch(intent)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

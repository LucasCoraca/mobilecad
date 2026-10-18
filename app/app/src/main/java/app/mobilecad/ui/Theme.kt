package app.mobilecad.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width

/** MobileCAD palette (blue / red from the logo, neutral greys); [dark] switches every token to its dark
 *  counterpart (Preferences ▸ Theme), and the composables reading them recompose. */
object OS {
    var dark by mutableStateOf(false)
    private fun c(light: Long, dark: Long) = if (this.dark) Color(dark) else Color(light)

    val Primary get() = c(0xFF2A6FC9, 0xFF2F6FD6); val BlueMed get() = c(0xFF418FDE, 0xFF5AB4EA); val Cloud get() = c(0xFF9CC6EF, 0xFF2B5379)
    val Navy get() = c(0xFF1B2733, 0xFFE3E9EF); val Steel get() = c(0xFF606A73, 0xFFA3AEB8); val Aluminum get() = c(0xFF9AA5AF, 0xFF6F7A84)
    val Red get() = c(0xFFE0403F, 0xFFF0714A); val Green get() = c(0xFF47AB72, 0xFF5BC487)
    val Metal4 get() = c(0xFFD3D7D9, 0xFF3A4149); val Metal5 get() = c(0xFFE9EBEC, 0xFF2A3037); val Metal6 get() = c(0xFFF4F5F6, 0xFF22272D)
    val ToolbarBg get() = c(0xFFF6F8FB, 0xFF1D2329); val ToolbarDiv get() = c(0xFFD3DEEA, 0xFF2E4A66); val ToolPanel get() = c(0xFFE2E8EF, 0xFF26303A)
    val TabInactive get() = c(0xFFF9F9F9, 0xFF181C20); val TabDivider get() = c(0xFFEFEFEF, 0xFF2A2F35); val ChildBg get() = c(0xFFF5F5F6, 0xFF1F2328)
    /** The document title bar. */
    val TopBar get() = c(0xFFFFFFFF, 0xFF1C2026)
    /** Panels, dialogs, the tree (white on light). */
    val Surface get() = c(0xFFFFFFFF, 0xFF181B1F)
    /** Highlighted rows / toggled buttons, error and success banners. */
    val SelBg get() = c(0xFFDCE9F8, 0xFF23476B); val ErrBg get() = c(0xFFFDECEA, 0xFF4A2620); val OkBg get() = c(0xFFE6F4EC, 0xFF1F3D2C)
}

/** Window size class, updated from the root layout (phones, folded foldables and split screen are compact): side panels
 *  become full-width bottom sheets, the feature list a drawer, panel heights follow the screen. */
object Screen {
    var compact by mutableStateOf(false)
    var width by mutableStateOf(1000.dp)
    var height by mutableStateOf(800.dp)
    /** A floating panel's width: its tablet width, or the full width on a compact screen. */
    fun panelWidth(w: Dp): Modifier = if (compact) Modifier.fillMaxWidth() else Modifier.width(w)
    /** A panel's scrollable height: at most its tablet height and 45% of the screen (phones in landscape are short). */
    fun panelHeight(h: Dp): Dp = minOf(h, height * (if (compact) 0.42f else 0.6f))
}

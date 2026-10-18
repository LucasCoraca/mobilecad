package app.mobilecad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Numeric keypad (replaces the system keyboard for quantities).
 * Produces FreeCAD expression text: "25 mm", "2 * (10 mm + 5 mm)", "45 °".
 */
@Composable
fun Numpad(title: String, initial: String, units: List<String>, onAccept: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val parsed = remember(initial) { splitUnit(initial, units) }
    var text by remember(initial) { mutableStateOf(parsed.first) }
    var unit by remember(initial) { mutableStateOf(parsed.second ?: units.firstOrNull() ?: "") }
    var fresh by remember(initial) { mutableStateOf(true) }           // first key replaces the value
    var unitMenu by remember { mutableStateOf(false) }
    fun type(s: String) { text = if (fresh) s else text + s; fresh = false }
    fun result(): String {
        val t = text.trim(); if (t.isEmpty()) return initial
        val hasUnit = units.any { t.endsWith(it) } || t.any { it.isLetter() }
        return if (hasUnit || unit.isEmpty()) t else "$t $unit"
    }
    // key height follows the screen so the pad fits in landscape on phones (5 rows + the value field)
    val keyH = ((Screen.height - 150.dp) / 5).coerceIn(36.dp, 52.dp)
    Column(modifier.then(if (Screen.compact) Modifier.widthIn(max = 420.dp).fillMaxWidth() else Modifier.width(320.dp)).shadow(10.dp, RoundedCornerShape(8.dp)).background(OS.Surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text(title, color = OS.Steel, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, OS.BlueMed, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text.ifEmpty { " " }, Modifier.weight(1f), fontSize = 22.sp, color = if (fresh) OS.Aluminum else OS.Navy, maxLines = 1)
            if (units.isNotEmpty()) Box {
                Text(unit, Modifier.clickable { unitMenu = true }.background(OS.Metal5, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = OS.Primary, fontWeight = FontWeight.Medium)
                DropdownMenu(unitMenu, { unitMenu = false }) { units.forEach { u -> DropdownMenuItem({ Text(u) }, { unit = u; unitMenu = false }) } }
            }
        }
        Row {
            Column(Modifier.weight(3f)) {
                listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf("±", "0", ".")).forEach { row ->
                    Row { row.forEach { k -> Key(k, Modifier.weight(1f), keyH) {
                        when (k) { "±" -> { text = if (text.startsWith("-")) text.drop(1) else "-$text"; fresh = false }; else -> type(k) } } } }
                }
            }
            Column(Modifier.weight(1f)) {
                Key("⌫", Modifier.fillMaxWidth(), keyH, icon = true) { text = if (fresh) "" else text.dropLast(1); fresh = false }
                Key("+", Modifier.fillMaxWidth(), keyH, accent = true) { type(" + ") }
                Key("−", Modifier.fillMaxWidth(), keyH, accent = true) { type(" - ") }
                Row { Key("×", Modifier.weight(1f), keyH, accent = true) { type(" * ") }; Key("÷", Modifier.weight(1f), keyH, accent = true) { type(" / ") } }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Key("(", Modifier.weight(1f), keyH, accent = true) { type("(") }; Key(")", Modifier.weight(1f), keyH, accent = true) { type(")") }
            Key("C", Modifier.weight(1f), keyH, accent = true) { text = ""; fresh = false }
            Box(Modifier.weight(1f).height(keyH).padding(3.dp).background(OS.Metal5, RoundedCornerShape(6.dp)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Cancel", tint = OS.Steel)
            }
            Box(Modifier.weight(1f).height(keyH).padding(3.dp).background(OS.Primary, RoundedCornerShape(6.dp)).clickable { onAccept(result()) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Accept", tint = Color.White)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, h: androidx.compose.ui.unit.Dp = 52.dp, accent: Boolean = false, icon: Boolean = false, onClick: () -> Unit) {
    Box(modifier.height(h).padding(3.dp).background(if (accent) OS.ToolbarBg else OS.Metal6, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (icon) Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace", tint = OS.Steel)
        else Text(label, fontSize = 20.sp, color = if (accent) OS.Primary else OS.Navy)
    }
}

private fun splitUnit(v: String, units: List<String>): Pair<String, String?> {
    val t = v.trim(); val u = units.sortedByDescending { it.length }.firstOrNull { t.endsWith(" $it") || t.endsWith(it) && t.dropLast(it.length).trim().toDoubleOrNull() != null }
    return if (u != null) t.removeSuffix(u).trim() to u else t to null
}

val LENGTH_UNITS = listOf("mm", "cm", "m", "µm", "in", "ft")
val ANGLE_UNITS = listOf("°", "rad")

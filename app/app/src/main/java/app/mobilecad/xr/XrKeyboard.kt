package app.mobilecad.xr

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.ui.OS

/**
 * The headset's keyboard. Android's own keyboard does not come up on the private display MobileCAD's interface runs on,
 * so while a text field there has focus this one is shown; its keys go to that field through the field's own input
 * connection, as a system keyboard's would (text, backspace, the field's action / new line).
 */
class XrKeyboard {
    private var editor: View? = null
    private var ic: InputConnection? = null
    private var info = EditorInfo()

    /** The focused text field, or null: the keyboard is shown while there is one. */
    fun attach(view: View?) {
        if (view === editor) return
        editor = view; info = EditorInfo(); ic = view?.onCreateInputConnection(info)
    }
    val attached get() = editor != null

    private fun conn(): InputConnection? = ic ?: editor?.onCreateInputConnection(info).also { ic = it }
    fun type(s: String) { if (conn()?.commitText(s, 1) != true) { ic = null; conn()?.commitText(s, 1) } }
    // (Compose's text fields take edits, not raw key events, from an input connection)
    fun backspace() {
        val c = conn() ?: return
        val sel = c.getSelectedText(0)
        if (!sel.isNullOrEmpty()) c.commitText("", 1) else c.deleteSurroundingText(1, 0)
    }
    fun enter() {
        val multiline = info.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (multiline) type("\n")
        else conn()?.performEditorAction(if (action == EditorInfo.IME_ACTION_UNSPECIFIED || action == EditorInfo.IME_ACTION_NONE) EditorInfo.IME_ACTION_DONE else action)
    }
    private fun moveCursor(d: Int) {
        val c = conn() ?: return
        val before = c.getTextBeforeCursor(100000, 0)?.length ?: return; val after = c.getTextAfterCursor(100000, 0)?.length ?: 0
        val p = (before + d).coerceIn(0, before + after); c.setSelection(p, p)
    }
    fun left() = moveCursor(-1)
    fun right() = moveCursor(1)
    fun close() { editor?.clearFocus(); attach(null) }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        var shift by remember { mutableStateOf(false) }
        var symbols by remember { mutableStateOf(false) }
        val rows = if (symbols) listOf("1234567890", "@#$%&*-+=/", "()[]{}<>_\\", ".,:;!?'\"~")
                   else listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        Column(modifier.shadow(8.dp, RoundedCornerShape(10.dp)).background(OS.Metal5, RoundedCornerShape(10.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (i == 3 && !symbols) Key(if (shift) "⇧" else "⇪", 1.5f, active = shift) { shift = !shift }
                    r.forEach { ch -> val c = if (shift && !symbols) ch.uppercaseChar() else ch
                        Key(c.toString()) { type(c.toString()); if (shift) shift = false } }
                    if (i == 3) Key("⌫", 1.5f) { backspace() }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Key(if (symbols) "abc" else "?123", 1.5f) { symbols = !symbols }
                Key("◀", 1f) { left() }
                Key("space", 4f) { type(" ") }
                Key("▶", 1f) { right() }
                Key("⏎", 1.5f, primary = true) { enter() }
                Key("✕", 1f) { close() }
            }
        }
    }

    @Composable
    private fun RowScope.Key(label: String, weight: Float = 1f, active: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
        Box(Modifier.weight(weight).fillMaxHeight()
            .background(if (primary || active) OS.Primary else OS.Surface, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Text(label, color = if (primary || active) androidx.compose.ui.graphics.Color.White else OS.Navy, fontSize = if (label.length > 1) 13.sp else 17.sp)
        }
    }
}

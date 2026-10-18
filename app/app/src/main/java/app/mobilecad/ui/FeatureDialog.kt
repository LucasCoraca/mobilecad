package app.mobilecad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.FcProperty
import org.json.JSONArray

/** The dialog's value editors, provided by the shell: the numpad (numbers with units or =expressions), a text box,
 *  a file picker and a chooser. */
class DialogEditors(
    val number: (title: String, initial: String, units: List<String>, accept: (String) -> Unit) -> Unit,
    val text: (title: String, initial: String, multiline: Boolean, accept: (String) -> Unit) -> Unit,
    val file: (FcProperty) -> Unit,
    val choose: (title: String, options: List<Pair<String, String>>, accept: (String) -> Unit) -> Unit,
)

/**
 * Feature dialog generated from a FreeCAD object's live properties.
 * Primary rows + visibility rules mirror FreeCAD's task panels (e.g. TaskPadPocketParameters); everything else is under "More".
 */
object DialogSpec {
    private val primary = mapOf(
        // FEM solvers (CalculiX / Elmer / Z88 ...): the analysis setup first, the many solver controls under More options
        "Fem::FemSolverObjectPython" to listOf("AnalysisType", "SimulationType", "GeometricalNonlinearity", "MaterialNonlinearity",
            "ThermoMechSteadyState", "TimeInitialStep", "TimeEnd", "EigenmodesCount", "WorkingDirectory"),
        // primitives: FreeCAD's task dialog order (Length, Width, Height ...), not alphabetical
        "Part::Box" to listOf("Length", "Width", "Height"), "PartDesign::AdditiveBox" to listOf("Length", "Width", "Height"),
        "PartDesign::SubtractiveBox" to listOf("Length", "Width", "Height"),
        "Part::Cylinder" to listOf("Radius", "Height", "Angle"), "PartDesign::AdditiveCylinder" to listOf("Radius", "Height", "Angle"),
        "PartDesign::SubtractiveCylinder" to listOf("Radius", "Height", "Angle"),
        "Part::Cone" to listOf("Radius1", "Radius2", "Height", "Angle"), "PartDesign::AdditiveCone" to listOf("Radius1", "Radius2", "Height", "Angle"),
        "Part::Sphere" to listOf("Radius", "Angle1", "Angle2", "Angle3"), "PartDesign::AdditiveSphere" to listOf("Radius", "Angle1", "Angle2", "Angle3"),
        "Part::Torus" to listOf("Radius1", "Radius2", "Angle1", "Angle2", "Angle3"), "PartDesign::AdditiveTorus" to listOf("Radius1", "Radius2", "Angle1", "Angle2", "Angle3"),
        "Part::Tube" to listOf("OuterRadius", "InnerRadius", "Height"),
        "PartDesign::Pad" to listOf("Profile", "Type", "Length", "Length2", "UpToFace", "UpToShape", "Offset", "Midplane", "Reversed", "TaperAngle", "Refine"),
        "PartDesign::Pocket" to listOf("Profile", "Type", "Length", "Length2", "UpToFace", "UpToShape", "Offset", "Midplane", "Reversed", "TaperAngle", "Refine"),
        "PartDesign::Revolution" to listOf("Profile", "ReferenceAxis", "Type", "Angle", "Angle2", "UpToFace", "Midplane", "Reversed", "Refine"),
        "PartDesign::Groove" to listOf("Profile", "ReferenceAxis", "Type", "Angle", "Angle2", "UpToFace", "Midplane", "Reversed", "Refine"),
        "PartDesign::Fillet" to listOf("Base", "Radius", "UseAllEdges", "Refine"),
        "Part::Fillet" to listOf("Base", "Edges"), "Part::Chamfer" to listOf("Base", "Edges"),
        "PartDesign::Chamfer" to listOf("Base", "ChamferType", "Size", "Size2", "Angle", "FlipDirection", "UseAllEdges", "Refine"),
        "PartDesign::Draft" to listOf("Base", "NeutralPlane", "PullDirection", "Angle", "Reversed"),
        "PartDesign::Thickness" to listOf("Base", "Value", "Mode", "Join", "Reversed", "Intersection"),
        "PartDesign::Hole" to listOf("Profile", "Threaded", "ThreadType", "ThreadSize", "ThreadClass", "ThreadFit", "Diameter", "ThreadDirection",
            "HoleCutType", "HoleCutDiameter", "HoleCutDepth", "HoleCutCountersinkAngle", "DepthType", "Depth", "DrillPoint", "DrillPointAngle", "Tapered", "TaperedAngle", "Reversed"),
        "PartDesign::LinearPattern" to listOf("Originals", "Direction", "Mode", "Length", "Offset", "Occurrences", "Reversed", "Refine"),
        "PartDesign::PolarPattern" to listOf("Originals", "Axis", "Mode", "Angle", "Offset", "Occurrences", "Reversed", "Refine"),
        "PartDesign::Mirrored" to listOf("Originals", "MirrorPlane", "Refine"),
        "PartDesign::AdditiveLoft" to listOf("Profile", "Sections", "Ruled", "Closed", "Refine"),
        "PartDesign::SubtractiveLoft" to listOf("Profile", "Sections", "Ruled", "Closed", "Refine"),
        "PartDesign::AdditivePipe" to listOf("Profile", "Spine", "Mode", "Transition", "Transformation", "Refine"),
        "PartDesign::SubtractivePipe" to listOf("Profile", "Spine", "Mode", "Transition", "Transformation", "Refine"),
        "PartDesign::AdditiveHelix" to listOf("Profile", "ReferenceAxis", "Mode", "Pitch", "Height", "Turns", "Angle", "Growth", "LeftHanded", "Reversed", "Refine"),
        "PartDesign::SubtractiveHelix" to listOf("Profile", "ReferenceAxis", "Mode", "Pitch", "Height", "Turns", "Angle", "Growth", "LeftHanded", "Reversed", "Refine"),
        "PartDesign::Plane" to listOf("AttachmentSupport", "MapMode", "AttachmentOffset"),
        "Sketcher::SketchObject" to listOf("AttachmentSupport", "MapMode", "AttachmentOffset"),
    )

    // Assembly joints: src/Mod/Assembly/JointObject.py JointUsing* lists drive TaskAssemblyCreateJoint's rows
    private val jointRows = listOf("JointType", "Reference1", "Reference2", "Distance", "Distance2", "Angle", "@Offset", "@Rotation", "@Reverse",
        "EnableLengthMin", "LengthMin", "EnableLengthMax", "LengthMax", "EnableAngleMin", "AngleMin", "EnableAngleMax", "AngleMax")
    private val usingDistance = setOf("Distance", "RackPinion", "Screw", "Gears", "Belt"); private val usingDistance2 = setOf("Gears", "Belt")
    private val usingOffset = setOf("Fixed", "Revolute"); private val usingRotation = setOf("Fixed", "Slider")
    private val usingReverse = setOf("Fixed", "Revolute", "Cylindrical", "Slider", "Distance", "Parallel")
    private val limitLength = setOf("Cylindrical", "Slider"); private val limitAngle = setOf("Revolute", "Cylindrical")

    private fun jointVisible(prop: String, v: Map<String, Any?>): Boolean {
        val jt = v["JointType"]?.toString() ?: return true
        return when (prop) {
            "Distance" -> jt in usingDistance; "Distance2" -> jt in usingDistance2; "Angle" -> jt == "Angle"
            "@Offset" -> jt in usingOffset; "@Rotation" -> jt in usingRotation; "@Reverse" -> jt in usingReverse
            "EnableLengthMin", "EnableLengthMax" -> jt in limitLength; "LengthMin" -> jt in limitLength && v["EnableLengthMin"] == true
            "LengthMax" -> jt in limitLength && v["EnableLengthMax"] == true
            "EnableAngleMin", "EnableAngleMax" -> jt in limitAngle; "AngleMin" -> jt in limitAngle && v["EnableAngleMin"] == true
            "AngleMax" -> jt in limitAngle && v["EnableAngleMax"] == true
            else -> true
        }
    }

    /** Rows hidden by the current enum mode (FreeCAD task panels hide them the same way). */
    fun visible(type: String, prop: String, values: Map<String, Any?>): Boolean {
        if (values.containsKey("JointType")) return jointVisible(prop, values)
        val mode = values["Type"]?.toString()
        when (prop) {
            "Length" -> return mode == null || mode in setOf("Length", "TwoLengths", "Dimension")
            "Length2" -> return mode == "TwoLengths"
            "UpToFace" -> return mode == "UpToFace"
            "UpToShape" -> return mode == "UpToShape"
            "Angle2" -> return mode == "TwoAngles"
            "Angle" -> if (type.endsWith("Revolution") || type.endsWith("Groove")) return mode == null || mode in setOf("Angle", "TwoAngles")
            "Midplane" -> return mode == null || mode in setOf("Length", "Dimension", "Angle")
            "Size2" -> return values["ChamferType"]?.toString() == "Two distances"
            "Depth" -> return values["DepthType"]?.toString() == "Dimension"
            "ThreadType", "ThreadSize", "ThreadClass", "ThreadFit", "ThreadDirection" -> return prop == "ThreadType" || values["Threaded"] == true || prop == "ThreadSize"
        }
        return true
    }

    fun split(type: String, all: List<FcProperty>): Pair<List<FcProperty>, List<FcProperty>> {
        val props = all.filter { it.type != "App::PropertyUUID" }      // internal identifiers (FEM Analysis Uid ...)
        val p = if (props.any { it.name == "JointType" }) jointRows else primary[type]
        if (p != null) return p.mapNotNull { n -> props.firstOrNull { it.name == n } } to props.filter { it.name !in p && !it.readonly && (it.group !in setOf("Base", "Shape History") || it.name == "Placement") }
        val own = type.substringAfter("::")
        // FEM boundary conditions (TaskFemConstraint*): the geometry, then the load / condition values (Force, Pressure
        // are ungrouped C++ properties); amplitude tables, formulas and the symbol scale stay under More options
        if (type.startsWith("Fem::Constraint")) {
            val aux = Regex("AmplitudeValues|EnableAmplitude|.*Formula|Scale|Label|Label2|Suppressed")
            val main = props.filter { it.name == "References" } +
                props.filter { !it.readonly && it.name != "References" && !aux.matches(it.name) && (it.group.isEmpty() || it.group == own) }.sortedBy { if (it.group.isEmpty()) 0 else 1 }
            return main to props.filter { it !in main && !it.readonly && it.group !in setOf("Base") }
        }
        // Python features (BIM, FEM, CAM ... all "...::FeaturePython"): the object's own property group is the largest
        // editable group that is not one of FreeCAD's shared groups
        val generic = setOf("Base", "Shape History", "Attachment", "Placement", "Expressions", "IFC", "IFC Attributes", "IFC Properties",
            "IFC Quantities", "Component", "Material", "Visibility", "Display Options", "Object style", "Children")
        val group = if (props.any { it.group == own }) own else props.filter { !it.readonly && it.group !in generic }
            .groupBy { it.group }.maxByOrNull { it.value.size }?.key
        val main = props.filter { it.group == group && !it.readonly }
        return main to props.filter { it !in main && !it.readonly && (it.group != "Base" || it.name == "Placement") }
    }

    fun isQuantity(t: String) = t.startsWith("App::PropertyQuantity") || t in setOf("App::PropertyLength", "App::PropertyDistance", "App::PropertyAngle",
        "App::PropertyFloat", "App::PropertyFloatConstraint", "App::PropertyPrecision", "App::PropertyPercent", "App::PropertyInteger",
        "App::PropertyIntegerConstraint", "App::PropertyVolume", "App::PropertyArea", "App::PropertyPosition") || t.startsWith("App::PropertyLength") ||
        t.removePrefix("App::Property") in UNIT_PROPS
    /** App/PropertyUnits.h: the PropertyQuantity subclasses (Force, Pressure, Temperature ...) */
    private val UNIT_PROPS = setOf("Acceleration", "AmountOfSubstance", "Area", "CompressiveStrength", "CurrentDensity", "Density", "DissipationRate", "DynamicViscosity", "ElectricalCapacitance", "ElectricalConductance", "ElectricalConductivity", "ElectricalInductance", "ElectricalResistance", "ElectricCharge", "SurfaceChargeDensity", "VolumeChargeDensity", "ElectricCurrent", "ElectricPotential", "Frequency", "Force", "HeatFlux", "MassMomentOfInertia", "AreaMomentOfInertia", "InverseArea", "InverseLength", "InverseVolume", "KinematicViscosity", "LuminousIntensity", "MagneticFieldStrength", "MagneticFlux", "MagneticFluxDensity", "Magnetization", "ElectromagneticPotential", "Mass", "Moment", "Pressure", "Power", "ShearModulus", "SpecificEnergy", "SpecificHeat", "Speed", "Stiffness", "StiffnessDensity", "Stress", "Temperature", "ThermalConductivity", "ThermalExpansionCoefficient", "ThermalTransferCoefficient", "Time", "UltimateTensileStrength", "VacuumPermittivity", "Velocity", "Volume", "VolumeFlowRate", "VolumetricThermalExpansionCoefficient", "Work", "YieldStrength", "YoungsModulus")
    fun isLink(t: String) = t.startsWith("App::PropertyLink") || t.startsWith("App::PropertyXLink")
    fun units(t: String, value: Any? = null) = when { t.contains("Angle") -> ANGLE_UNITS; t.contains("Length") || t.contains("Distance") || t.contains("Position") -> LENGTH_UNITS
        // other unit properties (Force, Pressure ...): the unit the value is shown in, so a bare number means "N", not FreeCAD's internal mN
        else -> value?.toString()?.trim()?.let { Regex("^[-+0-9.eE]+\\s*(\\S.*)$").find(it)?.groupValues?.get(1) }?.let { listOf(it) } ?: emptyList() }
}

fun linkSummary(v: Any?): String = when (v) {
    null -> "None"
    is String -> if (v.isEmpty()) "None" else v
    is JSONArray -> {
        // [[object, [sub, ...]], ...]: "Pad · Edge3, Edge7" for one object, "3 objects · 5 elements" otherwise
        val items = ArrayList<Pair<String, List<String>>>()
        for (i in 0 until v.length()) {
            val x = v.optJSONArray(i) ?: continue
            val a = x.optJSONArray(1)
            val subs = ArrayList<String>()
            if (a != null) for (k in 0 until a.length()) a.getString(k).takeIf { it.isNotEmpty() && it !in subs }?.let { subs.add(it) }   // joints repeat the element
            items.add(x.getString(0) to subs)
        }
        val subs = items.sumOf { it.second.size.coerceAtLeast(1) }
        when {
            items.isEmpty() -> "None"
            items.size == 1 && items[0].second.isEmpty() -> items[0].first
            items.size == 1 -> items[0].first + " · " + (if (items[0].second.size <= 3) items[0].second.joinToString(", ") else "${items[0].second.size} elements")
            else -> "${items.size} objects · $subs elements"
        }
    }
    else -> v.toString()
}

@Composable
fun FeatureDialog(
    title: String, type: String, props: List<FcProperty>, error: String?, activeQuery: String?,
    onQueryFocus: (String) -> Unit, onSet: (FcProperty, Any?) -> Unit, onNumpad: (FcProperty) -> Unit,
    onOk: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, editors: DialogEditors? = null,
    onSetPart: (path: String, text: String) -> Unit = { _, _ -> },
) {
    var more by remember(title) { mutableStateOf(false) }
    var collapsed by remember(title) { mutableStateOf(false) }
    val values = props.associate { it.name to it.value }
    val (main, rest) = DialogSpec.split(type, props)
    Column(modifier.then(Screen.panelWidth(300.dp)).shadow(8.dp, RoundedCornerShape(6.dp)).background(OS.Surface, RoundedCornerShape(6.dp))) {
        Row(Modifier.fillMaxWidth().height(44.dp).background(OS.Metal5, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).clickable { collapsed = !collapsed }.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, if (collapsed) "Expand" else "Collapse", tint = OS.Steel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = OS.Navy, fontSize = 15.sp, maxLines = 1)
            IconBtn(Icons.Default.Check, "OK", OS.Green, onClick = onOk)
            IconBtn(Icons.Default.Close, "Cancel", OS.Red, onClick = onCancel)
        }
        if (error != null) Text(error, Modifier.fillMaxWidth().background(OS.ErrBg).padding(horizontal = 10.dp, vertical = 6.dp), color = OS.Red, fontSize = 12.sp)
        if (!collapsed) Column(Modifier.heightIn(max = Screen.panelHeight(420.dp)).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            main.filter { DialogSpec.visible(type, it.name, values) }.forEach { PropRow(it, activeQuery, onQueryFocus, onSet, onNumpad, editors, onSetPart) }
            if (rest.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (more) "Fewer options" else "More options (${rest.size})", Modifier.weight(1f), color = OS.Primary, fontSize = 13.sp)
                    Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OS.Primary)
                }
                if (more) rest.forEach { PropRow(it, activeQuery, onQueryFocus, onSet, onNumpad, editors, onSetPart) }
            }
        }
    }
}

@Composable
private fun PropRow(p: FcProperty, activeQuery: String?, onQueryFocus: (String) -> Unit, onSet: (FcProperty, Any?) -> Unit, onNumpad: (FcProperty) -> Unit,
                    ed: DialogEditors?, onSetPart: (String, String) -> Unit) {
    val label = p.name.removePrefix("@").replace(Regex("([a-z])([A-Z0-9])"), "$1 $2")
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        when {
            p.kind == "parts" && ed != null -> {
                // vector / placement: one numpad field per component (desktop property editor's x / y / z, yaw / pitch / roll)
                Text(label, fontSize = 11.sp, color = OS.Steel)
                val parts = p.extra?.optJSONArray("parts") ?: JSONArray()
                (0 until parts.length()).map { parts.getJSONObject(it) }.chunked(3).forEach { row ->
                    Row(Modifier.padding(top = 3.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { c ->
                            val ex = c.optString("expression").takeIf { !c.isNull("expression") && it.isNotEmpty() }
                            Row(Modifier.weight(1f).height(32.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).clickable {
                                ed.number("$label ${c.getString("label")}", ex?.let { "=$it" } ?: c.optString("value"), DialogSpec.units(c.getString("type"), c.optString("value"))) { v -> onSetPart(c.getString("path"), v) }
                            }.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.getString("label"), color = OS.Steel, fontSize = 10.sp); Spacer(Modifier.width(4.dp))
                                Text(ex?.let { "=$it" } ?: c.optString("value"), Modifier.weight(1f), color = if (ex != null) OS.Primary else OS.Navy, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            p.kind == "color" && ed != null -> Row(Modifier.fillMaxWidth().height(36.dp).clickable {
                ed.choose(label, PALETTE.map { it.second to it.first } + ("custom" to "Custom (#rrggbb)…")) { c ->
                    if (c == "custom") ed.text("$label (#rrggbb)", p.value?.toString() ?: "", false) { t -> onSet(p, t) } else onSet(p, c) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = OS.Navy, fontSize = 13.sp)
                val c = runCatching { Color(android.graphics.Color.parseColor(p.value?.toString())) }.getOrDefault(Color.Gray)
                Box(Modifier.size(width = 44.dp, height = 22.dp).background(c, RoundedCornerShape(4.dp)).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)))
            }
            (p.kind == "text" || p.kind == "list" || p.kind == "file") && ed != null -> {
                val shown = when (p.kind) {
                    "list" -> p.extra?.optInt("count")?.let { n -> if (n == 0) "Empty" else p.extra.optString("text").lines().let { l -> if (n == 1) l[0] else "$n items" } } ?: ""
                    "file" -> p.value?.toString()?.substringAfterLast('/')?.ifEmpty { "None" } ?: "None"
                    else -> p.value?.toString() ?: ""
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable {
                    when (p.kind) {
                        "file" -> ed.file(p)
                        "list" -> ed.text("$label (${p.extra?.optString("hint")})", p.extra?.optString("text") ?: "", true) { t -> onSet(p, t) }
                        else -> ed.text(label, p.value?.toString() ?: "", p.value?.toString()?.contains('\n') == true) { t -> onSet(p, t) }
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), color = OS.Navy, fontSize = 13.sp)
                    Text(shown.ifEmpty { " " }, Modifier.padding(start = 8.dp).widthIn(min = 40.dp, max = 170.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp), color = OS.Navy, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            p.kind == "edges" && ed != null -> EdgesEditor(p, label, activeQuery == p.name, { onQueryFocus(p.name) }, { v -> onSet(p, v) }, ed)
            p.type == "App::PropertyEnumeration" -> {
                Text(label, fontSize = 11.sp, color = OS.Steel)
                if (p.enums.size <= 4) Row(Modifier.padding(top = 4.dp).fillMaxWidth().border(1.dp, OS.Metal4, RoundedCornerShape(4.dp))) {
                    p.enums.forEach { e ->
                        val sel = e == p.value?.toString()
                        Box(Modifier.weight(1f).height(32.dp).background(if (sel) OS.Primary else OS.Surface).clickable { onSet(p, e) }, contentAlignment = Alignment.Center) {
                            Text(e.replace(Regex("([a-z])([A-Z])"), "$1 $2"), fontSize = 11.sp, color = if (sel) Color.White else OS.Navy, maxLines = 1)
                        }
                    }
                } else {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Row(Modifier.padding(top = 2.dp).fillMaxWidth().height(34.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).clickable { open = true }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(p.value?.toString() ?: "", Modifier.weight(1f), color = OS.Navy); Icon(Icons.Default.ExpandMore, null, tint = OS.Steel)
                        }
                        DropdownMenu(open, { open = false }) { p.enums.forEach { e -> DropdownMenuItem({ Text(e) }, { onSet(p, e); open = false }) } }
                    }
                }
            }
            p.type == "App::PropertyBool" -> Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = OS.Navy, fontSize = 13.sp)
                Switch(p.value == true, { onSet(p, it) }, Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = OS.Primary))
            }
            DialogSpec.isLink(p.type) -> {
                val active = activeQuery == p.name
                Text(label, fontSize = 11.sp, color = OS.Steel)
                Row(Modifier.padding(top = 2.dp).fillMaxWidth().height(34.dp).background(if (active) OS.Cloud.copy(alpha = .35f) else OS.Surface, RoundedCornerShape(4.dp))
                    .border(if (active) 2.dp else 1.dp, if (active) OS.BlueMed else OS.Metal4, RoundedCornerShape(4.dp)).clickable { onQueryFocus(p.name) }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(linkSummary(p.value), Modifier.weight(1f), color = OS.Navy, fontSize = 14.sp, maxLines = 1)
                    if (active) Text("Tap geometry", color = OS.Primary, fontSize = 11.sp)
                }
            }
            DialogSpec.isQuantity(p.type) -> Row(Modifier.fillMaxWidth().height(36.dp).clickable { onNumpad(p) }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = OS.Navy, fontSize = 13.sp)
                Text(p.expression?.let { "=$it" } ?: (p.value?.toString() ?: ""), Modifier.border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (p.expression != null) OS.Primary else OS.Navy, fontSize = 13.sp)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = OS.Navy, fontSize = 14.sp)
                Text(p.value?.toString() ?: "", Modifier.padding(start = 8.dp).widthIn(max = 200.dp), color = OS.Steel, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val PALETTE = listOf("Gray" to "#cccccc", "White" to "#ffffff", "Black" to "#000000", "Red" to "#e53935", "Orange" to "#fb8c00",
    "Yellow" to "#fdd835", "Green" to "#43a047", "Cyan" to "#00acc1", "Blue" to "#1e88e5", "Purple" to "#8e24aa", "Brown" to "#8d6e63")

/** Part Fillet / Chamfer edge table (DlgFilletEdges): tap edges on the model to add / remove them, each edge has its own
 *  radius (chamfer: size), plus a value applied to all edges, and All / None. */
@Composable
private fun EdgesEditor(p: FcProperty, label: String, active: Boolean, onFocus: () -> Unit, onSet: (List<List<String>>) -> Unit, ed: DialogEditors) {
    val rows = (p.value as? JSONArray)?.let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { listOf(it.getString(0), it.getString(1), it.getString(2)) } } } ?: emptyList()
    val word = if (p.extra?.optBoolean("chamfer") == true) "Size" else "Radius"
    val nEdges = p.extra?.optInt("nedges") ?: 0
    Text(label, fontSize = 11.sp, color = OS.Steel)
    Row(Modifier.padding(top = 2.dp).fillMaxWidth().height(34.dp).background(if (active) OS.Cloud.copy(alpha = .35f) else OS.Surface, RoundedCornerShape(4.dp))
        .border(if (active) 2.dp else 1.dp, if (active) OS.BlueMed else OS.Metal4, RoundedCornerShape(4.dp)).clickable(onClick = onFocus).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (rows.isEmpty()) "No edges" else "${rows.size} edge${if (rows.size == 1) "" else "s"}", Modifier.weight(1f), color = OS.Navy, fontSize = 14.sp)
        Text(if (active) "Tap edges" else "Tap to pick", color = OS.Primary, fontSize = 11.sp)
    }
    Row(Modifier.padding(top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val fallback = rows.firstOrNull()?.get(1) ?: "1 mm"
        Chip("$word for all") { ed.number("$word for all edges", fallback, LENGTH_UNITS) { v -> onSet(rows.map { listOf(it[0], v, v) }) } }
        if (nEdges > 0) Chip("All") { onSet((1..nEdges).map { i -> rows.firstOrNull { it[0] == "Edge$i" } ?: listOf("Edge$i", fallback, fallback) }) }
        if (rows.isNotEmpty()) Chip("None") { onSet(emptyList()) }
    }
    rows.forEachIndexed { i, r ->
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r[0], Modifier.weight(1f), color = OS.Navy, fontSize = 13.sp)
            Text(r[1], Modifier.border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).clickable {
                ed.number("${r[0]} $word", r[1], LENGTH_UNITS) { v -> onSet(rows.mapIndexed { k, x -> if (k == i) listOf(x[0], v, v) else x }) }
            }.padding(horizontal = 8.dp, vertical = 3.dp), color = OS.Navy, fontSize = 13.sp)
            Text("✕", Modifier.clickable { onSet(rows.filterIndexed { k, _ -> k != i }) }.padding(horizontal = 10.dp, vertical = 4.dp), color = OS.Red, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) =
    Text(text, Modifier.border(1.dp, OS.Metal4, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), color = OS.Primary, fontSize = 12.sp)

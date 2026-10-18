# FreeCAD command catalog (from source, main @ e95a31c)

`*` = command defined in the module but not placed on a toolbar/menu file (context menu, task dialog or hidden).

## Assembly (33)

| Command | Menu text | Tooltip |
|---|---|---|
| Assembly_CreateAssembly | New Assembly | Assembly_CreateAssembly |
| Assembly_Insert | Insert Component | tooltip |
| Assembly_SolveAssembly | Solve Assembly | Assembly_SolveAssembly |
| Assembly_CreateView | Exploded View | Assembly_CreateView |
| Assembly_CreateSnapshot | Snapshot | Assembly_CreateSnapshot |
| Assembly_CreateSimulation | Simulation | Assembly_CreateSimulation |
| Assembly_CreateBom | Bill of Materials | Assembly_CreateBom |
| Assembly_LinkSelectLinked | Go to Linked Assembly | Selects the linked assembly and switches to its original document |
| Assembly_ExportASMT | Export ASMT File | Assembly_ExportASMT |
| Assembly_SelectJointsOfComponent | Select Component Joints | Selects all joints referencing the selected component |
| Assembly_ToggleGrounded | Toggle Grounded | Assembly_ToggleGrounded |
| Assembly_CreateJointRigidGroup | Create Rigid Group | Assembly_CreateJointRigidGroup |
| Assembly_CreateJointFixed | Assembly_CreateJointFixed | Assembly_CreateJointFixed |
| Assembly_CreateJointRevolute | Revolute Joint | Assembly_CreateJointRevolute |
| Assembly_CreateJointCylindrical | Cylindrical Joint | Assembly_CreateJointCylindrical |
| Assembly_CreateJointSlider | Slider Joint | Assembly_CreateJointSlider |
| Assembly_CreateJointBall | Ball Joint | Assembly_CreateJointBall |
| Assembly_CreateJointDistance | Distance Joint | Assembly_CreateJointDistance |
| Assembly_CreateJointParallel | Parallel Joint | Assembly_CreateJointParallel |
| Assembly_CreateJointPerpendicular | Perpendicular Joint | Assembly_CreateJointPerpendicular |
| Assembly_CreateJointAngle | Angle Joint | Assembly_CreateJointAngle |
| Assembly_CreateJointRackPinion | Rack and Pinion Joint | Assembly_CreateJointRackPinion |
| Assembly_CreateJointScrew | Screw Joint | Assembly_CreateJointScrew |
| Assembly_CreateJointGearBelt | Gears/Belt Joint | Assembly_CreateJointGearBelt |
| Assembly_ActivateAssembly | Activate Assembly | Sets an assembly as the active one for editing. |
| Assembly_SelectConflictingConstraints * | Select Conflicting Constraints | Selects conflicting joints in the active assembly |
| Assembly_SelectRedundantConstraints * | Select Redundant Constraints | Selects redundant joints in the active assembly |
| Assembly_SelectMalformedConstraints * | Select Malformed Constraints | Selects malformed joints in the active assembly |
| Assembly_SelectComponentsWithDoFs * | Select Components With DoFs | Selects unconstrained components in the active assembly |
| Assembly_CreateJointGears * | Gears Joint | Assembly_CreateJointGears |
| Assembly_CreateJointBelt * | Belt Joint | Assembly_CreateJointBelt |
| Assembly_InsertNewPart * | New Part | Assembly_InsertNewPart |
| Assembly_InsertLink * | Insert Component | tooltip |

## BIM (190)

| Command | Menu text | Tooltip |
|---|---|---|
| BIM_Sketch | New Sketch | Creates a new sketch in the current working plane |
| Draft_Line | Line | Creates a 2-point line |
| Draft_Wire | Polyline | Creates a polyline |
| Draft_Rectangle | Rectangle | Creates a 2-point rectangle |
| BIM_ArcTools |  |  |
| Draft_Circle | Circle | Creates a circle (full circular arc) |
| Draft_Ellipse | Ellipse | Creates an ellipse |
| Draft_Polygon | Polygon | Creates a regular polygon (triangle, square, pentagon…) |
| BIM_SplineTools |  |  |
| Draft_Point | Point | Creates a point |
| Draft_Fillet | Fillet | Creates a fillet between 2 selected edges |
| BIM_DimensionAligned | Aligned Dimension | Creates an aligned dimension |
| BIM_DimensionHorizontal | Horizontal Dimension | Creates an horizontal dimension |
| BIM_DimensionVertical | Vertical Dimension | Creates a vertical dimension |
| BIM_Text | Text | Create a text in the current 3D view or TechDraw page |
| BIM_Leader | Leader | Creates a polyline with an arrow at its endpoint |
| Draft_Label | Label | Draft_Label |
| Draft_Hatch | Hatch | Creates hatches on the faces of a selected object |
| BIM_AxisTools |  |  |
| Arch_Grid | Grid | Creates a customizable grid object |
| Arch_SectionPlane | Section Plane | Arch_SectionPlane |
| BIM_Create2DViews |  |  |
| BIM_TDPage | New Page | Creates a new TechDraw page from a template |
| BIM_TDView | New View | BIM_TDView |
| Arch_Site | Site | Creates a site including selected objects |
| Arch_Building | Building | Creates a building object including selected objects. |
| Arch_Level | Level | Creates a building part object that represents a level |
| Arch_Space | Space | Creates a space object from selected boundary objects |
| Arch_Wall | Wall | Arch_Wall |
| Arch_CurtainWall | Curtain Wall | Arch_CurtainWall |
| BIM_Column | Column | Creates a column at a specified location |
| BIM_Beam | Beam | Creates a beam between two points |
| BIM_Slab | Slab | Creates a slab from a planar shape |
| BIM_Door | Door | Places a door at a given location |
| Arch_Window | Window | Arch_Window |
| BIM_Covering | Covering | Creates a covering (floor finish, cladding) on a selected face |
| Arch_Pipe | Pipe | Creates a pipe object from a given wire or line |
| Arch_PipeConnector | Connector | Creates a connector between 2 or 3 selected pipes |
| Arch_Stairs | Stairs | Creates a flight of stairs |
| Arch_Roof | Roof | Creates a roof object from the selected wire. |
| Arch_Panel | Panel | Arch_Panel |
| Arch_Frame | Frame | Arch_Frame |
| Arch_Fence | Fence | Creates a fence object from a selected section, post and path |
| Arch_Truss | Truss | Creates a truss object from the selected line or from scratch |
| Arch_Equipment | Equipment | Creates an equipment from a selected object (Part or Mesh) |
| Arch_Rebar | Custom Rebar | Arch_Rebar |
| BIM_GenericTools |  |  |
| Draft_Move | Move | Draft_Move |
| Draft_Rotate | Rotate | Draft_Rotate |
| Draft_Scale | Scale | Scales the selected objects from a base point |
| Draft_Mirror | Mirror | Mirrors the selected objects along a line defined by 2 points |
| BIM_CloneTools |  |  |
| BIM_Copy | Copy | Copies selected objects to another location |
| BIM_SimpleCopy | Simple Copy | Creates a simple non-parametric copy |
| BIM_Compound | Compound | Creates a compound of several shapes |
| BIM_OffsetTools |  |  |
| BIM_Trimex |  |  |
| Draft_Join | Join | Draft_Join |
| Draft_Split | Split | Splits the selected line or polyline at a specified point |
| Draft_Stretch | Stretch | Stretches the selected objects |
| Draft_Draft2Sketch | Draft to Sketch | Draft_Draft2Sketch |
| Draft_Edit | Edit | Edits the active object |
| Draft_Upgrade | Upgrade | Draft_Upgrade |
| Draft_Downgrade | Downgrade | Draft_Downgrade |
| Arch_Add | Add Component | Adds the selected components to the active object |
| Arch_Remove | Remove Component | Arch_Remove |
| BIM_ArrayTools |  |  |
| Arch_CutPlane | Cut With Plane | Cuts an object with a plane |
| BIM_ExtrudeTools |  |  |
| BIM_BooleanTools |  |  |
| BIM_Setup | BIM Setup | Sets common FreeCAD preferences for a BIM workflow |
| BIM_ProjectManager | Setup Project | Creates or manages a BIM project |
| BIM_Windows | Manage Doors and Windows | BIM_Windows |
| BIM_IfcManageTools |  |  |
| BIM_Layers | Manage Layers | Sets/modifies the different layers of your BIM project |
| BIM_Material | Material | Sets or creates a material for selected objects |
| BIM_ReportTools |  |  |
| BIM_Preflight | Preflight Checks | BIM_Preflight |
| Draft_AnnotationStyleEditor | Annotation Styles | Draft_AnnotationStyleEditor |
| BIM_Trash | Move to Trash | Moves the selected objects to the trash folder |
| BIM_WPView | Working Plane View | BIM_WPView |
| Draft_SelectGroup | Select Group | Draft_SelectGroup |
| Draft_Slope | Set Slope | Draft_Slope |
| Draft_WorkingPlaneProxy | Working Plane Proxy | Draft_WorkingPlaneProxy |
| Draft_AddConstruction | Add to Construction Group | Draft_AddConstruction |
| Arch_SplitMesh | Split Mesh | Splits selected meshes into independent components |
| Arch_MeshToShape | Mesh to Shape | Turns selected meshes into Part shape objects |
| Arch_SelectNonSolidMeshes | Select Non-Manifold Meshes | Arch_SelectNonSolidMeshes |
| Arch_RemoveShape | Remove Shape From BIM | Removes cubic shapes from BIM components |
| Arch_CloseHoles | Close Holes | Closes holes in open shapes, turning them into solids |
| Arch_MergeWalls | Merge Walls | Merges the selected walls, if possible |
| Arch_Check | Check | Checks the selected objects for problems |
| Arch_ToggleIfcBrepFlag | Toggle IFC B-Rep Flag | Forces an object to be exported as B-rep or not |
| Arch_ToggleSubs | Toggle Subcomponents | Shows or hides the subcomponents of this object |
| BIM_Diff | IFC Shape Diff | Shows the difference between two IFC-based documents |
| BIM_IfcExplorer | IFC Explorer | Opens the IFC explorer utility |
| Arch_IfcSpreadsheet | New IFC Spreadsheet | Creates a spreadsheet to store IFC properties of an object |
| BIM_ImagePlane | Image Plane | Creates a plane from an image |
| BIM_Rewire | Rewire | Recreates wires from selected objects |
| BIM_Glue | Glue | Joins selected shapes into one non-parametric shape |
| BIM_Reextrude | Re-Extrude | Recreates an extruded structure from a selected face |
| Arch_PanelTools | Panel Tools | Panel tools |
| Arch_StructureTools |  |  |
| BIM_Project | IFC Project | Creates an empty NativeIFC project |
| BIM_Nudge_Switch | Nudge Switch |  |
| BIM_Nudge_Up | Nudge Up |  |
| BIM_Nudge_Down | Nudge Down |  |
| BIM_Nudge_Left | Nudge Left |  |
| BIM_Nudge_Right | Nudge Right |  |
| BIM_Nudge_RotateLeft | Nudge Rotate Left |  |
| BIM_Nudge_RotateRight | Nudge Rotate Right |  |
| BIM_Nudge_Extend | Nudge Extend |  |
| BIM_Nudge_Shrink | Nudge Shrink |  |
| BIM_SetWPFront | Working Plane Front | Sets the working plane to Front |
| BIM_SetWPTop | Working Plane Top | Sets the working plane to Top |
| BIM_SetWPSide | Working Plane Side | Sets the working plane to Side |
| Draft_SelectPlane | Working Plane | Draft_SelectPlane |
| Draft_Arc | Arc | Creates a circular arc from a center point and a radius |
| Draft_Arc_3Points | Arc From 3 Points | Creates a circular arc from 3 points |
| Draft_BSpline | B-Spline | Creates a multiple-point B-spline |
| Draft_BezCurve | Bézier Curve | Draft_BezCurve |
| Draft_CubicBezCurve | Cubic Bézier Curve | Draft_CubicBezCurve |
| Arch_Axis | Axis | Creates a set of axes |
| Arch_AxisSystem | Axis System | Creates an axis system from a set of axes |
| BIM_Offset2D | 2D Offset | Utility to offset planar shapes |
| Draft_Offset | Offset | Draft_Offset |
| Draft_OrthoArray | Array | Creates copies of the selected object in an orthogonal pattern |
| Draft_PathLinkArray | Path Link Array | Draft_PathLinkArray |
| Draft_PolarArray | Polar Array | Creates copies of the selected object in a polar pattern |
| Draft_PointLinkArray | Point Link Array | Draft_PointLinkArray |
| BIM_Fuse | Union | Creates a union of several shapes |
| BIM_Cut | Difference | Creates a difference between two shapes |
| BIM_Common | Intersection | Creates an intersection of two shapes |
| BIM_ExtrudeFace | Extrude Face | Extrudes a selected face into a solid |
| BIM_Extrude | Extrude | Extrudes a selected 2D shape |
| BIM_IfcElements | Manage IFC Elements | BIM_IfcElements |
| BIM_IfcQuantities | Manage IFC Quantities | BIM_IfcQuantities |
| BIM_IfcProperties | Manage IFC Properties | BIM_IfcProperties |
| BIM_Classification | Manage Classification | BIM_Classification |
| BIM_Report | Report | Create a new BIM Report to query model data with SQL |
| Arch_Schedule | Schedule | Creates a schedule to collect data from the model |
| Arch_Survey | Survey | Starts survey |
| BIM_Clone | Clone | Clones selected objects to another location |
| BIM_LinkMake | Make Link | BIM_LinkMake |
| BIM_Unclone | Unclone | BIM_Unclone |
| Arch_Profile | Profile | Creates a profile |
| BIM_Box | Box | Graphically creates a generic box in the current document |
| BIM_Builder | Shape Builder | Advanced utility to create shapes |
| Draft_Facebinder | Facebinder | Creates a facebinder from the selected faces |
| BIM_Library | Objects Library | Opens the objects library |
| Arch_Component | Component | Creates an undefined architectural component |
| Arch_Reference | External Reference | Creates an external reference object |
| BIM_DrawingView | 2D Drawing | Creates a drawing container to contain elements of a 2D view |
| BIM_Shape2DView |  |  |
| BIM_Shape2DCut |  |  |
| Draft_UpdateShape2DView | Force 2D View Update | Draft_UpdateShape2DView |
| Arch_RebarTools |  |  |
| BIM_Welcome | BIM Welcome Screen | Shows the BIM workbench welcome screen |
| BIM_Views | Views Manager | Shows or hides the views manager |
| BIM_Examples | BIM Examples | BIM_Examples |
| Std_ReportBug | Report an Issue | Opens the bugtracker to report an issue |
| BIM_Tutorial | BIM Tutorial | Starts or continues the BIM in-game tutorial |
| BIM_Help | BIM Help | BIM_Help |
| BIM_Ungroup | Remove From Group | Removes this object from its parent group |
| BIM_EmptyTrash | Empty Trash | BIM_EmptyTrash |
| Draft_AddToGroup | Add to Group | Draft_AddToGroup |
| BIM_ResetCloneColors | Reset Colors | BIM_ResetCloneColors |
| BIM_Reorder | Reorder Children | Reorders children of the selected object |
| BIM_MoveView | Move View | Moves this view to an existing page |
| Arch_Floor * | Level | Arch_Floor |
| Arch_StructuralSystem * | Structural System | Arch_StructuralSystem |
| Arch_StructuresFromSelection * | Multiple Structures | Arch_StructuresFromSelection |
| Arch_PipeTools * | Pipe Tools | Pipe tools |
| BIM_Background * | Toggle Background | BIM_Background |
| Arch_CloneComponent * | Clone Component | Clones an object as an undefined architectural component |
| Arch_Panel_Cut * | Panel Cut | Creates 2D views of selected panels |
| Arch_Panel_Sheet * | Panel Sheet | Creates a 2D sheet which can contain panel cuts |
| Arch_Nest * | Nest | Nests a series of selected shapes in a container |
| BIM_Convert * | Convert to BIM | Converts any object to a BIM component |
| Arch_Material * | Material | Creates or edits the material definition of a selected object. |
| Arch_MultiMaterial * | Multi-Material | Creates or edits multi-materials |
| Arch_MaterialTools * | Material Tools | Material tools |
| Arch_AxisTools * | Axis Tools | Axis tools |
| IFC_Diff * | IFC File Diff | tt |
| IFC_Expand * | IFC Expand | tt |
| IFC_ConvertDocument * | Convert Document | tt |
| IFC_MakeProject * | Convert to IFC Project | tt |
| IFC_Save * | Save IFC File | tt |
| IFC_SaveAs * | Save IFC File As… | tt |
| IFC_UpdateIOS * | IfcOpenShell Update | tt |

## CAM (52)

| Command | Menu text | Tooltip |
|---|---|---|
| CAM_Job | New Job | Creates a CAM job |
| CAM_Workplane | Work Plane | CAM_Workplane |
| CAM_Sanity | Sanity Check | Checks the CAM job for common errors |
| CAM_Post | Post Process | Post Processes the selected Job |
| CAM_PostSelected | Post Process Selected | Post Processes the selected operations |
| CAM_Inspect | Inspect Toolpath | Inspects the contents of a toolpath object |
| CAM_SelectLoop | Finish Selecting Loop | CAM_SelectLoop |
| CAM_OpActiveToggle | Toggle Operation | Toggles the active state of the operation |
| CAM_SimulatorGL | CAM Simulator | Simulates G-code on stock |
| CAM_Simulator | Legacy CAM Simulator | Simulates G-code on stock |
| CAM_Comment | Comment | Adds a Comment to the CNC program |
| CAM_Stop | Stop | Adds an optional or mandatory stop to the program |
| CAM_OperationCopy | Copy Operation | Copies the operation in the job |
| CAM_Array | Array | Creates an array from selected toolpaths |
| CAM_SimpleCopy | Simple Copy | CAM_SimpleCopy |
| CAM_DressupArray | Array | CAM_DressupArray |
| CAM_DressupAxisMap | Axis Map | Remaps one axis to another |
| CAM_DressupPathBoundary | Boundary | CAM_DressupPathBoundary |
| CAM_DressupPathBoundary2 | Boundary2 | CAM_DressupPathBoundary2 |
| CAM_DressupDogbone | Dogbone | CAM_DressupDogbone |
| CAM_DressupDragKnife | Drag Knife | CAM_DressupDragKnife |
| CAM_DressupLeadInOut | Lead In/Out | CAM_DressupLeadInOut |
| CAM_DressupMirror | Mirror | Creates mirror of a selected path |
| CAM_DressupPlungeMilling | Plunge Milling | CAM_DressupPlungeMilling |
| CAM_DressupRampEntry | Ramp Entry | CAM_DressupRampEntry |
| CAM_DressupTag | Tag | Creates a tag dress-up object from a selected toolpath |
| CAM_DressupZCorrect | Z Depth Correction | Corrects Z depth using a probe map |
| CAM_PostTools |  |  |
| CAM_SimTools |  |  |
| CAM_EngraveTools |  |  |
| CAM_DrillingTools |  |  |
| CAM_DressupTools |  |  |
| CAM_PathShape | Path from Shape | Creates path from selected shapes with tool controller |
| CAM_Area | Area | Creates a feature area from the selected objects |
| CAM_Area_Workplane | Area Workplane | Selects a workplane for a feature area |
| CAM_Camotics | CAMotics | Simulates using CAMotics |
| CAM_3dTools |  |  |
| CAM_ExportTemplate | Export Template | CAM_ExportTemplate |
| CAM_PropertyBag | CAM_PropertyBag | CAM_PropertyBag |
| CAM_SetStartPoint | Start Point Selection | Selects the start point |
| CAM_ToolBitSave | menuTxt | toolTip |
| CAM_ToolBitSaveAs | menuTxt | toolTip |
| CAM_Compound * | Compound | Creates a compound from the selected toolpaths |
| CAM_Shape * | From Shape | Creates a toolpath from a selected shape |
| CAM_Copy * | Copy | Creates a linked copy of another toolpath |
| CAM_ToolController * | Tool Controller | Adds a new tool controller to the active job |
| CAM_ToolBitLibraryOpen * | Toolbit Library Manager | Opens an editor to manage toolbit libraries |
| CAM_ToolBitDock * | Add Toolbit… | Opens the toolbit selection dialog |
| CAM_ToolBitCreate * | New Toolbit | Creates a new toolbit object |
| CAM_ToolBitLoad * | Load Tool | Loads an existing toolbit object from a file |
| CAM_Fixture * | Fixture | Creates a fixture offset |
| CAM_QuickValidate * | Quick Validate | CAM_QuickValidate |

## Draft (86)

| Command | Menu text | Tooltip |
|---|---|---|
| Draft_Hyperlink | Open Links | Opens linked documents |
| Draft_UpdateShape2DView | Force 2D View Update | Draft_UpdateShape2DView |
| Draft_Line | Line | Creates a 2-point line |
| Draft_Wire | Polyline | Creates a polyline |
| Draft_Fillet | Fillet | Creates a fillet between 2 selected edges |
| Draft_ArcTools | Arc Tools | Tools to create various types of circular arcs |
| Draft_Circle | Circle | Creates a circle (full circular arc) |
| Draft_Ellipse | Ellipse | Creates an ellipse |
| Draft_Rectangle | Rectangle | Creates a 2-point rectangle |
| Draft_Polygon | Polygon | Creates a regular polygon (triangle, square, pentagon…) |
| Draft_BSpline | B-Spline | Creates a multiple-point B-spline |
| Draft_BezierTools | Bézier Tools | Tools to create various types of Bézier curves |
| Draft_Point | Point | Creates a point |
| Draft_Facebinder | Facebinder | Creates a facebinder from the selected faces |
| Draft_ShapeString | Shape From Text | Creates a shape from a text string and a specified font |
| Draft_Hatch | Hatch | Creates hatches on the faces of a selected object |
| Draft_Text | Text | Creates a multi-line annotation |
| Draft_Dimension | Dimension | Draft_Dimension |
| Draft_Label | Label | Draft_Label |
| Draft_AnnotationStyleEditor | Annotation Styles | Draft_AnnotationStyleEditor |
| Draft_Move | Move | Draft_Move |
| Draft_Rotate | Rotate | Draft_Rotate |
| Draft_Scale | Scale | Scales the selected objects from a base point |
| Draft_Mirror | Mirror | Mirrors the selected objects along a line defined by 2 points |
| Draft_Offset | Offset | Draft_Offset |
| Draft_Trimex | Trimex | Trims or extends the selected object |
| Draft_Stretch | Stretch | Stretches the selected objects |
| Draft_Clone | Clone | Creates a clone of the selected objects |
| Draft_ArrayTools | Array Tools | Draft_ArrayTools |
| Draft_Edit | Edit | Edits the active object |
| Draft_SubelementHighlight | Highlight Subelements | Draft_SubelementHighlight |
| Draft_Join | Join | Draft_Join |
| Draft_Split | Split | Splits the selected line or polyline at a specified point |
| Draft_Upgrade | Upgrade | Draft_Upgrade |
| Draft_Downgrade | Downgrade | Draft_Downgrade |
| Draft_WireToBSpline | Convert Wire/B-Spline | Draft_WireToBSpline |
| Draft_Draft2Sketch | Draft to Sketch | Draft_Draft2Sketch |
| Draft_Slope | Set Slope | Draft_Slope |
| Draft_FlipDimension | Flip Dimension | Draft_FlipDimension |
| Draft_Shape2DViewTools | 2D View Tools | Draft_Shape2DViewTools |
| Draft_SetStyle | Set Style | Sets the default style and can apply the style to objects |
| Draft_ApplyStyle | Apply Current Style | Applies the current style to the selected objects and groups |
| Draft_Layer | New Layer | Draft_Layer |
| Draft_LayerManager | Manage Layers | Allows to modify the layers |
| Draft_AddNamedGroup | New Named Group | Adds a group with a given name |
| Draft_SelectGroup | Select Group | Draft_SelectGroup |
| Draft_ToggleConstructionMode | Toggle Construction Mode | Toggles the construction mode |
| Draft_AddToLayer | Add to Layer | Draft_AddToLayer |
| Draft_AddToGroup | Add to Group | Draft_AddToGroup |
| Draft_AddConstruction | Add to Construction Group | Draft_AddConstruction |
| Draft_ToggleDisplayMode | Toggle Wireframe | Draft_ToggleDisplayMode |
| Draft_ToggleGrid | Toggle Grid | Toggles the visibility of the Draft grid |
| Draft_SelectPlane | Working Plane | Draft_SelectPlane |
| Draft_WorkingPlaneProxy | Working Plane Proxy | Draft_WorkingPlaneProxy |
| Draft_Heal | Heal | Draft_Heal |
| Draft_ShowSnapBar | Show Snap Toolbar | Shows the snap toolbar if it is hidden |
| Draft_Snap_Lock | Snap Lock | Enables or disables snapping globally |
| Draft_Snap_Endpoint | Snap Endpoint | Snaps to the endpoints of edges |
| Draft_Snap_Midpoint | Snap Midpoint | Snaps to the midpoint of edges |
| Draft_Snap_Center | Snap Center | Draft_Snap_Center |
| Draft_Snap_Angle | Snap Angle | Draft_Snap_Angle |
| Draft_Snap_Intersection | Snap Intersection | Draft_Snap_Intersection |
| Draft_Snap_Perpendicular | Snap Perpendicular | Snaps to the perpendicular points on faces and edges |
| Draft_Snap_Extension | Snap Extension | Draft_Snap_Extension |
| Draft_Snap_Parallel | Snap Parallel | Snaps to an imaginary line parallel to straight edges |
| Draft_Snap_Special | Snap Special | Snaps to special points defined by the object |
| Draft_Snap_Near | Snap Near | Snaps to the nearest point on faces and edges |
| Draft_Snap_Ortho | Snap Ortho | Draft_Snap_Ortho |
| Draft_Snap_Grid | Snap Grid | Snaps to the intersections of grid lines |
| Draft_Snap_WorkingPlane | Snap Working Plane | Projects snap points onto the current working plane |
| Draft_Snap_Dimensions | Snap Dimensions | Shows temporary X and Y dimensions |
| Draft_PathTwistedArray * | Twisted Path Array | Draft_PathTwistedArray |
| Draft_PathTwistedLinkArray * | Twisted Path Link Array | Draft_PathTwistedLinkArray |
| Draft_Arc * | Arc | Creates a circular arc from a center point and a radius |
| Draft_Arc_3Points * | Arc From 3 Points | Creates a circular arc from 3 points |
| Draft_PointArray * | Point Array | Draft_PointArray |
| Draft_PointLinkArray * | Point Link Array | Draft_PointLinkArray |
| Draft_PathArray * | Path Array | Creates copies of the selected object along a selected path |
| Draft_PathLinkArray * | Path Link Array | Draft_PathLinkArray |
| Draft_BezCurve * | Bézier Curve | Draft_BezCurve |
| Draft_CubicBezCurve * | Cubic Bézier Curve | Draft_CubicBezCurve |
| Draft_AutoGroup * | Auto-Group | Adds new Draft and BIM objects to the selected layer or group |
| Draft_CircularArray * | Circular Array | Draft_CircularArray |
| Draft_OrthoArray * | Array | Creates copies of the selected object in an orthogonal pattern |
| Draft_Shape2DView * | Shape 2D View | Draft_Shape2DView |
| Draft_PolarArray * | Polar Array | Creates copies of the selected object in a polar pattern |

## Fem (102)

| Command | Menu text | Tooltip |
|---|---|---|
| FEM_MeshClear |  |  |
| FEM_MeshClearGroups |  |  |
| FEM_MeshDisplayInfo |  |  |
| FEM_Analysis |  |  |
| FEM_MaterialSolid |  |  |
| FEM_MaterialFluid |  |  |
| FEM_MaterialMechanicalNonlinear |  |  |
| FEM_MaterialReinforced |  |  |
| FEM_MaterialEditor |  |  |
| FEM_ElementGeometry1D |  |  |
| FEM_ElementRotation1D |  |  |
| FEM_ElementGeometry2D |  |  |
| FEM_ElementFluid1D |  |  |
| FEM_CompEmConstraints | Electromagnetic Boundary Conditions | Electromagnetic boundary conditions |
| FEM_ConstraintInitialFlowVelocity |  |  |
| FEM_ConstraintInitialPressure |  |  |
| FEM_ConstraintFlowVelocity |  |  |
| FEM_ConstraintPlaneRotation | Plane Multi-Point Constraint | Creates a plane multi-point constraint for a face |
| FEM_ConstraintSectionPrint |  |  |
| FEM_ConstraintTransform | Local Coordinate System | Creates a local coordinate system on a face |
| FEM_ConstraintFixed | Fixed Boundary Condition | Creates a fixed boundary condition for a geometric entity |
| FEM_ConstraintRigidBody | Rigid Body Constraint | Creates a rigid body constraint for a geometric entity |
| FEM_ConstraintDisplacement | Displacement Boundary Condition | Creates a displacement boundary condition for a geometric entity |
| FEM_ConstraintContact | Contact Constraint | Creates a contact constraint between faces |
| FEM_ConstraintTie |  |  |
| FEM_ConstraintSpring | Spring Boundary Condition | Creates a spring boundary condition on a face |
| FEM_ConstraintForce | Force Load | Creates a force load applied to a geometric entity |
| FEM_ConstraintPressure | Pressure Load | Creates a pressure load acting on a face |
| FEM_ConstraintCentrif |  |  |
| FEM_ConstraintSelfWeight |  |  |
| FEM_ConstraintInitialTemperature | Initial Temperature | Creates an initial temperature acting on a body |
| FEM_ConstraintHeatflux | Heat Flux Load | Creates a heat flux load acting on a face |
| FEM_ConstraintTemperature | Temperature Boundary Condition | Creates a temperature/concentrated heat flux load acting on a face |
| FEM_ConstraintBodyHeatSource |  |  |
| FEM_MeshNetgenFromShape |  |  |
| FEM_MeshGmshFromShape |  |  |
| FEM_MeshRegion |  |  |
| FEM_MeshGroup |  |  |
| FEM_MeshGMSHRefinement | GMSH Refinements | Mesh refinements for the GMSH mesh generation |
| FEM_FEMMesh2Mesh |  |  |
| FEM_CompSolvers |  |  |
| FEM_CompMechEquations | Mechanical Equations | Mechanical equations for the Elmer solver |
| FEM_CompEmEquations | Electromagnetic Equations | Electromagnetic equations for the Elmer solver |
| FEM_EquationFlow |  |  |
| FEM_EquationFlux |  |  |
| FEM_EquationHeat |  |  |
| FEM_SolverControl |  |  |
| FEM_SolverRun |  |  |
| FEM_ResultsPurge |  |  |
| FEM_ResultShow |  |  |
| FEM_PostApplyChanges | Apply Changes to Pipeline | Applies changes to parameters directly and not on recompute only |
| FEM_PostPipelineFromResult | Post Pipeline From Result | Creates a post processing pipeline from a result object |
| FEM_PostBranchFilter | Pipeline Branch | Branches the pipeline into a new path |
| FEM_PostFilterWarp | Warp Filter | Warps the geometry along a vector field by a certain factor |
| FEM_PostFilterClipScalar | Scalar Clip Filter | Defines a clip filter which clips a field with a scalar value |
| FEM_PostFilterCutFunction | Function Cut Filter | Cuts the data along an implicit function |
| FEM_PostFilterClipRegion | Region Clip Filter | Defines a clip filter which uses functions to define the clipped region |
| FEM_PostFilterContours | Contours Filter | Defines a contours filter that displays iso contours |
| FEM_PostFilterGlyph |  |  |
| FEM_PostFilterDataAlongLine | Line Clip Filter | Defines a clip filter which clips a field along a line |
| FEM_PostFilterLinearizedStresses | Stress Linearization Plot | Defines a stress linearization plot |
| FEM_PostFilterDataAtPoint | Data at Point Clip Filter | Defines a clip filter which clips a field data at point |
| FEM_PostFilterCalculator | Calculator Filter | Creates a new field from current data |
| FEM_PostCreateFunctions | Filter Functions | Functions for use in postprocessing filter |
| FEM_PostVisualization | Data Visualizations | FEM_PostVisualization |
| FEM_ClippingPlaneAdd |  |  |
| FEM_ClippingPlaneRemoveAll |  |  |
| FEM_Examples |  |  |
| FEM_ConstraintElectromagnetic |  |  |
| FEM_ConstraintCurrentDensity |  |  |
| FEM_ConstraintMagnetization |  |  |
| FEM_ConstraintElectricChargeDensity |  |  |
| FEM_ConstraintFluidBoundary | Fluid Boundary Condition | Create fluid boundary condition on face entity for Computional Fluid Dynamics |
| FEM_ConstraintBearing | Bearing Constraint | Creates a bearing constraint |
| FEM_ConstraintGear | Gear Constraint | Creates a gear constraint |
| FEM_ConstraintPulley | Pulley Constraint | Creates a pulley constraint |
| FEM_ConstantVacuumPermittivity |  |  |
| FEM_CreateNodesSet | Nodes Set | Creates a FEM mesh nodes set |
| FEM_CreateElementsSet | Erase Elements | Creates a FEM mesh elements set |
| FEM_FemAddPart * | Add Part to Analysis | Adds a part to the analysis |
| FEM_DefineNodesSet * | Node Set by Polygon | Creates a node set by polygon selection |
| FEM_DefineElementsSet * | Element Set From Polygon | Creates a collection of elements selected by a polygon |
| FEM_EquationDeformation * |  |  |
| FEM_EquationElasticity * |  |  |
| FEM_EquationElectricforce * |  |  |
| FEM_EquationElectrostatic * |  |  |
| FEM_EquationMagnetodynamic * |  |  |
| FEM_EquationMagnetodynamic2D * |  |  |
| FEM_EquationStaticCurrent * |  |  |
| FEM_MeshBoundaryLayer * |  |  |
| FEM_MeshDistance * |  |  |
| FEM_MeshManipulate * |  |  |
| FEM_MeshAdvanced * |  |  |
| FEM_MeshShape * |  |  |
| FEM_MeshTransfiniteCurve * |  |  |
| FEM_MeshTransfiniteSurface * |  |  |
| FEM_MeshTransfiniteVolume * |  |  |
| FEM_SolverCalculiXCcxTools * |  |  |
| FEM_SolverCalculiX * |  |  |
| FEM_SolverElmer * |  |  |
| FEM_SolverMystran * |  |  |
| FEM_SolverZ88 * |  |  |

## Inspection (2)

| Command | Menu text | Tooltip |
|---|---|---|
| Inspection_VisualInspection | Visual Inspection | Inspects the objects visually |
| Inspection_InspectElement | Inspection… | Inspects distance information |

## Material (6)

| Command | Menu text | Tooltip |
|---|---|---|
| Material_Edit | Edit | Edits material properties |
| Std_SetAppearance * | Appearance | Sets the display properties of the selected object |
| Std_SetMaterial * | Material | Sets the material of the selected object |
| Materials_InspectAppearance * | Inspect Appearance | Inspects the appearance properties of the selected object |
| Materials_InspectMaterial * | Inspect Material | Inspects the material properties of the selected object |
| Materials_MigrateToExternal * | Migrate | Migrates the materials to the external materials manager |

## Mesh (38)

| Command | Menu text | Tooltip |
|---|---|---|
| Mesh_Import | Import Mesh… | Imports a mesh from a file |
| Mesh_Export | Export Mesh… | Exports a mesh to a file |
| Mesh_VertexCurvature | Curvature Plot | Calculates the curvature of the vertices of a mesh |
| Mesh_Evaluation | Evaluate and Repair | Opens a dialog to analyze and repair a mesh |
| Mesh_EvaluateFacet | Face Info | Displays information about the selected faces |
| Mesh_CurvatureInfo | Curvature Info | Displays information about the curvature |
| Mesh_EvaluateSolid | Evaluate Solid | Checks whether the mesh is a solid |
| Mesh_BoundingBox | Bounding Box Info | Shows the bounding box coordinates of the selected mesh |
| Mesh_Union | Union | Unifies the selected meshes |
| Mesh_Intersection | Intersection | Creates a boolean intersection from the selected meshes |
| Mesh_Difference | Difference | Creates a boolean difference of the selected meshes |
| Mesh_PolyCut | Cut | Cuts the mesh with a selected polygon |
| Mesh_PolyTrim | Trim | Trims a mesh with a selected polygon |
| Mesh_PolySegm | Segment | Creates a mesh segment |
| Mesh_TrimByPlane | Trim With Plane | Trims a mesh by removing faces on one side of a selected plane |
| Mesh_SectionByPlane | Section From Plane | Sections the mesh with the selected plane |
| Mesh_CrossSections | Cross-Sections | Creates cross-sections of the mesh |
| Mesh_FromPartShape | Mesh From Shape | Tessellates the selected shape to a mesh |
| Mesh_RemeshGmsh | Refinement | Refines an existing mesh |
| Mesh_HarmonizeNormals | Harmonize Normals | Harmonizes the normals of the mesh |
| Mesh_FlipNormals | Flip Normals | Flips the normals of the selected mesh |
| Mesh_FillupHoles | Fill Holes | Fills holes in the mesh |
| Mesh_FillInteractiveHole | Close Hole | Closes a hole interactively in the mesh |
| Mesh_AddFacet | Add Triangle | Adds a triangle manually to a mesh |
| Mesh_RemoveComponents | Remove Components | Removes topologically independent components from the mesh |
| Mesh_RemoveCompByHand | Remove Components Manually | Marks a component to remove it from the mesh |
| Mesh_Segmentation | Segmentation | Creates new mesh segments from the mesh |
| Mesh_SegmentationBestFit | Segmentation From Best-Fit Surfaces | Creates new mesh segments from the best-fit surfaces |
| Mesh_Smoothing | Smooth | Smoothes the selected meshes |
| Mesh_Decimating | Decimate | Decimates a mesh |
| Mesh_Scale | Scale | Scales the selected mesh objects |
| Mesh_BuildRegularSolid | Regular Solid | Builds a regular solid |
| Mesh_Merge | Merge | Merges selected meshes into one |
| Mesh_SplitComponents | Split by Components | Splits the selected mesh into its components |
| MeshPart_CreateFlatMesh | Unwrap Mesh | Finds a flat representation of a mesh |
| MeshPart_CreateFlatFace | Unwrap Face | Finds a flat representation of a face |
| Mesh_FromGeometry * | Mesh From Geometry | Creates a mesh from the selected geometry |
| Mesh_PolySplit * | Split | Splits a mesh into 2 meshes |

## MeshPart (7)

| Command | Menu text | Tooltip |
|---|---|---|
| MeshPart_Mesher | Mesh From Shape | Tessellate shape |
| MeshPart_TrimByPlane * | Trim Mesh | Trims a mesh with a plane |
| MeshPart_SectionByPlane * | Section | Creates a section from a mesh and plane |
| MeshPart_CrossSections * | Cross-Sections | Applies cross-sections to the mesh |
| MeshPart_CurveOnMesh * | Curve on Mesh | Creates an approximated curve on top of a mesh object |
| MeshPart_CreateFlatMesh * | Unwrap Mesh | Finds a flat representation of a mesh |
| MeshPart_CreateFlatFace * | Unwrap Face | Finds a flat representation of a face |

## OpenSCAD (23)

| Command | Menu text | Tooltip |
|---|---|---|
| OpenSCAD_ReplaceObject | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ReplaceObject', 'Replace Object') |  |
| OpenSCAD_RemoveSubtree | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_RemoveSubtree', 'Remove Objects and Children') |  |
| OpenSCAD_RefineShapeFeature | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_RefineShapeFeature', 'Refine Shape Feature') |  |
| OpenSCAD_MirrorMeshFeature | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_MirrorMeshFeature', 'Mirror Mesh Feature') |  |
| OpenSCAD_ScaleMeshFeature | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ScaleMeshFeature', 'Scale Mesh Feature') |  |
| OpenSCAD_ResizeMeshFeature | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ResizeMeshFeature', 'Resize Mesh Feature') |  |
| OpenSCAD_IncreaseToleranceFeature | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_IncreaseToleranceFeature', 'Increase Tolerance Feature') |  |
| OpenSCAD_Edgestofaces | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_Edgestofaces', 'Convert Edges to Faces') |  |
| OpenSCAD_ExpandPlacements | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ExpandPlacements', 'Expand Placements') |  |
| OpenSCAD_ExplodeGroup | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ExplodeGroup', 'Explode Group') |  |
| Part_CheckGeometry | Check Geometry | Analyzes the selected shapes for errors |
| Part_Primitives | Primitive | Creates solid geometric primitives parametrically |
| Part_Builder | Shape Builder | Advanced utility to create shapes |
| Part_Cut | Cut | Cuts 2 selected shapes |
| Part_Fuse | Union | Unites the selected shapes |
| Part_Common | Intersection | Intersects the selected shapes |
| Part_Extrude | Extrude | Extrudes the selected sketch or profile |
| Part_Revolve | Revolve | Revolves the selected shape |
| OpenSCAD_AddOpenSCADElement | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_AddOpenSCADElement', 'Add OpenSCAD Element') | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_AddOpenSCADElement' |
| OpenSCAD_MeshBoolean | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_MeshBoolean','Mesh Boolean') | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_MeshBoolean' |
| OpenSCAD_Hull | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_Hull', 'Hull') |  |
| OpenSCAD_Minkowski | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_Minkowski', 'Minkowski Sum') |  |
| OpenSCAD_ColorCodeShape * | QtCore.QT_TRANSLATE_NOOP('OpenSCAD_ColorCodeShape', 'Color Shapes') |  |

## Part (84)

| Command | Menu text | Tooltip |
|---|---|---|
| Part_Box | Cube | Creates a solid cube |
| Part_Cylinder | Cylinder | Creates a solid cylinder |
| Part_Sphere | Sphere | Creates a solid sphere |
| Part_Cone | Cone | Creates a solid cone |
| Part_Torus | Torus | Creates a solid torus |
| Part_Tube | Tube | Creates a tube |
| Part_SimpleCopy | Simple Copy | Creates a simple non-parametric copy of the selected shapes |
| Part_TransformedCopy | Transformed Copy | Creates a non-parametric copy with transformed placement of the selected shapes |
| Part_ElementCopy | Shape Element Copy | Creates a non-parametric copy of the selected shape element |
| Part_RefineShape | Refine Shape | Creates a refined copy of the selected shapes |
| Part_Boolean | Boolean Operation | Applies a boolean operation with the selected shapes |
| Part_Cut | Cut | Cuts 2 selected shapes |
| Part_Fuse | Union | Unites the selected shapes |
| Part_Common | Intersection | Intersects the selected shapes |
| Part_JoinConnect | Connect Shapes | Fuses shapes, taking care to preserve voids |
| Part_JoinEmbed | Embed Shapes | Fuses one shape into another, taking care to preserve voids |
| Part_JoinCutout | Cutout Shape | Creates a cutout in the selected shape to fit another shape |
| Part_BooleanFragments | Boolean Fragments | Part_BooleanFragments |
| Part_SliceApart | Slice Apart | Part_SliceApart |
| Part_Slice | Slice to Compound | Part_Slice |
| Part_XOR | Boolean XOR | Part_XOR |
| Part_Compound | Compound | Compounds the selected shapes |
| Part_ExplodeCompound | Explode Compound | Part_ExplodeCompound |
| Part_CompoundFilter | Compound Filter | Part_CompoundFilter |
| Part_ToleranceSet | Set Tolerance | Part_ToleranceSet |
| Part_BoxSelection | Box Selection | Selects elements in the 3D view using a box selection |
| Part_Primitives | Primitive | Creates solid geometric primitives parametrically |
| Part_Builder | Shape Builder | Advanced utility to create shapes |
| Part_ShapeFromMesh | Shape From Mesh | Creates a shape from the selected mesh |
| Part_PointsFromMesh | Points From Shape | Creates distributed points from the selected shape |
| Part_MakeSolid | Convert to Solid | Converts the selected shell or compound to a solid |
| Part_ReverseShape | Reverse Shapes | Reverses the orientation of the selected shapes |
| Sketcher_NewSketch | New Sketch | Creates a new sketch |
| Part_Extrude | Extrude | Extrudes the selected sketch or profile |
| Part_Revolve | Revolve | Revolves the selected shape |
| Part_Mirror | Mirror | Mirrors the selected shape |
| Part_Scale | Scale | Scales the selected shape |
| Part_Fillet | Fillet | Fillets the selected edges of a shape |
| Part_Chamfer | Chamfer | Chamfers the selected edges of a shape |
| Part_MakeFace | Face From Wires | Creates a face from the selected wires (e.g. from a sketch) |
| Part_RuledSurface | Ruled Surface | Creates a ruled surface between 2 selected wires |
| Part_Loft | Loft | Lofts the selected profiles |
| Part_Sweep | Sweep | Sweeps profiles along a wire |
| Part_Section | Section | Sections 2 selected shapes |
| Part_CrossSections | Cross-Sections | Creates cross-sections |
| Part_Offset | 3D Offset | Offsets shapes in 3D |
| Part_Offset2D | 2D Offset | Offsets planar shapes in 2D |
| Part_Thickness | Thickness | Removes the selected faces and offsets the remaining shape outward to add thickness |
| Part_ProjectionOnSurface | Project on Surface | Projects edges, wires, or faces of one shape\n |
| Part_SectionCut | Persistent Section Cut | Creates a new object as a boolean intersection of all visible  |
| Part_EditAttachment |  |  |
| Part_CheckGeometry | Check Geometry | Analyzes the selected shapes for errors |
| Part_Defeaturing | Defeaturing | Removes the selected features from a shape |
| Materials_InspectAppearance | Inspect Appearance | Inspects the appearance properties of the selected object |
| Materials_InspectMaterial | Inspect Material | Inspects the material properties of the selected object |
| Std_RandomColor | Random Color | Assigns random diffuse colors for the selected objects |
| Part_ColorPerFace | Appearance per Face | Sets the appearance of individual faces of the selected object |
| Part_CompOffset | Offset | Tools to offset shapes (construct parallel shapes) |
| Part_CompCompoundTools | Compound Tools | Compound tools for working with multiple shapes |
| Part_CompJoinFeatures | Join Shapes | Joins the selected walled shapes |
| Part_CompSplitFeatures | Split Shapes | Shape splitting and compsolid creation tools |
| Part_PickCurveNet * | Pick Curve Network | Picks a curve network |
| Part_NewDoc * | New Document | Creates an Empty Part Document |
| Part_Box2 * | Box Fix 1 | Creates a solid box |
| Part_Box3 * | Box Fix 2 | Creates a solid box |
| Part_Import * | Import CAD File | Imports a CAD file |
| Part_Export * | Export CAD File | Exports to a CAD file |
| Part_ImportCurveNet * | Import Curve Network | Imports a curve network |
| Part_ShapeInfo * | Shape Info | Displays information about the selected shape |
| Part_LinkArrayCircular * | Circular Link Array | Creates a concentric circular array of linked objects |
| Part_LinkArrayPath * | Path Link Array | Creates an array of linked objects along a path |
| Part_LinkArrayPoint * | Point Link Array | Creates an array of linked objects at each point of a sketch or shape |
| Part_LinkArrayLinear * | Linear Link Array | Creates a linear array of linked objects |
| Part_LinkArrayPolar * | Polar Link Array | Creates a polar array of linked objects |
| Part_CoordinateSystem * | Coordinate System | Creates a coordinate system that can be attached to other objects |
| Part_DatumPlane * | Datum Plane | Creates a datum plane that can be attached to other objects |
| Part_DatumLine * | Datum Line | Creates a datum line that can be attached to other objects |
| Part_DatumPoint * | Datum Point | Creates a datum point that can be attached to other objects |
| Part_SimpleCylinder * | Cylinder | Creates a solid cylinder |
| Part_SelectFilter * | Selection Filter | Changes the selection filter |
| Part_VertexSelection * | Vertex Selection | Only allows the selection of vertices |
| Part_EdgeSelection * | Edge Selection | Only allows the selection of edges |
| Part_FaceSelection * | Face Selection | Only allows the selection of faces |
| Part_RemoveSelectionGate * | No Selection Filters | Clears all selection filters |

## PartDesign (62)

| Command | Menu text | Tooltip |
|---|---|---|
| PartDesign_InvoluteGear |  |  |
| PartDesign_Sprocket |  |  |
| PartDesign_MoveTip | Set Tip | Moves the tip of the body to the selected feature |
| Std_ToggleFreeze | Toggle Freeze | toolTip.c_str() |
| PartDesign_MoveFeature | Move Object To… | Moves the selected object to another body |
| PartDesign_MoveFeatureInTree | Move Feature After… | Moves the selected feature after another feature in the same body |
| PartDesign_MultiTransform | Multi-Transform | Applies multiple transformations to the selected features or active body |
| Part_DatumPoint | Datum Point | Creates a datum point that can be attached to other objects |
| Part_DatumLine | Datum Line | Creates a datum line that can be attached to other objects |
| Part_DatumPlane | Datum Plane | Creates a datum plane that can be attached to other objects |
| Part_CoordinateSystem | Coordinate System | Creates a coordinate system that can be attached to other objects |
| PartDesign_Body | New Body | Creates a new body and activates it |
| PartDesign_Fillet | Fillet | Applies a fillet to the selected edges or faces |
| PartDesign_Chamfer | Chamfer | Applies a chamfer to the selected edges or faces |
| PartDesign_NewSketch | New Sketch | Creates a new sketch |
| PartDesign_Draft | Draft | Applies a draft to the selected faces |
| PartDesign_Thickness | Thickness | Applies thickness and removes the selected faces |
| PartDesign_Defeaturing | Defeaturing | Removes selected faces from a solid |
| PartDesign_Boolean | Boolean Operation | Applies boolean operations with the selected objects and the active body |
| PartDesign_Pad | Pad | Extrudes the selected sketch or profile and adds it to the body |
| PartDesign_Pocket | Pocket | Extrudes the selected sketch or profile and removes it from the body |
| PartDesign_Hole | Hole | Creates holes in the active body at the center points of circles or arcs of the selected sketch or profile |
| PartDesign_Revolution | Revolve | Revolves the selected sketch or profile around a line or axis and adds it to the body |
| PartDesign_Groove | Groove | Revolves the sketch or profile around a line or axis and removes it from the body |
| PartDesign_AdditiveLoft | Additive Loft | Lofts the selected sketch or profile through one or more sections and adds it to the body |
| PartDesign_SubtractiveLoft | Subtractive Loft | Lofts the selected sketch or profile through one or more sections and removes it from the body |
| PartDesign_AdditivePipe | Additive Pipe | Sweeps the selected sketch or profile along a path and adds it to the body |
| PartDesign_SubtractivePipe | Subtractive Pipe | Sweeps the selected sketch or profile along a path and removes it from the body |
| PartDesign_AdditiveHelix | Additive Helix | Sweeps the selected sketch or profile along a helix and adds it to the body |
| PartDesign_SubtractiveHelix | Subtractive Helix | Sweeps the selected sketch or profile along a helix and removes it from the body |
| PartDesign_Mirrored | Mirror | Mirrors the selected features or active body |
| PartDesign_LinearPattern | Linear Pattern | Duplicates the selected features or the active body in a linear pattern |
| PartDesign_PolarPattern | Polar Pattern | Duplicates the selected features or the active body in a circular pattern |
| PartDesign_CircularPattern | Circular Pattern | Duplicates the selected features or the active body in concentric circular patterns |
| PartDesign_PathPattern | Path Pattern | Duplicates the selected features or the active body along a path |
| PartDesign_PointPattern | Point Pattern | Duplicates the selected features or the active body at points from a shape |
| Sketcher_EditSketch | Edit Sketch | Opens the selected sketch for editing |
| Sketcher_MapSketch | Attach Sketch | Attaches a sketch to the selected geometry element |
| Sketcher_ReorientSketch | Reorient Sketch | Places the selected sketch on one of the global coordinate planes.\n |
| Sketcher_ValidateSketch | Validate Sketch | Validates a sketch by checking for missing coincidences,\n |
| Sketcher_MergeSketches | Merge Sketches | Creates a new sketch by merging at least 2 selected sketches |
| Sketcher_MirrorSketch | Mirror Sketch | Creates a new mirrored sketch for each selected sketch\n |
| PartDesign_ShapeBinder | Shape Binder | Creates a new shape binder |
| PartDesign_SubShapeBinder | Sub-Shape Binder | Creates a reference to geometry from one or more objects, allowing it to be used inside  |
| PartDesign_Clone | Clone | Copies a solid object parametrically as the base feature of a new body |
| PartDesign_CompPrimitiveAdditive | Additive Primitive | Creates an additive primitive |
| PartDesign_CompPrimitiveSubtractive | Subtractive Primitive | Creates a subtractive primitive |
| Materials_InspectAppearance | Inspect Appearance | Inspects the appearance properties of the selected object |
| Materials_InspectMaterial | Inspect Material | Inspects the material properties of the selected object |
| Part_CheckGeometry | Check Geometry | Analyzes the selected shapes for errors |
| PartDesign_WizardShaft |  |  |
| Std_RandomColor | Random Color | Assigns random diffuse colors for the selected objects |
| Part_ColorPerFace | Appearance per Face | Sets the appearance of individual faces of the selected object |
| Std_DuplicateSelection | Duplicate Selection | Duplicates the selected objects to the active document |
| PartDesign_DuplicateSelection | Duplicate Object | Duplicates the selected object and adds it to the active body |
| PartDesign_Plane * | Datum Plane | Creates a new datum plane |
| PartDesign_Line * | Datum Line | Creates a new datum line |
| PartDesign_Point * | Datum Point | Creates a new datum point |
| PartDesign_CoordinateSystem * | Local Coordinate System | Creates a new local coordinate system |
| PartDesign_Scaled * | Scale | Scales the selected features or the active body |
| PartDesign_Migrate * | Migrate | Migrates the document to the modern Part Design workflow |
| PartDesign_WizardShaftCallBack * |  |  |

## Points (6)

| Command | Menu text | Tooltip |
|---|---|---|
| Points_Import | Import Points… | Imports a point cloud |
| Points_Export | Export Points… | Exports a point cloud |
| Points_Convert | Convert to Points | Converts to points |
| Points_Structure | Structured Point Cloud | Converts points to a structured point cloud |
| Points_Merge | Merge Point Clouds | Merges several point clouds into one |
| Points_PolyCut | Cut Point Cloud | Cuts a point cloud with a selected polygon |

## ReverseEngineering (15)

| Command | Menu text | Tooltip |
|---|---|---|
| Reen_PoissonReconstruction | Poisson… | Performs Poisson surface reconstruction |
| Reen_ViewTriangulation | Structured Point Clouds | Triangulates structured point clouds |
| Mesh_RemeshGmsh | Refinement | Refines an existing mesh |
| Mesh_VertexCurvature | Curvature Plot | Calculates the curvature of the vertices of a mesh |
| Mesh_CurvatureInfo | Curvature Info | Displays information about the curvature |
| Reen_Segmentation | Mesh Segmentation… | Creates separate mesh segments based on surface types |
| Reen_SegmentationManual | Manual Segmentation… | Creates mesh segments manually |
| Reen_SegmentationFromComponents | From Components | Creates mesh segments from components |
| Reen_MeshBoundary | Wire From Mesh Boundary… | Creates a wire from mesh boundaries |
| Reen_ApproxPlane | Plane | Approximates a plane |
| Reen_ApproxCylinder | Cylinder | Approximates a cylinder |
| Reen_ApproxSphere | Sphere | Approximates a sphere |
| Reen_ApproxPolynomial | Polynomial Surface | Approximates a polynomial surface |
| Reen_ApproxSurface | Approximate B-Spline Surface… | Approximates a B-spline surface |
| Reen_ApproxCurve | Approximate B-Spline Curve… | Approximates a B-spline curve |

## Robot (15)

| Command | Menu text | Tooltip |
|---|---|---|
| Robot_InsertWaypoint | Insert in Trajectory | Inserts the robot tool location into the trajectory |
| Robot_InsertWaypointPreselect | Insert in Trajectory | Inserts the preselection position into the trajectory (W) |
| Robot_AddToolShape | Tool | Adds a tool shape to the robot |
| Robot_SetHomePos | Set Home Position | Sets the home position |
| Robot_RestoreHomePos | Move to Home | Moves to the home position |
| Robot_TrajectoryDressUp | Dress-Up Trajectory | Creates a dress-up object that overrides aspects of a trajectory |
| Robot_TrajectoryCompound | Trajectory Compound | Groups and connects multiple trajectories into one |
| Robot_Create | Place Robot | Places a robot in the scene |
| Robot_CreateTrajectory | Trajectory | Creates a new empty trajectory |
| Robot_Edge2Trac | Edge to Trajectory | Generates a trajectory from the selected edges |
| Robot_Simulate | Simulate Trajectory | Simulates robot movement along a selected trajectory |
| Robot_ExportKukaCompact | Kuka Compact Subroutine | Exports the trajectory as a compact KRL subroutine |
| Robot_ExportKukaFull | Kuka Full Subroutine | Exports the trajectory as a full KRL subroutine |
| Robot_SetDefaultOrientation | Set Default Orientation | Sets the default orientation for subsequent commands for waypoint creation |
| Robot_SetDefaultValues | Set Default Values | Sets the default values for speed, acceleration, and continuity for  |

## Sketcher (101)

| Command | Menu text | Tooltip |
|---|---|---|
| Sketcher_ProfilesHexagon1 | Creates a hexagonal profile | Creates a hexagonal profile in the sketch |
| Sketcher_NewSketch | New Sketch | Creates a new sketch |
| Sketcher_EditSketch | Edit Sketch | Opens the selected sketch for editing |
| Sketcher_MapSketch | Attach Sketch | Attaches a sketch to the selected geometry element |
| Sketcher_ReorientSketch | Reorient Sketch | Places the selected sketch on one of the global coordinate planes.\n |
| Sketcher_ValidateSketch | Validate Sketch | Validates a sketch by checking for missing coincidences,\n |
| Sketcher_MergeSketches | Merge Sketches | Creates a new sketch by merging at least 2 selected sketches |
| Sketcher_MirrorSketch | Mirror Sketch | Creates a new mirrored sketch for each selected sketch\n |
| Sketcher_LeaveSketch | Leave Sketch | Finishes editing the active sketch. Press Escape to exit. |
| Sketcher_CancelSketch | Cancel Editing | Leaves 'edit' mode and reverts any changes |
| Sketcher_ViewSketch | Align View to Sketch | Aligns the camera orientation perpendicular to the active sketch plane |
| Sketcher_ViewSection | Toggle Section View | Toggles between section view and full view |
| Sketcher_StopOperation | Stop Operation | Stops the active operation while in edit mode |
| Sketcher_CreatePolyline | Polyline | Creates a polyline in the sketch. M key cycles through segment modes. |
| Sketcher_CreateLine | Line | Creates a line |
| Sketcher_CreateArc | Arc From Center | Creates an arc defined by a center point and an end point |
| Sketcher_Create3PointArc | Arc From 3 Points | Creates an arc defined by 2 end points and 1 point on the arc |
| Sketcher_CreateArcOfEllipse | Elliptical Arc | Creates an elliptical arc |
| Sketcher_CreateArcOfHyperbola | Hyperbolic Arc | Creates a hyperbolic arc |
| Sketcher_CreateArcOfParabola | Parabolic Arc | Creates a parabolic arc |
| Sketcher_CreateCircle | Circle From Center | Creates a circle from a center and rim point |
| Sketcher_Create3PointCircle | Circle From 3 Points | Creates a circle from 3 perimeter points |
| Sketcher_CreateEllipseByCenter | Ellipse From Center | Creates an ellipse from a center and rim point |
| Sketcher_CreateEllipseBy3Points | Ellipse From 3 Points | Creates an ellipse from 3 points on its perimeter |
| Sketcher_CreateTriangle | Triangle | Creates an equilateral triangle from a center and corner point |
| Sketcher_CreateSquare | Square | Creates a square from a center and corner point |
| Sketcher_CreatePentagon | Pentagon | Creates a pentagon from a center and corner point |
| Sketcher_CreateHexagon | Hexagon | Creates a hexagon from a center and corner point |
| Sketcher_CreateHeptagon | Heptagon | Creates a heptagon from a center and corner point |
| Sketcher_CreateOctagon | Octagon | Creates an octagon from a center and corner point |
| Sketcher_CreateRegularPolygon | Polygon | Creates a regular polygon from a center and corner point |
| Sketcher_CreateRectangle | Rectangle | Creates a rectangle from 2 corner points |
| Sketcher_CreateRectangle_Center | Centered Rectangle | Creates a centered rectangle from a center and a corner point |
| Sketcher_CreateOblong | Rounded Rectangle | Creates a rounded rectangle from 2 corner points |
| Sketcher_CreateSlot | Slot | Creates a slot |
| Sketcher_CreateArcSlot | Arc Slot | Creates an arc slot |
| Sketcher_CreateBSpline | B-Spline | Creates a B-spline curve defined by control points |
| Sketcher_CreatePeriodicBSpline | Periodic B-Spline | Creates a periodic B-spline curve defined by control points |
| Sketcher_CreateBSplineByInterpolation | B-Spline From Knots | Creates a B-spline from knots, i.e. from interpolation |
| Sketcher_CreatePeriodicBSplineByInterpolation | Periodic B-Spline From Knots | Creates a periodic B-spline defined by knots using interpolation |
| Sketcher_CreateFillet | Fillet | Creates a fillet between 2 selected curves or at coincident points |
| Sketcher_CreateChamfer | Chamfer | Creates a chamfer between 2 selected curves or at coincident points |
| Sketcher_Trimming | Trim Edge | Trims an edge with respect to the selected position |
| Sketcher_Extend | Extend Edge | Extends an edge with respect to the selected position |
| Sketcher_Split | Split Edge | Splits an edge into 2 segments while preserving constraints |
| Sketcher_CreatePoint | Point | Creates a point |
| Sketcher_CreateText | Text | Creates text geometries controlled by a Text constraint.\n |
| Sketcher_ToggleConstruction | Toggle Construction Geometry | Toggles between defining geometry and construction geometry modes |
| Sketcher_ConstrainGroup | Group Constraint | Constrains the selected geometries together as a single entity. |
| Sketcher_Dimension | Dimension | Constrains contextually based on the selection. The type can be changed with the M key. |
| Sketcher_ConstrainSnellsLaw | Refraction Constraint | Constrains the selected elements based on the refraction law (Snell's Law) |
| Sketcher_ToggleDrivingConstraint | Toggle Driving/Reference Constraints | Toggles between driving and reference mode of the selected constraints and commands |
| Sketcher_ToggleActiveConstraint | Toggle Constraints | Toggles the state of the selected constraints |
| Sketcher_CompConstrainRadDia | Radius/Diameter Dimension | Constrains the radius or diameter of an arc or a circle |
| Sketcher_Projection | External Projection | Creates the projection of external geometry in the sketch plane |
| Sketcher_Intersection | External Intersection | Creates the intersection of external geometry with the sketch plane |
| Sketcher_CarbonCopy | Carbon Copy | Copies the geometry of another sketch |
| Sketcher_SelectOrigin | Select Origin | Selects the local origin point of the sketch |
| Sketcher_SelectHorizontalAxis | Select Horizontal Axis | Selects the local horizontal axis of the sketch |
| Sketcher_SelectVerticalAxis | Select Vertical Axis | Selects the local vertical axis of the sketch |
| Sketcher_Translate | Move / Array Transform | Translates the selected geometries and enables the creation of 'i' * 'j' total elements |
| Sketcher_Rotate | Rotate / Polar Transform | Rotates the selected geometry by creating 'n' total elements, enabling circular pattern creation |
| Sketcher_Scale | Scale | Scales the selected geometries |
| Sketcher_Offset | Offset | Adds an equidistant closed contour around selected geometry: positive values offset outward, negative values inward |
| Sketcher_Symmetry | Mirror | Creates a mirrored copy of the selected geometry |
| Sketcher_RemoveAxesAlignment | Remove Axes Alignment | Modifies the constraints to remove axes alignment while trying to  |
| Sketcher_DeleteAllGeometry | Delete All Geometry | Deletes all geometry and their constraints in the current sketch,  |
| Sketcher_DeleteAllConstraints | Delete All Constraints | Deletes all constraints in the sketch |
| Sketcher_CopyClipboard | Copy Elements | Copies the selected geometries and constraints to the clipboard |
| Sketcher_Cut | Cut Elements | Cuts the selected geometries and constraints to the clipboard |
| Sketcher_Paste | Paste Elements | Pastes the geometries and constraints from the clipboard into the sketch |
| Sketcher_BSplineConvertToNURBS | Geometry to B-Spline | Converts the selected geometry to B-splines |
| Sketcher_BSplineIncreaseDegree | Increase B-Spline Degree | Increases the degree of the B-spline |
| Sketcher_BSplineDecreaseDegree | Decrease B-Spline Degree | Decreases the degree of the B-spline |
| Sketcher_BSplineIncreaseKnotMultiplicity | Increase Knot Multiplicity | Increases the multiplicity of the selected knot of a B-spline |
| Sketcher_BSplineDecreaseKnotMultiplicity | Decrease Knot Multiplicity | Decreases the multiplicity of the selected knot of a B-spline |
| Sketcher_BSplineInsertKnot | Insert Knot | Inserts a knot at a given parameter. If a knot already exists at that  |
| Sketcher_JoinCurves | Join Curves | Joins 2 curves at selected end points |
| Sketcher_CompModifyKnotMultiplicity | Modify Knot Multiplicity | Modifies the multiplicity of the selected knot of a B-spline |
| Sketcher_SelectElementsWithDoFs | Select Under-Constrained Elements | Selects geometrical elements where the solver still detects  |
| Sketcher_SelectConstraints | Select Associated Constraints | Selects the constraints associated with the selected geometrical elements |
| Sketcher_SelectRedundantConstraints | Select Redundant Constraints | Selects all redundant constraints |
| Sketcher_SelectConflictingConstraints | Select Conflicting Constraints | Selects all conflicting constraints |
| Sketcher_ArcOverlay | Toggle Circular Helper for Arcs | Toggles the visibility of the circular helpers for all arcs |
| Sketcher_RestoreInternalAlignmentGeometry | Toggle Internal Geometry | Toggles the visibility of all internal geometry |
| Sketcher_SwitchVirtualSpace | Switch Virtual Space | Switches the selected constraints or the view to the other virtual space |
| Sketcher_CompBSplineShowHideGeometryInformation | Toggle B-Spline Information Layer | Toggles the visibility of the information layer for all B-splines |
| Sketcher_Grid * | Toggle Grid | Toggles the grid display in the active sketch |
| Sketcher_Snap * | Toggle Snap | Toggles snapping |
| Sketcher_RenderingOrder * | Rendering Order | Reorders items in the rendering order |
| Sketcher_ChangeDimensionConstraint * | Edit Value | Edits the value of a dimensional constraint |
| Sketcher_CreatePolylineLegacy * | Polyline | Creates a continuous polyline. Press the 'M' key to switch segment modes |
| Sketcher_BSplineDegree * | Toggle B-Spline Degree | Toggles the visibility of the degree for all B-splines |
| Sketcher_BSplinePolygon * | Toggle B-Spline Control Polygon | Toggles the visibility of the control polygons for all B-splines |
| Sketcher_BSplineComb * | Toggle B-Spline Curvature Comb | Toggles the visibility of the curvature comb for all B-splines |
| Sketcher_BSplineKnotMultiplicity * | Toggle B-Spline Knot Multiplicity | Toggles the visibility of the knot multiplicity for all B-splines |
| Sketcher_BSplinePoleWeight * | Toggle B-Spline Control Point Weight | Toggles the visibility of control point weights for all B-splines |
| Sketcher_SelectMalformedConstraints * | Select Malformed Constraints | Selects all malformed constraints |
| Sketcher_SelectPartiallyRedundantConstraints * | Select Partially Redundant Constraints | Selects all partially redundant constraints |
| Sketcher_CompCopy * | Clone | Creates a clone of the geometry taking as reference the last selected point |
| Sketcher_RectangularArray * | Rectangular Array | Creates a rectangular array pattern of the geometry taking as  |

## Spreadsheet (15)

| Command | Menu text | Tooltip |
|---|---|---|
| Spreadsheet_AlignLeft | Align Left | Aligns cell contents to the left |
| Spreadsheet_AlignCenter | Align Horizontal Center | Aligns cell contents to the horizontal center |
| Spreadsheet_AlignRight | Align Right | Aligns cell contents to the right |
| Spreadsheet_AlignTop | Align Top | Aligns cell contents to the top |
| Spreadsheet_AlignVCenter | Align Vertical Center | Aligns cell contents to the vertical center |
| Spreadsheet_AlignBottom | Align Bottom | Aligns cell contents to the bottom |
| Spreadsheet_StyleBold | Bold Text | Sets the text in the selected cells bold |
| Spreadsheet_StyleItalic | Italic Text | Sets the text in the selected cells italic |
| Spreadsheet_StyleUnderline | Underline Text | Underlines the text in the selected cells |
| Spreadsheet_CreateSheet | New Spreadsheet | Creates a new spreadsheet |
| Spreadsheet_Import | Import Spreadsheet | Imports a CSV file into a new spreadsheet |
| Spreadsheet_Export | Export Spreadsheet | Exports the spreadsheet to a CSV file |
| Spreadsheet_MergeCells | Merge Cells | Merges the selected cells |
| Spreadsheet_SplitCell | Split Cell | Splits a previously merged cell |
| Spreadsheet_SetAlias | Set Alias | Sets an alias for the selected cell |

## Start (1)

| Command | Menu text | Tooltip |
|---|---|---|
| Start_Start | Start Page | Displays the start page |

## Surface (7)

| Command | Menu text | Tooltip |
|---|---|---|
| Surface_Filling | Filling | Creates a surface from a series of selected boundary edges.\n |
| Surface_GeomFillSurface | Fill Boundary Curves | Creates a surface from 2, 3, or 4 boundary edges |
| Surface_Sections | Sections | Creates a surface from a series of sectional edges |
| Surface_ExtendFace | Extend Face | Extrapolates the selected face or surface at its boundaries with  |
| Surface_CurveOnMesh | Curve on Mesh | Creates an approximated curve on top of a mesh.\n |
| Surface_BlendCurve | Blend Curve | Joins 2 edges with continuity |
| Surface_Cut | Surface Cut | Cuts one shape using another |

## TechDraw (128)

| Command | Menu text | Tooltip |
|---|---|---|
| TechDraw_Dimension | Dimension | Inserts new contextual dimensions to the selection.\n |
| TechDraw_LengthDimension | Length Dimension | Inserts a length dimension of an edge or distance between two points |
| TechDraw_HorizontalDimension | Horizontal Length Dimension | Inserts a horizontal length dimension of an edge or distance between two points |
| TechDraw_VerticalDimension | Vertical Length Dimension | Inserts a vertical length dimension of an edge or distance between two points |
| TechDraw_RadiusDimension | Radius Dimension | Inserts a radius dimension of a circular edge or arc |
| TechDraw_DiameterDimension | Diameter Dimension | Inserts a diameter dimension of a circular edge or arc |
| TechDraw_AngleDimension | Angle Dimension | Inserts an angle dimension between two edges |
| TechDraw_3PtAngleDimension | Angle Dimension From 3 Points | Inserts an angle dimension between 3 selected points |
| TechDraw_AreaDimension | Area Annotation | Inserts an annotation showing the area of a selected face |
| TechDraw_HorizontalExtentDimension | Horizontal Extent Dimension | Inserts a dimension showing the horizontal extent (overall length) of an object or feature |
| TechDraw_VerticalExtentDimension | Vertical Extent Dimension | Inserts a dimension showing the vertical extent (overall length) of an object or feature |
| TechDraw_DimensionRepair | Repair Dimension References | Repairs broken or incorrect dimension references |
| TechDraw_ExtensionSelectLineAttributes | Select Line Attributes, Cascade Spacing and Delta Distance | Configures the default attributes for cosmetic lines and centerlines, including cascade spacing and delta distance |
| TechDraw_ExtensionChangeLineAttributes | Change Line Attributes | Changes the selected cosmetic lines and centerlines to the specified attributes |
| TechDraw_ExtensionExtendLine | Extend Line | Extends a selected cosmetic line or centerline at both ends by the specified delta distance |
| TechDraw_ExtensionShortenLine | Shorten Line | Shortens a selected cosmetic line or centerline at both ends by the specified delta distance |
| TechDraw_ExtensionLockUnlockView | Toggle View Lock | Locks or unlocks the position of the selected views |
| TechDraw_ExtensionPositionSectionView | Position Section View | TechDraw_ExtensionPositionSectionView |
| TechDraw_ExtensionPosHorizChainDimension | Align Horizontal Chain Dimensions | QT_TR_NOOP("Aligns the horizontal dimensions to create a chain dimension:<br>\ - Select two or more horizontal dimensions<br>\ - The first dimension defines the |
| TechDraw_ExtensionPosVertChainDimension | Align Vertical Chain Dimensions | QT_TR_NOOP("Aligns the vertical dimensions to create a chain dimension:<br>\ - Select two or more vertical dimensions<br>\ - The first dimension defines the pos |
| TechDraw_ExtensionPosObliqueChainDimension | Align Oblique Chain Dimensions | QT_TR_NOOP("Aligns the oblique dimensions to create a chain dimension:<br>\ - Select two or more parallel oblique dimensions<br>\ - The first dimension defines  |
| TechDraw_ExtensionCascadeHorizDimension | Cascade Horizontal Dimensions | QT_TR_NOOP("Evenly spaces the selected horizontal dimensions:<br>\ - Specify the cascade spacing (optional)<br>\ - Select two or more horizontal dimensions<br>\ |
| TechDraw_ExtensionCascadeVertDimension | Cascade Vertical Dimensions | QT_TR_NOOP("Evenly spaces the selected vertical dimensions:<br>\ - Specify the cascade spacing (optional)<br>\ - Select two or more vertical dimensions<br>\ - T |
| TechDraw_ExtensionCascadeObliqueDimension | Cascade Oblique Dimensions | QT_TR_NOOP("Evenly spaces the selected oblique dimensions:<br>\ - Specify the cascade spacing (optional)<br>\ - Select two or more parallel oblique dimensions<b |
| TechDraw_ExtensionAreaAnnotation | Area Annotation | Calculates the area of multiple selected faces |
| TechDraw_ExtensionArcLengthAnnotation | Arc Length Annotation | Inserts an annotation with the calculated arc length of the selected edges |
| TechDraw_ExtensionCustomizeFormat | Customize Format Label | Customizes the format label of a selected dimension or balloon |
| TechDraw_ExtensionCircleCenterLines | Circle Centerlines | Adds centerlines to the selected circles and arcs |
| TechDraw_ExtensionHoleCircle | Bolt Circle Centerlines | Adds centerlines to a circular pattern of three or more selected circles |
| TechDraw_ExtensionThreadHoleSide | Cosmetic Thread Hole Side View | Adds a cosmetic thread to the side view of a hole or circle |
| TechDraw_ExtensionThreadHoleBottom | Cosmetic Thread Hole Bottom View | Adds a cosmetic thread to the top or bottom view of selected holes or circles |
| TechDraw_ExtensionThreadBoltSide | Cosmetic Thread Bolt Side View | Adds a cosmetic thread to the side view of a  |
| TechDraw_ExtensionThreadBoltBottom | Cosmetic Thread Bolt Bottom View | Adds a cosmetic thread to the top or bottom view of the selected bolts/screws/rods |
| TechDraw_ExtensionVertexAtIntersection | Cosmetic Intersection Vertices | Adds cosmetic vertices at the intersections of selected edges |
| TechDraw_CommandAddOffsetVertex | Offset Vertex |  |
| TechDraw_CosmeticCircle | Cosmetic 1 Point Circle | Adds a cosmetic circle based on a selected centerpoint |
| TechDraw_ExtensionDrawCosmCircle | Cosmetic 2 Point Circle | Adds a cosmetic circle based on two selected vertices, where the first is the center point and the second is the radius |
| TechDraw_ExtensionDrawCosmCircle3Points | Cosmetic 3 Point Circle | Adds a cosmetic circle that passes through 3 selected perimeter points |
| TechDraw_ExtensionDrawCosmArc | Cosmetic Arc | Adds a cosmetic counter clockwise arc based on three vertices,  |
| TechDraw_ExtensionLineParallel | Cosmetic Parallel Line | Adds a cosmetic circle to 3 selected vertices |
| TechDraw_ExtensionLinePerpendicular | Cosmetic Perpendicular Line | Adds a cosmetic line perpendicular to the selected line through the selected vertex |
| TechDraw_ExtensionCreateHorizChainDimension | Horizontal Chain Dimension | Inserts a sequence of aligned horizontal dimensions to at least  |
| TechDraw_ExtensionCreateVertChainDimension | Vertical Chain Dimension | Inserts a sequence of aligned vertical dimensions to at least  |
| TechDraw_ExtensionCreateObliqueChainDimension | Oblique Chain Dimension | Inserts a sequence of aligned oblique dimensions to at least  |
| TechDraw_ExtensionCreateHorizCoordDimension | Horizontal Coordinate Dimension | Adds evenly spaced horizontal dimensions between 3 or more vertices aligned to a shared baseline |
| TechDraw_ExtensionCreateVertCoordDimension | Vertical Coordinate Dimension | Adds evenly spaced vertical dimensions between 3 or more vertices aligned to a shared baseline |
| TechDraw_ExtensionCreateObliqueCoordDimension | Oblique Coordinate Dimension | Adds evenly spaced oblique dimensions between 3 or more vertices aligned to a shared baseline |
| TechDraw_ExtensionCreateHorizChamferDimension | Horizontal Chamfer Dimension | Inserts a horizontal size and angle dimension for a chamfer from 2 selected vertices |
| TechDraw_ExtensionCreateVertChamferDimension | Vertical Chamfer Dimension | Inserts a vertical size and angle dimension for a chamfer from 2 selected vertices |
| TechDraw_ExtensionCreateLengthArc | Arc Length Dimension | Inserts an arc length dimension to the selected arc |
| TechDraw_ExtensionInsertDiameter | Insert '⌀' Prefix | Inserts a '⌀' symbol at the beginning of the dimension |
| TechDraw_ExtensionInsertSquare | Insert '□' Prefix | Inserts a '□' symbol at the beginning of the dimension |
| TechDraw_ExtensionInsertRepetition | Insert 'n×' Prefix | Inserts a repeated feature count at the beginning of the dimension |
| TechDraw_ExtensionRemovePrefixChar | Remove Prefix | Removes the prefix symbols at the beginning of the dimension |
| TechDraw_ExtensionIncreaseDecimal | Increase Decimal Places | Increases the number of decimal places of the dimension |
| TechDraw_ExtensionDecreaseDecimal | Decrease Decimal Places | Decreases the number of decimal places of the dimension |
| TechDraw_Annotation | Text Annotation | Inserts an editable text block annotation to the current page |
| TechDraw_RichTextAnnotation | Rich Text Annotation | Inserts a rich text annotation in the current page |
| TechDraw_Balloon | Balloon Annotation | Inserts a new balloon annotation in the selected view |
| TechDraw_AxoLengthDimension | Axonometric Length Dimension |  |
| TechDraw_StackTop | Stack Top | Moves the selected view to the top of the stack |
| TechDraw_StackBottom | Stack Bottom | Moves the selected view to the bottom of the stack |
| TechDraw_StackUp | Stack Up | Moves the selected view up 1 level in the view stack |
| TechDraw_StackDown | Stack Down | Moves the selected view down 1 level in the view stack |
| TechDraw_LeaderLine | Leader Line | Adds a leader line |
| TechDraw_FaceCenterLine | Centerline on Face | Adds a centerline to selected faces |
| TechDraw_2LineCenterLine | Centerline Between 2 Lines | Adds a centerline between 2 selected lines |
| TechDraw_2PointCenterLine | Centerline Between 2 Points | Adds a centerline between 2 selected points |
| TechDraw_2PointCosmeticLine | Cosmetic Line Through 2 Points | Adds a cosmetic line that passes through 2 selected points |
| TechDraw_DecorateLine | Edit Line Appearance | Opens the 'Line decoration' dialog to edit the selected lines |
| TechDraw_ShowAll | Toggle Edge Visibility | Toggles the visibility of the selected edges |
| TechDraw_CosmeticVertex | Cosmetic Vertex | Adds a cosmetic vertex |
| TechDraw_Midpoints | Midpoint Vertices | Adds cosmetic vertices at the midpoint of the selected edges |
| TechDraw_Quadrants | Quadrant Vertices | Adds cosmetic vertices at the quadrant points of the selected circles |
| TechDraw_PageDefault | New Page | Creates a new page with the default template |
| TechDraw_PageTemplate | New Page From Template | Creates a new page from a custom template |
| TechDraw_FillTemplateFields | Update Template Fields | TechDraw_FillTemplateFields |
| TechDraw_RedrawPage | Redraw Page | Redraws the current page |
| TechDraw_PrintAll | Print All Pages | Prints all pages with the print dialog |
| TechDraw_ExportPageSVG | Export Page as SVG | Exports the current page as an SVG |
| TechDraw_ExportPageDXF | Export Page as DXF | Exports the current page as a DXF |
| TechDraw_ExportPagePDF | Export Page as PDF | Exports the current page as a PDF |
| TechDraw_View | New View | Inserts a new view into the current page based on the selected object in the tree view  |
| TechDraw_BrokenView | Broken View | Inserts a new broken view for the selected objects or base view and break definition objects |
| TechDraw_SectionView | Section View | Inserts a new section view based on the selected view in the current page |
| TechDraw_ComplexSection | Complex Section View | Inserts a complex section view based on the selected view in the current page |
| TechDraw_DetailView | Detail View | Inserts a new detail view based on the selected view in the current page |
| TechDraw_ProjectionGroup | Projection Group | Inserts multiple new linked views of the selected objects in the current page |
| TechDraw_ClipGroup | Clip Group | Inserts a new clip group for the selected view |
| TechDraw_Symbol | Insert SVG | Inserts a symbol from an SVG file |
| TechDraw_Image | Bitmap Image | Inserts a bitmap from a file into the current page |
| TechDraw_ShareView | Share View |  |
| TechDraw_ToggleFrame | Toggle View Frames | Toggles visibility of view frames and vertices |
| TechDraw_ProjectShape | Project Shape | Creates a projected geometry of the selected object in the 3D view from the current camera angle |
| TechDraw_ActiveView | Active View | Inserts an image of the open 3D view in the current page.\n |
| TechDraw_DraftView | Draft View | Inserts a view of a Draft object |
| TechDraw_ArchView | BIM View | Inserts a view of a BIM section plane |
| TechDraw_SpreadsheetView | Spreadsheet View | Inserts a view of a spreadsheet in the current page |
| TechDraw_Hatch | Image Hatch | Applies a hatch pattern to the selected faces using an image file |
| TechDraw_GeometricHatch | Geometric Hatch | Applies a geometric hatch pattern to the selected faces |
| TechDraw_WeldSymbol | Weld Symbol | Adds welding information to the selected leader line |
| TechDraw_SurfaceFinishSymbols | Surface Finish Symbol | Adds a surface finish symbol in the selected view |
| TechDraw_HoleShaftFit | Hole/Shaft Fit | TechDraw_HoleShaftFit |
| TechDraw_AlignVertexesVertically | Align Vertices/Edge Vertically | Aligns the selected vertices or edges vertically to the view rotation |
| TechDraw_AlignVertexesHorizontally | Align Vertices/Edge Horizontally | Aligns the selected vertices or edges horizontally to the view rotation |
| TechDraw_CosmeticEraser | Remove Cosmetic Object | Removes the selected cosmetic object from the page |
| TechDraw_SectionGroup | Section View (Simple or Complex) | Inserts a simple or complex section view in the current page |
| TechDraw_StackGroup | View Stacking Order | Adjusts the stacking order of the selected views |
| TechDraw_ExtentGroup | Extent Dimension | Inserts a dimension showing the extent (overall length) of an object or feature |
| TechDraw_ExtensionExtendShortenLineGroup | Extend Line | Extends a selected cosmetic line or centerline at both ends by the specified delta distance |
| TechDraw_ExtensionCircleCenterLinesGroup | Circle Centerlines | Adds centerlines to selected circles and arcs |
| TechDraw_ExtensionThreadsGroup | Cosmetic Thread Hole Side View | Adds a cosmetic thread to the side view of a selected hole between two selected parallel lines |
| TechDraw_CommandVertexCreationGroup | Cosmetic Intersection Vertices |  |
| TechDraw_ExtensionDrawCirclesGroup | Cosmetic 1 Point Circle | Adds a cosmetic circle based on two vertices, where the first selection is the centerpoint and the second is the radius |
| TechDraw_ExtensionLinePPGroup | Cosmetic Parallel Line | Adds a cosmetic line parallel to the selected line through the selected vertex |
| TechDraw_ExtensionCreateChainDimensionGroup | Horizontal Chain Dimension | Inserts a sequence of aligned horizontal dimensions to at least  |
| TechDraw_ExtensionCreateCoordDimensionGroup | Horizontal Coordinate Dimension | Adds evenly spaced horizontal dimensions between 3 or more vertices aligned to a shared baseline |
| TechDraw_ExtensionChamferDimensionGroup | Horizontal Chamfer Dimension | Inserts a horizontal size and angle dimension for a chamfer from 2 selected vertices |
| TechDraw_ExtensionInsertPrefixGroup | Insert '⌀' Prefix | Inserts a '⌀' symbol at the beginning of the dimension text |
| TechDraw_ExtensionIncreaseDecreaseGroup | Increase Decimal Places | Increases the number of decimal places of the dimension |
| TechDraw_CosmeticVertexGroup | Cosmetic Vertex | Inserts a cosmetic vertex |
| TechDraw_CenterLineGroup | Centerline | Inserts a centerline to a face, or between 2 lines or edges |
| TechDraw_ClipGroupAdd * | Add View To Clip Group | Adds the selected view to a clip group |
| TechDraw_ClipGroupRemove * | Remove From Clip Group | Removes a view based on the selected clip group |
| TechDraw_ExtensionPosChainDimensionGroup * | Align Horizontal Chain Dimensions | QT_TR_NOOP("Aligns the horizontal dimensions to create a chain dimension:<br>\ - Select two or more horizontal dimensions<br>\ - The first dimension defines the |
| TechDraw_ExtensionCascadeDimensionGroup * | Cascade Horizontal Dimensions | QT_TR_NOOP("Evenly spaces the selected horizontal dimensions:<br>\ - Specify the cascade spacing (optional)<br>\ - Select two or more horizontal dimensions<br>\ |
| TechDraw_ToggleGrid * | Toggle Grid | Toggles the grid on the active page |
| TechDraw_MoveView * | Move View |  |

## TemplatePyMod (15)

| Command | Menu text | Tooltip |
|---|---|---|
| Part_JoinConnect | Connect Shapes | Fuses shapes, taking care to preserve voids |
| TemplatePyGrp_1 |  |  |
| Part_JoinEmbed | Embed Shapes | Fuses one shape into another, taking care to preserve voids |
| TemplatePyGrp_2 |  |  |
| Part_JoinCutout | Cutout Shape | Creates a cutout in the selected shape to fit another shape |
| TemplatePyGrp_3 |  |  |
| Std_New | New Document | Creates a new empty document |
| TemplatePyMod_Cmd1 |  |  |
| TemplatePyMod_Cmd2 |  |  |
| TemplatePyMod_Cmd3 |  |  |
| TemplatePyMod_Cmd4 |  |  |
| TemplatePyMod_Cmd5 |  |  |
| TemplatePyMod_Cmd6 |  |  |
| TemplatePyGroup * |  |  |
| TemplatePyCheckable * |  |  |

## Std (core, all workbenches) (199)

| Command | Menu text | Tooltip |
|---|---|---|
| Std_LinkMakeGroup | Link Group | Creates a group of links |
| Std_LinkMake | Make Link | A link is an object that references another object, either within the same  |
| Std_LinkMakeRelative | Make Sub-Link | Creates a sub-object or sub-element link |
| Std_LinkReplace | Replace With Link | Replaces the selected objects with links |
| Std_LinkUnlink | Unlink | Unlinks the object by placing it directly in the container |
| Std_LinkImport | Import Links | Imports selected external links |
| Std_LinkImportAll | Import All Links | Imports all links of the active document |
| Std_LinkSelectLinked | Go to Linked Object | Selects the linked object and switches to its original document |
| Std_LinkSelectLinkedFinal | Go to Deepest Linked Object | Selects the deepest linked object and switches to its original document |
| Std_LinkSelectAllLinks | Select All Links | Selects all links to the current selected object |
| Std_DlgMacroRecord | Record Macro | Opens a dialog to record a macro |
| Std_DlgMacroExecute | Macros | Opens a dialog to execute a recorded macro |
| Std_DlgMacroExecuteDirect | Execute Macro | Executes the macro in the editor |
| Std_MacroAttachDebugger | Attach to Remote Debugger | Attaches to a remotely running debugger |
| Std_OpenMacrosFolder | Open Macro Folder | Opens the macros folder in the system file manager |
| Std_TileWindows | Tile | Tiles the windows |
| Std_CascadeWindows | Cascade | Tiles pragmatic |
| Std_CloseActiveWindow | Close | Closes the active window |
| Std_CloseAllWindows | Close All | Closes all windows |
| Std_ActivateNextWindow | Next | Activates the next window |
| Std_ActivatePrevWindow | Previous | Switches to the previously active window |
| Std_Windows | Choose Open Window | Displays the open windows |
| Std_UserInterface | Dock Views | Docks all top-level views |
| Std_DockViewMenu | Panels | Lists available dock panels |
| Std_ToolBarMenu | Toolbars | Toggles this window |
| Std_ToggleToolBarLock | Lock Toolbars | Locks toolbars so they are no longer moveable |
| Std_ViewStatusBar | Status Bar | Toggles the status bar |
| Std_WindowsMenu | Activate Window | Activates this window |
| Std_OpenGroup | Open… | Opens a document or imports files |
| Std_Open | Open… | Opens a document or imports files |
| Std_Import | Import… | Imports a file into the active document |
| Std_Export | Export… | Exports an object in the active document |
| Std_MergeProjects | Merge Document | Merges another FreeCAD document into the active one |
| Std_DependencyGraph | Dependency Graph | Shows the dependency graph of the objects in the active document |
| Std_ExportDependencyGraph | Export Dependency Graph… | Exports the dependency graph as a Graphviz (.gv) file |
| Std_New | New Document | Creates a new empty document |
| Std_Save | Save | Saves the active document |
| Std_SaveAs | Save As… | Saves the active document under a new file name |
| Std_SaveCopy | Save a Copy… | Saves a copy of the active document under a new file name |
| Std_SaveAll | Save All | Saves all open documents |
| Std_Revert | Revert | Reverts to the saved version of this file |
| Std_ProjectInfo | Document Information | Shows information about the active document |
| Std_ProjectUtil | Document Utility | Extracts or creates document files |
| Std_Print | Print | Prints the active document |
| Std_PrintPreview | Print Preview | Previews the active document before printing |
| Std_PrintPdf | Export PDF | Exports the active document as a PDF file |
| Std_Quit | Exit | Quits the application |
| Std_Undo | Undo | Undoes the previous action |
| Std_Redo | Redo | Redoes a previously undone action |
| Std_Cut | Cut | Removes the selection and copies it to the clipboard |
| Std_Copy | Copy | Copies the selection to the clipboard |
| Std_Paste | Paste | Pastes the contents of the clipboard |
| Std_DuplicateSelection | Duplicate Selection | Duplicates the selected objects to the active document |
| Std_SelectAll | Select All | Selects all objects in the active document |
| Std_Delete | Delete | Deletes the selected objects |
| Std_Refresh | Recompute | Recomputes the active document |
| Std_Transform | Transform | Transforms the selected object |
| Std_Placement | Placement | Opens the placement editor to adjust the placement of the selected object |
| Std_TransformManip | Transform | Transforms the selected object in the 3D view |
| Std_Alignment | Align To… | Aligns the selected objects |
| Std_Edit | Toggle Edit Mode | Toggles the selected object's edit mode |
| Std_Properties | Properties | Shows the property view, which displays the properties of the selected object. |
| Std_TestQM | Test Translation Files | Runs a test to verify .qm translation files |
| Std_TestReloadQM | Reload Translation Files | Reloads the translation files |
| Std_Test1 | Test1 | Test function 1 |
| Std_Test2 | Test2 | Test function 2 |
| Std_Test3 | Test3 | Test function 3 |
| Std_Test4 | Test4 | Test function 4 |
| Std_Test5 | Test5 | Test function 5 |
| Std_Test6 | Test6 | Test function 6 |
| Std_TestCmdFuncs | Test Functions | Test functions |
| Std_TestProgress1 | Breakable Bar | Test a breakable progress bar |
| Std_TestProgress2 | Unbreakable Bar | Test a unbreakable progress bar |
| Std_TestProgress3 | Nested Progress Bar | Test nested progress bar |
| Std_TestProgress4 | Mixed Nested Bar | Test a mixed up nested progress bar |
| Std_TestProgress5 | From Thread | Test a progress bar from a thread |
| Std_MDITest1 | Remove MDI 1 | Remove MDI from main window |
| Std_MDITest2 | Remove MDI 2 | Remove view from MDI area |
| Std_MDITest3 | Remove MDI 3 | Unset parent and remove from main window |
| Std_TestConsoleOutput | Test Console Output | Runs test cases to verify console messages |
| Std_OrthographicCamera | Orthographic View | Switches to orthographic view mode |
| Std_PerspectiveCamera | Perspective View | Switches to perspective view mode |
| Std_ViewSaveCamera | Save Current Camera | Saves the current camera settings |
| Std_ViewRestoreCamera | Restore Saved Camera | Restores the saved camera settings |
| Std_FreezeViews | Freeze Display | Freezes the current view position |
| Std_ToggleClipPlane | Clipping View | Toggles clipping of the active view |
| Std_DrawStyle | Draw Style | Changes the draw style of the objects |
| Std_ToggleVisibility | Toggle Visibility | Toggles the visibility of the selection |
| Std_ToggleTransparency | Toggle Transparency | toolTip.c_str() |
| Std_ToggleSelectability | Toggle Selectability | Toggles the property of the objects to get selected in the 3D view |
| Std_ShowSelection | Show Selection | Shows all selected objects |
| Std_HideSelection | Hide Selection | Hides all selected objects |
| Std_SelectVisibleObjects | Select Visible Objects | Selects all visible objects in the active document |
| Std_ToggleObjects | Toggle All Objects | Toggles the visibility of all objects in the active document |
| Std_ShowObjects | Show All Objects | Shows all objects in the document |
| Std_HideObjects | Hide All Objects | Hides all objects in the document |
| Std_ViewHome | Home | Sets the camera to the default home view |
| Std_ViewBottom | Bottom | Sets the camera to the bottom view |
| Std_ViewFront | Front | Sets the camera to the front view |
| Std_ViewLeft | Left | Sets the camera to the left view |
| Std_ViewRear | Rear | Sets the camera to the rear view |
| Std_ViewRight | Right | Sets the camera to the right view |
| Std_ViewTop | Top | Sets the camera to the top view |
| Std_ViewIsometric | Isometric | Sets the camera to the isometric view |
| Std_ViewDimetric | Dimetric | Sets the camera to the dimetric view |
| Std_ViewTrimetric | Trimetric | Sets the camera to the trimetric view |
| Std_ViewRotateLeft | Rotate Left | Rotates the view by 90\xc2\xb0 counter-clockwise |
| Std_ViewRotateRight | Rotates Right | Rotates the view by 90\xc2\xb0 clockwise |
| Std_ViewFitAll | Fit All | Fits all content into the 3D view |
| Std_ViewFitSelection | Fit Selection | Fits the selected content into the 3D view |
| Std_ViewDock | Docked | Displays the active view either in fullscreen, undocked, or docked mode |
| Std_ViewUndock | Undocked | Displays the active view either in fullscreen, undocked, or docked mode |
| Std_MainFullscreen | Fullscreen | Displays the main window in fullscreen mode |
| Std_ViewFullscreen | Fullscreen | Displays the active view either in fullscreen, undocked, or docked mode |
| Std_ViewDockUndockFullscreen | Document Window | Displays the active view either in fullscreen, undocked, or docked mode |
| Std_ViewScreenShot | Save Image… | Creates a screenshot of the active view |
| Std_ViewLoadImage | Load Image… | Loads an image |
| Std_ViewCreate | New 3D View | Opens a new 3D view window for the active document |
| Std_ToggleNavigation | Toggle Navigation/Edit Mode | Toggles between navigation and edit mode |
| Std_AxisCross | Toggle Axis Cross | Toggles the axis cross at the origin |
| Std_ViewExample1 | Inventor Example #1 | Shows a 3D texture with manipulator |
| Std_ViewExample2 | Inventor Example #2 | Shows spheres and drag-lights |
| Std_ViewExample3 | Inventor Example #3 | Shows an animated texture |
| Std_ViewIvIssueCamPos | Issue Camera Position | Issues the camera position to the console and to a macro, to easily recall this position |
| Std_ViewZoomIn | Zoom In | Increases the zoom factor by a fixed amount |
| Std_ViewZoomOut | Zoom Out | Decreases the zoom factor by a fixed amount |
| Std_ViewBoxZoom | Box Zoom | Activates the box zoom tool |
| Std_BoxSelection | Box Selection | Activates the box selection tool |
| Std_BoxElementSelection | Box Element Selection | Activates box element selection |
| Std_TreeSelection | Go to Selection | Scrolls to the first selected item |
| Std_TreeCollapse | Collapse Selected Items | Collapses the currently selected tree items |
| Std_TreeExpand | Expand Selected Items | Expands the currently selected tree items |
| Std_TreeSelectAllInstances | Select All Instances | Selects all instances of the currently selected object |
| Std_SceneInspector | Scene Inspector | Opens the scene inspector |
| Std_TextureMapping | Texture Mapping | Maps textures to shapes |
| Std_DemoMode | View Turntable | Opens a turntable view |
| Std_SelBack | Selection Back | toolTip.c_str() |
| Std_SelForward | Selection Forward | toolTip.c_str() |
| Std_TreeSingleDocument | Single Document | Displays only the active document in the tree view |
| Std_TreeMultiDocument | Multi Document | Displays all documents in the tree view |
| Std_TreeCollapseDocument | Collapse/Expand | Expands the active document and collapses all others |
| Std_TreeSyncView | Sync View | Switches to the 3D view containing the selected item from the tree view |
| Std_TreeSyncSelection | Sync Selection | Expands the tree item when the corresponding object is selected in the 3D view |
| Std_TreeSyncPlacement | Sync Placement | Adjusts the placement on drag-and-drop of objects across coordinate systems (e.g. in part containers) |
| Std_TreePreSelection | Preselection | Preselects the object in 3D view when hovering the cursor over the tree item |
| Std_TreeRecordSelection | Record Selection | Records the selection in the tree view in order to go back/forward using the navigation buttons |
| Std_TreeDrag | Initiate Dragging | Initiates dragging of the currently selected tree items |
| Std_SelBoundingBox | Bounding Box | Shows selection bounding box |
| Std_DockOverlayAll | Toggle Overlay for All Panels | Toggles overlay mode for all docked panels |
| Std_DockOverlayTransparentAll | Toggle Transparent Panels | Toggles transparent mode for all docked overlay panels.\n |
| Std_DockOverlayToggle | Toggle Overlay | Toggles overlay mode for the docked window under the cursor |
| Std_DockOverlayToggleTransparent | Toggle Transparent Mode | Toggles transparent mode for the docked panel under cursor.\n |
| Std_DockOverlayToggleLeft | Toggle Left | Toggles the visibility of the left overlay panel |
| Std_DockOverlayToggleRight | Toggle Right | Toggles the visibility of the right overlay panel |
| Std_DockOverlayToggleTop | Toggle Top | Toggles the visibility of the top overlay panel |
| Std_DockOverlayToggleBottom | Toggle Bottom | Toggles the visibility of the bottom overlay panel |
| Std_DockOverlayMouseTransparent | Bypass Mouse Events in Overlay Panels | Bypasses all mouse events in docked overlay panels |
| Std_ToggleBottomPanels | Toggle Bottom Panels | Toggles the bottom dock panels |
| Std_StoreWorkingView | Store Working View | Stores a temporary working view for the current document |
| Std_RecallWorkingView | Recall Working View | Recalls a previously stored temporary working view |
| Std_AlignToSelection | Align to Selection | Aligns the camera view to the selected elements in the 3D view |
| Std_ClarifySelection | Clarify Selection | Displays a context menu at the mouse cursor to select overlapping  |
| Std_Recompute | Recompute | Recomputes a feature or document |
| Std_RandomColor | Random Color | Assigns random diffuse colors for the selected objects |
| Std_ToggleFreeze | Toggle Freeze | toolTip.c_str() |
| Std_ToggleSuppress | Toggle Suppressed | toolTip.c_str() |
| Std_SendToPythonConsole | Send to Python Console | Sends the selected objects to the Python console |
| Std_ToggleSkipRecompute | Skip Recomputes | toolTip.c_str() |
| Std_Part | New Part | Creates a part, which is a general-purpose container to group objects so they  |
| Std_Group | New Group | Creates a group, which is a general-purpose container to group objects in the  |
| Std_VarSet | Variable Set | Creates a variable set, which is an object that maintains a set of properties to be used as variables |
| Std_Workbench | Workbench | Switches between workbenches |
| Std_RecentFiles | Open Recent | Displays the list of recently opened files |
| Std_RecentMacros | Recent Macros | Displays the list of recently used macros |
| Std_About | About %1 | Displays information about %1 |
| Std_AboutQt | About Qt | Displays information about Qt |
| Std_WhatsThis | What's This? | Opens the documentation for the selected command |
| Std_RestartInSafeMode | Restart in Safe Mode | Starts FreeCAD without any modules or plugins loaded |
| Std_DlgParameter | Edit Parameters | Opens a dialog to edit the parameters |
| Std_DlgPreferences | Preferences | Opens a dialog to edit the preferences |
| Std_DlgCustomize | Customize | Opens a dialog to edit toolbars, shortcuts, and macros |
| Std_CommandLine | Command Line | Opens a command line interface in the console |
| Std_OnlineHelp | Help | Opens the Help documentation |
| Std_OnlineHelpWebsite | Help Website | Opens the help documentation |
| Std_FreeCADDonation | Donate to FreeCAD | Opens the FreeCAD donation page |
| Std_DevHandbook | Developers Handbook | Opens the FreeCAD developers handbook |
| Std_FreeCADWebsite | FreeCAD Website | Navigates to the official FreeCAD website |
| Std_FreeCADUserHub | User Documentation | Opens the documentation for users |
| Std_FreeCADForum | FreeCAD Forum | Opens the FreeCAD forum to find help from other users |
| Std_ReportBug | Report an Issue | Opens the bugtracker to report an issue |
| Std_TextDocument | Text Document | Adds a text document to the active document |
| Std_UnitsCalculator | Units Converter | Starts the units converter |
| Std_UserEditMode | Edit Mode | Defines behavior when editing an object from the tree view |
| Std_ReloadStyleSheet | Reload Stylesheet | Reloads the current stylesheet |
| Std_AnnotationLabel | Annotation Label | Creates a new annotation label at the picked location in the 3D view |
| Std_SetAppearance | Appearance | Sets the display properties of the selected object |
| Std_SetMaterial | Material | Sets the material of the selected object |
| Std_Measure | Measure | Measures a feature |
| Std_MassProperties | Mass Properties | Calculates mass properties of selected objects |


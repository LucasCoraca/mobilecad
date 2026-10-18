# FreeCAD feature properties (from App sources)

## PartDesign::Body
- **AllowCompound** = `true` — Allow multiple solids in Body

## PartDesign::Line
- **ResizeMode** = `static_cast<long>(0` enum: Automatic, Manual — 
- **Length** = `20` — Length of the line

## PartDesign::Plane
- **ResizeMode** = `static_cast<long>(0` enum: Automatic, Manual — 
- **Length** = `20` — Length of the plane
- **Width** = `20` — Width of the plane

## PartDesign::Feature
- **BaseFeature** = `nullptr` — 
- **_Body** = `nullptr),
        "Base",
        (App::PropertyType)(App::P` — 

## PartDesign::FeatureAddSub
- **AddSubShape** = `TopoDS_Shape(` — 
- **Operation** = `false` — Boolean operation to compute

## PartDesign::FeatureBase
- **UseLegacyBaseFeaturePlacement** = `App::GetApplication().isRestoring(` — 

## PartDesign::Boolean
- **Type** = `(long)0` enum: Fuse, Cut, Common — 
- **UseLegacyBodyPlacement** = `App::GetApplication().isRestoring(` — 

## PartDesign::Chamfer
- **ChamferType** = `0L` enum: Equal distance, Two distances, Distance and Angle — Type of chamfer
- **Size** = `1.0` — Size of chamfer
- **Size2** = `1.0` — Second size of chamfer
- **Angle** = `45.0` — Angle of chamfer
- **FlipDirection** = `false` — Flip direction

## PartDesign::Draft
- **Angle** = `1.5` — 
- **NeutralPlane** = `nullptr` — NeutralPlane
- **PullDirection** = `nullptr` — PullDirection
- **Reversed** = `0` — 

## PartDesign::DressUp
- **Base** = `nullptr` — 

## PartDesign::FeatureExtrude
- **UseLegacyTaperDirection** = `false` — Use legacy profile copying for tapered extrusions

## PartDesign::Fillet
- **Radius** = `1.0` — Fillet radius.

## PartDesign::Groove
- **SideType** = `0L` — Type of side definition
- **Type** = `0L` — Groove type for side 1
- **Type2** = `0L` enum: Angle, ThroughAll, UpToFirst, UpToFace, ?TwoAngles — Groove type for side 2
- **Base** = `Base::Vector3d(` — 
- **Axis** = `Base::Vector3d::UnitY` — Axis
- **ProjectAxis** = `false` — Projects the reference axis onto the profile plane
- **Angle** = `fullAngle` — Angle
- **Angle2** = `emptyAngle` — Groove angle in 2nd direction
- **UpToFace** = `nullptr` — Face where groove will end
- **UpToFace2** = `nullptr` — Face where groove will end on side 2
- **ReferenceAxis** = `nullptr` — Reference axis of groove

## PartDesign::Helix
- **Base** = `Base::Vector3d(0.0, 0.0, 0.0` — 
- **Axis** = `Base::Vector3d(0.0, 1.0, 0.0` — 
- **ReferenceAxis** = `nullptr),
        group,
        App::Prop_None,
        QT_` — 
- **Mode** = `long(initialMode` enum: pitch-height-angle, pitch-turns-angle, height-turns-angle, height-turns-growth — 
- **Pitch** = `10.0),
        group,
        App::Prop_None,
        QT_TRA` — 
- **Height** = `30.0),
        group,
        App::Prop_None,
        QT_TRA` — 
- **Turns** = `3.0),
        group,
        App::Prop_None,
        QT_TRAN` — 
- **Angle** = `0.0),
        group,
        App::Prop_None,
        QT_TRAN` — 
- **Growth** = `0.0),
        group,
        App::Prop_None,
        QT_TRAN` — 
- **LeftHanded** = `false),
        group,
        App::Prop_None,
        QT_TR` — 
- **Reversed** = `false),
        group,
        App::Prop_None,
        QT_TR` — 
- **HasBeenEdited** = `false),
        group,
        App::Prop_Hidden,
        QT_` — 
- **Tolerance** = `0.1),
        group,
        App::Prop_None,
        QT_TRAN` — 
- **Outside** = `false),
        group,
        App::Prop_Hidden,
        QT_` — 

## PartDesign::Hole
- **Threaded** = `false` — Threaded
- **ModelThread** = `false` — Model actual thread
- **CosmeticThread** = `true` — Texture the thread
- **ThreadType** = `0L` enum: None, ISOMetricProfile, ISOMetricFineProfile, UNC, UNF, UNEF, NPT, BSP, BSW, BSF, ISOTyre — Thread type
- **ThreadSize** = `0L` — Thread size
- **ThreadClass** = `0L` enum: None — Thread class
- **ThreadFit** = `0L` enum: Medium, Fine, Coarse — Clearance hole fit
- **Diameter** = `6.0` — Diameter
- **ThreadDiameter** = `0.0` — Thread major diameter
- **ThreadDirection** = `0L` enum: Right, Left — Thread direction
- **HoleCutType** = `0L` enum: None, Counterbore, Countersink, Counterdrill — Head cut type
- **HoleCutCustomValues** = `false` — Custom cut values
- **HoleCutDiameter** = `0.0` — Head cut diameter
- **HoleCutDepth** = `0.0` — Head cut depth
- **HoleCutCountersinkAngle** = `90.0` — Head cut countersink angle
- **DepthType** = `0L` enum: Dimension, ThroughAll, UpToFirst — Type
- **Depth** = `25.0` — Length
- **DrillPoint** = `1L` enum: Flat, Angled — Drill point type
- **DrillPointAngle** = `118.0` — Drill point angle
- **DrillForDepth** = `(long)0` — The size of the drill point will be taken into\n account for the depth of blind holes
- **Tapered** = `false` — Tapered
- **TaperedAngle** = `90.0` — Tapered angle
- **ThreadDepthType** = `0L` enum: Hole Depth, Dimension, Tapped (DIN76) — Thread depth type
- **ThreadDepth** = `23.5` — Thread Length
- **UseCustomThreadClearance** = `false` — Use custom thread clearance
- **CustomThreadClearance** = `0.0` — Custom thread clearance (overrides ThreadClass)
- **BaseProfileType** = `BaseProfileTypeOptions::OnCirclesArcs` — Which profile feature to base the holes on
- **StartType** = `0L` — How to define the start plane
- **StartOffset** = `0.0` — Offset from the start plane
- **StartReference** = `nullptr` — Face, plane or sketch used as the start reference

## PartDesign::Loft
- **Sections** = `nullptr` — List of sections
- **Ruled** = `false` — Create ruled surface
- **Closed** = `false` — Close Last to First Profile

## PartDesign::Mirrored
- **MirrorPlane** = `nullptr` — Mirror plane

## PartDesign::MultiTransform
- **Transformations** = `nullptr` — 

## PartDesign::Pad
- **SideType** = `0L` — Type of sides definition
- **Type** = `0L` — Pad type for side 1
- **Type2** = `0L` enum: Length, UpToLast, UpToFirst, UpToFace, ?TwoLengths, UpToShape — Pad type for side 2
- **Length** = `10.0` — Pad length
- **Length2** = `10.0` — Pad length in 2nd direction
- **UseCustomVector** = `false` — Use custom vector for pad direction
- **Direction** = `Base::Vector3d(1.0, 1.0, 1.0` — 
- **ReferenceAxis** = `nullptr` — Reference axis of direction
- **AlongSketchNormal** = `true` — Measure pad length along the sketch normal direction
- **StartType** = `0L` — How to define the start plane
- **StartOffset** = `0.0` — Offset from the start plane
- **StartReference** = `nullptr` — Face, plane or sketch used as the start reference
- **UpToFace** = `nullptr` — Face where pad will end
- **UpToShape** = `nullptr` — Faces or shape(s) where pad will end
- **UpToFace2** = `nullptr` — Face where pad will end on side2
- **UpToShape2** = `nullptr` — Faces or shape(s) where pad will end on side2
- **Offset** = `0.0` — Offset from face in which pad will end
- **Offset2** = `0.0` — Offset from face in which pad will end on side 2
- **TaperAngle** = `0.0` — Taper angle
- **TaperAngle2** = `0.0` — Taper angle for 2nd direction

## PartDesign::Pipe
- **Sections** = `nullptr` — List of sections
- **Spine** = `nullptr` — Path to sweep along
- **SpineTangent** = `false` — Include tangent edges into path
- **AuxiliarySpine** = `nullptr` — Secondary path to orient sweep
- **AuxiliarySpineTangent** = `false` — Include tangent edges into secondary path
- **AuxiliaryCurvilinear** = `true` — Calculate normal between equidistant points on both spines
- **Mode** = `long(0` enum: Standard, Fixed, Frenet, Auxiliary, Binormal — 
- **Binormal** = `Base::Vector3d(` — 
- **Transition** = `long(0` enum: Transformed, Right corner, Round corner — 
- **Transformation** = `long(0` enum: Constant, Multisection, Linear, S-shape, Interpolation — 

## PartDesign::Pocket
- **SideType** = `0L` — Type of sides definition
- **Type** = `(long)0` — Pocket type
- **Type2** = `(long)0` enum: Length, ThroughAll, UpToFirst, UpToFace, ?TwoLengths, UpToShape — Pocket type
- **Length** = `5.0` — Pocket length
- **Length2** = `5.0` — Pocket length in 2nd direction
- **UseCustomVector** = `false` — Use custom vector for pocket direction
- **Direction** = `Base::Vector3d(1.0, 1.0, 1.0` — 
- **ReferenceAxis** = `nullptr` — Reference axis of direction
- **AlongSketchNormal** = `true` — Measure pocket length along the sketch normal direction
- **StartType** = `0L` — How to define the start plane
- **StartOffset** = `0.0` — Offset from the start plane
- **StartReference** = `nullptr` — Face, plane or sketch used as the start reference
- **UpToFace** = `nullptr` — Face where pocket will end
- **UpToShape** = `nullptr` — Face(s) or shape(s) where pocket will end
- **UpToFace2** = `nullptr` — Face where pocket will end
- **UpToShape2** = `nullptr` — Face(s) or shape(s) where pocket will end
- **Offset** = `0.0` — Offset from face in which pocket will end
- **Offset2** = `0.0` — Offset from face in which pocket will end on side 2
- **TaperAngle** = `0.0` — Taper angle
- **TaperAngle2** = `0.0` — Taper angle for 2nd direction

## PartDesign::Box
- **Length** = `10.0f` — The length of the box
- **Width** = `10.0f` — The width of the box
- **Height** = `10.0f` — The height of the box

## PartDesign::Cylinder
- **Radius** = `10.0f` — The radius of the cylinder
- **Angle** = `360.0f` — The closing angle of the cylinder 
- **Height** = `10.0f` — The height of the cylinder

## PartDesign::Sphere
- **Radius** = `5.0` — The radius of the sphere
- **Angle1** = `-90.0f` — The angle of the sphere
- **Angle2** = `90.0f` — The angle of the sphere
- **Angle3** = `360.0f` — The angle of the sphere

## PartDesign::Cone
- **Radius1** = `2.0` — The radius of the cone
- **Radius2** = `4.0` — The radius of the cone
- **Height** = `10.0` — The height of the cone
- **Angle** = `360.0` — The angle of the cone

## PartDesign::Ellipsoid
- **Radius1** = `2.0` — Radius in local Z-direction
- **Radius2** = `4.0` — Radius in local X-direction
- **Radius3** = `0.0` — Radius in local Y-direction\nIf zero, it is equal to Radius2
- **Angle1** = `-90.0f` — The angle of the ellipsoid
- **Angle2** = `90.0f` — The angle of the ellipsoid
- **Angle3** = `360.0f` — The angle of the ellipsoid

## PartDesign::Torus
- **Radius1** = `10.0` — Radius in local XY-plane
- **Radius2** = `2.0` — Radius in local XZ-plane
- **Angle1** = `-180.0` — The angle of the torus
- **Angle2** = `180.0` — The angle of the torus
- **Angle3** = `360.0` — The angle of the torus

## PartDesign::Prism
- **Polygon** = `6.0` — Number of sides in the polygon, of the prism
- **Circumradius** = `2.0` — Circumradius (centre to vertex) of the polygon, of the prism
- **Height** = `10.0f` — The height of the prism

## PartDesign::Wedge
- **Xmin** = `0.0f` — Xmin of the wedge
- **Ymin** = `0.0f` — Ymin of the wedge
- **Zmin** = `0.0f` — Zmin of the wedge
- **X2min** = `2.0f` — X2min of the wedge
- **Z2min** = `2.0f` — Z2min of the wedge
- **Xmax** = `10.0f` — Xmax of the wedge
- **Ymax** = `10.0f` — Ymax of the wedge
- **Zmax** = `10.0f` — Zmax of the wedge
- **X2max** = `8.0f` — X2max of the wedge
- **Z2max** = `8.0f` — Z2max of the wedge

## PartDesign::FeatureRefine
- **Refine** = `false` — Refine shape (clean up redundant edges) after operations
- **FuzzyTolerance** = `0.0),
        "Part Design",
        (App::PropertyType)(App` — 

## PartDesign::Revolution
- **SideType** = `0L` — Type of side definition
- **Type** = `0L` — Revolution type for side 1
- **Type2** = `0L` enum: Angle, UpToLast, UpToFirst, UpToFace, ?TwoAngles — Revolution type for side 2
- **Base** = `Base::Vector3d(` — 
- **Axis** = `Base::Vector3d::UnitY` — Axis
- **ProjectAxis** = `false` — Projects the reference axis onto the profile plane
- **Angle** = `fullAngle` — Angle
- **Angle2** = `emptyAngle` — Revolution angle in 2nd direction
- **UpToFace** = `nullptr` — Face where revolution will end
- **UpToFace2** = `nullptr` — Face where revolution will end on side 2
- **ReferenceAxis** = `nullptr` — Reference axis of revolution
- **FuseOrder** = `BaseFirst` enum: BaseFirst, FeatureFirst — Order of fuse operation to preserve compatibility with files created using FreeCAD 1.0

## PartDesign::Revolved
- **StartType** = `0L` — How to define the revolution start
- **StartOffset** = `0.0` — Angular offset from the profile or selected start reference
- **StartReference** = `nullptr` — Face, plane or sketch used as the start reference

## PartDesign::Scaled
- **Factor** = `2.0` — 
- **Occurrences** = `2` — 

## PartDesign::ProfileBased
- **Profile** = `nullptr` — Reference to sketch
- **Midplane** = `0` — Extrude symmetric to sketch face
- **Reversed** = `0` — Reverse extrusion direction
- **UpToFace** = `nullptr` — Face where feature will end
- **UpToShape** = `nullptr` — Shape where feature will end
- **UpToFace2** = `nullptr` — Face where feature will end
- **UpToShape2** = `nullptr` — Shape where feature will end
- **AllowMultiFace** = `false` — Allow multiple faces in profile

## PartDesign::Thickness
- **Value** = `1.0` — Thickness value
- **Mode** = `0L` enum: Skin, Pipe, RectoVerso — Mode
- **Join** = `0L` enum: Arc, Intersection — Join type
- **Reversed** = `true` — Apply the thickness towards the solids interior
- **Intersection** = `false` — Enable intersection-handling

## PartDesign::Transformed
- **Originals** = `nullptr` — 
- **TransformMode** = `static_cast<long>(Mode::Features` — 
- **SuppressedIndices** = `std::vector<long>(` — 

## PartDesign::ShapeBinder
- **Support** = `nullptr, nullptr` — Support of the geometry
- **TraceSupport** = `false` — Trace support shape

## PartDesign::SubShapeBinder
- **Support** = `nullptr` — Support of the geometry
- **Fuse** = `false` — Fuse solids from bound shapes
- **MakeFace** = `true` — Create face using wires from bound shapes
- **Offset** = `0.0` — 2D offset face or wires, 0.0 = no offset
- **OffsetJoinType** = `(long)0` enum: Arcs, Tangent, Intersection — Arcs, Tangent, Intersection
- **OffsetFill** = `false` — True = make face between original wire and offset.
- **OffsetOpenResult** = `false` — False = make closed offset from open wire.
- **OffsetIntersection** = `false` — False = offset child wires independently.
- **ClaimChildren** = `false` — Claim linked object as children
- **Relative** = `true` — Enable relative sub-object binding
- **BindMode** = `(long)0),
        "Base",
        App::Prop_None,
        "S` enum: Synchronized, Frozen, Detached — Refine shape (clean up redundant edges) after adding/subtracting
- **_Version** = `0` — 
- **_CopiedLink** = `nullptr` — 

## Part::AttachExtension
- **AttacherType** = `"Attacher::AttachEngine3D"` — Class name of attach engine object driving the attachment.
- **AttacherEngine** = `0L` — Attach engine object driving the attachment.
- **AttachmentSupport** = `nullptr, nullptr` — Support of the 2D geometry
- **MapMode** = `mmDeactivated` — Mode of attachment to other object
- **MapReversed** = `false` — Reverse Z-direction (flip sketch upside down)
- **MapPathParameter** = `0.0` — Sets point of curve to map the sketch to. 0..1 = start..end
- **AttachmentOffset** = `Base::Placement(` — 

## Part::BodyBase
- **Tip** = `nullptr` — 
- **BaseFeature** = `nullptr` — 

## Part::CircularPatternExtension
- **Axis** = `nullptr` — The axis and center of the circular pattern.
- **RadialDistance** = `50.0` — Distance between consecutive concentric circles.
- **TangentialDistance** = `25.0` — Approximate distance between consecutive elements on the same circle.
- **NumberCircles** = `3` — Number of concentric circles, including the original element.
- **Symmetry** = `1` — Number of symmetry divisions used to round each circle's element count.
- **StartAtShape** = `false` — Place the existing shape in the first circle, else use the axis' origin as center.
- **BinormalAxis** = `nullptr` — The axis representing the binormal of the first transformation on the first circle.
- **FlipBinormalAxis** = `false` — Flip the binormal axis' direction.

## Part::Compound
- **Links** = `nullptr` — 

## Part::Extrusion
- **Base** = `nullptr` — Shape to extrude
- **Dir** = `Base::Vector3d(0.0, 0.0, 1.0` — 
- **DirMode** = `dmCustom` enum: Custom, Edge, Normal — Sets, how Dir is updated.
- **DirLink** = `nullptr` — Link to edge defining extrusion direction.
- **LengthFwd** = `0.0` — Length of extrusion along direction. If both LengthFwd and LengthRev are zero, magnitude of Dir is used.
- **LengthRev** = `0.0` — Length of additional extrusion, against direction.
- **Solid** = `false` — If true, extruding a wire yields a solid. If false, a shell.
- **Reversed** = `false` — Set to true to swap the direction of extrusion.
- **Symmetric** = `false` — If true, extrusion is done in both directions to a total of LengthFwd. LengthRev is ignored.
- **TaperAngle** = `0.0` — Sets the angle of slope (draft) to apply to the sides. The angle is for outward taper; negative value yields inward tapering.
- **TaperAngleRev** = `0.0` — Taper angle of reverse part of extrusion.
- **FaceMakerClass** = `"Part::FaceMakerExtrusion"` — If Solid is true, this sets the facemaker class to use when converting wires to faces. Otherwise, ignored.
- **FaceMakerMode** = `3L` — If Solid is true, this sets the facemaker class to use when converting wires to faces. Otherwise, ignored.
- **InnerWireTaper** = `static_cast<long>(InnerWireTaper::Inverted` enum: Inverted, SameAsOuter — 

## Part::Face
- **Sources** = `nullptr` — 
- **FaceMakerClass** = `"Part::FaceMakerCheese"` — 

## Part::FeatureGeometrySet
- **GeometrySet** = `nullptr` — 

## Part::Mirroring
- **Source** = `nullptr` — 
- **Base** = `Base::Vector3d(` — 
- **Normal** = `Base::Vector3d(0, 0, 1` — 

## Part::Offset
- **Source** = `nullptr` — Source shape
- **Value** = `1.0` — Offset value
- **Mode** = `long(0` enum: Skin, Pipe, RectoVerso — 
- **Join** = `long(0` enum: Arc, Tangent, Intersection — 
- **Intersection** = `false` — Intersection
- **SelfIntersection** = `false` — Self Intersection
- **Fill** = `false` — Fill offset

## Part::Boolean
- **Base** = `nullptr` — 
- **Tool** = `nullptr` — 
- **History** = `ShapeHistory(` — 
- **Refine** = `0` — Refine shape (clean up redundant edges) after this boolean operation

## Part::Box
- **Length** = `10.0f` — The length of the box
- **Width** = `10.0f` — The width of the box
- **Height** = `10.0f` — The height of the box

## Part::Circle
- **Radius** = `2.0f` — 
- **Angle1** = `0.0f` — 
- **Angle2** = `360.0f` — 

## Part::MultiCommon
- **Shapes** = `nullptr` — 
- **History** = `ShapeHistory(` — 
- **Refine** = `0` — Refine shape (clean up redundant edges) after this boolean operation
- **Behavior** = `CommonOfAllShapes),
        "Compatibility",
        App::Pr` enum: CommonOfAllShapes, CommonOfFirstAndRest — 

## Part::CurveNet
- **FileName** = `""` — 

## Part::MultiFuse
- **Shapes** = `nullptr` — 
- **History** = `ShapeHistory(` — 
- **Refine** = `0` — Refine shape (clean up redundant edges) after this boolean operation

## Part::ImportBrep
- **FileName** = `""` — 

## Part::ImportIges
- **FileName** = `""` — 

## Part::ImportStep
- **FileName** = `""` — 

## Part::Polygon
- **Nodes** = `Base::Vector3d(` — 
- **Close** = `false` — 

## Part::Section
- **Approximation** = `false` — Approximate the output edges

## Part::ProjectOnSurface
- **Mode** = `0L` — Projection mode
- **Height** = `0.0` — Extrusion height
- **Offset** = `0.0` — Offset of solid
- **Direction** = `Base::Vector3d(0, 0, 1` — 
- **SupportFace** = `nullptr` — Support faceo
- **Projection** = `nullptr` — Shapes to project onto support face

## Part::Revolution
- **Source** = `nullptr` — Shape to revolve
- **Base** = `Base::Vector3d(0.0, 0.0, 0.0` — 
- **Axis** = `Base::Vector3d(0.0, 0.0, 1.0` — 
- **AxisLink** = `nullptr` — Link to edge to use as revolution axis.
- **Angle** = `360.0),
        "Revolve",
        App::Prop_None,
        "` — Extend revolution symmetrically from the profile.
- **Solid** = `false` — Make revolution a solid if possible
- **FaceMakerClass** = `""` — Facemaker to use if Solid is true.

## Part::Scale
- **Base** = `nullptr` — Shape to scale
- **Uniform** = `true` — If true, scale equally in all directions
- **UniformScale** = `1.0` — Uniform scale factor - 1.0 means no scaling
- **XScale** = `1.0` — Scale factor in X-direction - 1.0 means no scaling
- **YScale** = `1.0` — Scale factor in Y-direction - 1.0 means no scaling
- **ZScale** = `1.0` — Scale factor in Z-direction - 1.0 means no scaling

## Part::LinearPatternExtension
- **Direction** = `nullptr),
        "Direction 1",
        App::Prop_None,
   ` — Reverse the first direction of the pattern
- **Mode** = `long(initialMode` enum: Extent, Spacing — 
- **Length** = `100.0),
        "Direction 1",
        App::Prop_None,
     ` — The total number of instances in the first direction, including the original feature.
- **Direction2** = `nullptr),
        "Direction 2",
        App::Prop_None,
   ` — Reverse the second direction of the pattern
- **Mode2** = `long(initialMode` enum: Extent, Spacing — 
- **Length2** = `100.0),
        "Direction 2",
        App::Prop_None,
     ` — The total number of instances in the second direction, including the original feature.

## Part::Feature
- **Shape** = `TopoDS_Shape(` — 
- **ShapeMaterial** = `*mat` — 

## Part::FilletBase
- **Base** = `nullptr` — 
- **Edges** = `0, 0, 0` — 

## Part::FeatureReference
- **Reference** = `nullptr` — 

## Part::RuledSurface
- **Curve1** = `nullptr` — Curve of ruled surface
- **Curve2** = `nullptr` — Curve of ruled surface
- **Orientation** = `(long)0` enum: Automatic, Forward, Reversed — Orientation of ruled surface

## Part::Loft
- **Sections** = `nullptr` — List of sections
- **Solid** = `true` — Create solid
- **Ruled** = `false` — Ruled surface
- **Closed** = `false` — Close Last to First Profile
- **MaxDegree** = `5` — Maximum Degree
- **Linearize** = `false` — Linearize the result shape by simplifying linear edge and planar face into line and plane

## Part::Sweep
- **Sections** = `nullptr` — List of sections
- **Spine** = `nullptr` — Path to sweep along
- **Solid** = `true` — Create solid
- **Frenet** = `true` — Frenet
- **Transition** = `long(1` enum: Transformed, Right corner, Round corner — 
- **Linearize** = `false` — Linearize the result shape by simplifying linear edge and planar face into line and plane

## Part::Thickness
- **Faces** = `nullptr` — Faces to be removed
- **Value** = `1.0` — Thickness value
- **Mode** = `long(0` enum: Skin, Pipe, RectoVerso — 
- **Join** = `long(0` enum: Arc, Tangent, Intersection — 
- **Intersection** = `false` — Intersection
- **SelfIntersection** = `false` — Self Intersection

## Part::Refine
- **Source** = `nullptr` — Source shape

## Part::Reverse
- **Source** = `nullptr` — Source shape

## Part::PathPatternExtension
- **Path** = `nullptr` — Path edges used by the pattern.
- **Count** = `4` — Maximum number of occurrences.
- **SpacingMode** = `static_cast<long>(PathPatternSpacingMode::FixedCount` enum: Fixed count, Fixed spacing, Fixed count and spacing — 
- **Spacing** = `20.0` — Distance between occurrences.
- **StartOffset** = `0.0` — Unused distance at path start.
- **EndOffset** = `0.0` — Unused distance at path end.
- **ReversePath** = `false` — Traverses the path in reverse.
- **Align** = `false` — Aligns the occurrence X axes to the path.
- **VerticalVector** = `Base::Vector3d::UnitZ` — Preferred local Z direction when aligning occurrences.

## Part::PointPatternExtension
- **PointObject** = `nullptr` — Sketch or shape object whose vertices define the pattern positions.
- **OriginPointIndex** = `0` — The origin point index, used for the identity transformation.
- **RelativeToFirst** = `false` — Moves all transformations relative to the first one.

## Part::PolarPatternExtension
- **Axis** = `nullptr),
        "PolarPattern",
        App::Prop_None,
  ` — Reverses the pattern direction from counter-clockwise (default) to clockwise.
- **Mode** = `long(initialMode` — 
- **Angle** = `360.0),
        "PolarPattern",
        App::Prop_None,
    ` — The total number of instances in the pattern, including the original feature.

## Part::PreviewExtension
- **PreviewShape** = `TopoShape(` — 

## Part::Vertex
- **X** = `0.0f` — 
- **Y** = `0.0f` — 
- **Z** = `0.0f` — 

## Part::Line
- **X1** = `0.0` — X value of the start vertex
- **Y1** = `0.0` — Y value of the start vertex
- **Z1** = `0.0` — Z value of the start vertex
- **X2** = `0.0` — X value of the finish vertex
- **Y2** = `0.0` — Y value of the finish vertex
- **Z2** = `1.0` — Z value of the finish vertex

## Part::Plane
- **Length** = `100.0f` — The length of the plane
- **Width** = `100.0f` — The width of the plane

## Part::Sphere
- **Radius** = `5.0` — The radius of the sphere
- **Angle1** = `-90.0f` — The angle of the sphere
- **Angle2** = `90.0f` — The angle of the sphere
- **Angle3** = `360.0f` — The angle of the sphere

## Part::Ellipsoid
- **Radius1** = `2.0` — The radius of the ellipsoid
- **Radius2** = `4.0` — The radius of the ellipsoid
- **Radius3** = `0.0` — The radius of the ellipsoid
- **Angle1** = `-90.0f` — The angle of the ellipsoid
- **Angle2** = `90.0f` — The angle of the ellipsoid
- **Angle3** = `360.0f` — The angle of the ellipsoid

## Part::Cylinder
- **Radius** = `2.0` — The radius of the cylinder
- **Height** = `10.0f` — The height of the cylinder
- **Angle** = `360.0f` — The rotation angle of the cylinder

## Part::Prism
- **Polygon** = `6.0` — Number of sides in the polygon, of the prism
- **Circumradius** = `2.0` — Circumradius (centre to vertex) of the polygon, of the prism
- **Height** = `10.0f` — The height of the prism

## Part::RegularPolygon
- **Polygon** = `6.0` — Number of sides in the regular polygon
- **Circumradius** = `2.0` — Circumradius (centre to vertex) of the polygon

## Part::Cone
- **Radius1** = `2.0` — The radius of the cone
- **Radius2** = `4.0` — The radius of the cone
- **Height** = `10.0` — The height of the cone
- **Angle** = `360.0` — The angle of the cone

## Part::Torus
- **Radius1** = `10.0` — The radius of the torus
- **Radius2** = `2.0` — The radius of the torus
- **Angle1** = `-180.0` — The angle of the torus
- **Angle2** = `180.0` — The angle of the torus
- **Angle3** = `360.0` — The angle of the torus

## Part::Helix
- **Pitch** = `1.0` — The pitch of the helix
- **Height** = `2.0` — The height of the helix
- **Radius** = `1.0` — The radius of the helix
- **SegmentLength** = `0.0` — The number of turns per helix subdivision
- **Angle** = `0.0` — If angle is != 0 a conical otherwise a cylindircal surface is used
- **LocalCoord** = `long(0` enum: Right-handed, Left-handed — 
- **Style** = `long(0` enum: Old style, New style — 
- **Length** = `1.0` — The length of the helix

## Part::Spiral
- **Growth** = `1.0` — The growth of the spiral per rotation
- **Radius** = `1.0` — The radius of the spiral
- **Rotations** = `2.0` — The number of rotations
- **SegmentLength** = `1.0` — The number of turns per spiral subdivision
- **Length** = `1.0` — The length of the spiral

## Part::Wedge
- **Xmin** = `0.0f` — Xmin of the wedge
- **Ymin** = `0.0f` — Ymin of the wedge
- **Zmin** = `0.0f` — Zmin of the wedge
- **X2min** = `2.0f` — X2min of the wedge
- **Z2min** = `2.0f` — Z2min of the wedge
- **Xmax** = `10.0f` — Xmax of the wedge
- **Ymax** = `10.0f` — Ymax of the wedge
- **Zmax** = `10.0f` — Zmax of the wedge
- **X2max** = `8.0f` — X2max of the wedge
- **Z2max** = `8.0f` — Z2max of the wedge

## Part::Ellipse
- **MajorRadius** = `4.0f` — 
- **MinorRadius** = `4.0f` — 
- **Angle1** = `0.0f` — 
- **Angle2** = `360.0f` — 

## Part::PrismExtension
- **FirstAngle** = `0.0f` — Angle in first direction
- **SecondAngle** = `0.0f` — Angle in second direction

## Sketcher::SketchObject
- **Geometry** = `nullptr` — Sketch geometry
- **Constraints** = `nullptr` — Sketch constraints
- **ExternalGeometry** = `nullptr, nullptr` — Sketch external geometry
- **ExternalTypes** = `{}` — Sketch external geometry type: 0 = projection, 1 = intersection, 2 = both.
- **FullyConstrained** = `false` — Sketch is fully constrained
- **Exports** = `nullptr` — Sketch export geometry
- **ExternalGeo** = `nullptr` — Sketch external geometry
- **ArcFitTolerance** = `0.0` — Tolerance for fitting arcs of projected external geometry
- **InternalShape** = `Part::TopoShape(` — 
- **MakeInternals** = `false` — Enables selection of closed profiles within a sketch as input for operations
- **_InternalFaceVersion** = `0` — 

## Sketcher::SketchObjectSF
- **SketchFlatFile** = `nullptr` — SketchFlat file (*.skf) which defines this sketch

## Surface::Cut
- **ShapeList** = `nullptr, "TopoDS_Shape"` — 

## Surface::Extend
- **Face** = `nullptr` — 
- **Tolerance** = `0.1` — 
- **ExtendUNeg** = `0.05` — 
- **ExtendUPos** = `0.05` — 
- **ExtendUSymetric** = `true` — 
- **ExtendVNeg** = `0.05` — 
- **ExtendVPos** = `0.05` — 
- **ExtendVSymetric** = `true` — 
- **SampleU** = `32` — 
- **SampleV** = `32` — 

## Surface::Filling
- **BoundaryEdges** = `nullptr,""` — Boundary Edges (C0 is required for edges without a corresponding face)
- **BoundaryFaces** = `""` — Boundary Faces
- **BoundaryOrder** = `-1` — Order of constraint on boundary faces (C0, G1 and G2 are possible)
- **UnboundEdges** = `nullptr,""` — Unbound constraint edges (C0 is required for edges without a corresponding face)
- **UnboundFaces** = `""` — Unbound constraint faces
- **UnboundOrder** = `-1` — Order of constraint on curve faces (C0, G1 and G2 are possible)
- **FreeFaces** = `nullptr,""` — Free constraint on a face
- **FreeOrder** = `0` — Order of constraint on free faces
- **Points** = `nullptr,""` — Constraint Points (on Surface)
- **InitialFace** = `nullptr` — Initial surface to use
- **Degree** = `3` — Starting degree
- **PointsOnCurve** = `15` — Number of points on an edge for constraint
- **Iterations** = `2` — Number of iterations
- **Anisotropy** = `false` — Anisotropy
- **Tolerance2d** = `0.00001` — 2D Tolerance
- **Tolerance3d** = `0.0001` — 3D Tolerance
- **TolAngular** = `0.01` — G1 tolerance
- **TolCurvature** = `0.1` — G2 tolerance
- **MaximumDegree** = `8` — Maximum curve degree
- **MaximumSegments** = `9` — Maximum number of segments

## Surface::GeomFillSurface
- **FillType** = `(long)0` enum: Stretched, Coons, Curved — 
- **BoundaryList** = `nullptr, "Dummy"` — 
- **ReversedList** = `false` — 

## Surface::Sections
- **NSections** = `nullptr` — Section curves

## Surface::Sewing
- **ShapeList** = `nullptr, ""` — Input shapes
- **Tolerance** = `Precision::Confusion(` — 
- **SewingOption** = `true` — Sewing option
- **DegenerateShape** = `true` — Analysis of degenerated shapes
- **CutFreeEdges** = `true` — Cutting of free edges
- **Nonmanifold** = `false` — Non-manifold processing

## TechDraw::CosmeticExtension
- **CosmeticVertexes** = `nullptr), cgroup, App::Prop_Output, "CosmeticVertex Save/Res` — 

## TechDraw::DrawGeomHatch
- **Source** = `nullptr), vgroup, App::PropertyType::Prop_None,
            ` — 
- **PatIncluded** = `""), vgroup, App::Prop_None,
                      "Embedded` — 

## TechDraw::DrawLeaderLine
- **LeaderParent** = `nullptr), group, (App::PropertyType)(App::Prop_None),
      ` — 
- **StartSymbol** = `FilledArrow` — 
- **EndSymbol** = `NoEnd` — 
- **Scalable** = `false), group, App::Prop_None, "Scale line with LeaderParent` — 

## TechDraw::DrawProjGroup
- **Source** = `nullptr), group, App::Prop_None, "Shape to view");
    Sourc` — 

## TechDraw::DrawProjGroupItem
- **Type** = `(long)0` enum: DrawProjGroupItem, Front, DrawProjGroupItem, Left, DrawProjGroupItem, Right, DrawProjGroupItem, Rear, DrawProjGroupItem, Top, DrawProjGroupItem, Bottom, DrawProjGroupItem, FrontTopLeft, DrawProjGroupItem, FrontTopRight, DrawProjGroupItem, FrontBottomLeft, DrawProjGroupItem, FrontBottomRight — 
- **RotationVector** = `1.0, 0.0, 0.0` — Deprecated. Use XDirection.

## TechDraw::DrawTemplate
- **Orientation** = `0l` enum: Portrait, Landscape — 

## TechDraw::DrawTileWeld
- **LeftText** = `""), group, (App::PropertyType)(App::Prop_None),
           ` — 

## TechDraw::DrawView
- **X** = `0.0), group, App::Prop_None, "X position");
    ADD_PROPERTY` — 
- **Scale** = `prefScale(` — 
- **Caption** = `""), group, App::Prop_Output, "Short text about the view");
` — 

## TechDraw::DrawViewAnnotation
- **Text** = `"Default Text"), vgroup, App::Prop_None, "Annotation text");` — 
- **TextColor** = `Preferences::normalColor(` — 
- **TextSize** = `Preferences::labelFontSizeMM(` — 

## TechDraw::DrawViewBalloon
- **Text** = `""` — The text to be displayed
- **SourceView** = `nullptr` — Source view for balloon
- **OriginX** = `0` — Balloon origin x
- **OriginY** = `0` — Balloon origin y
- **EndType** = `static_cast<int>(Preferences::balloonArrow(` — 
- **EndTypeScale** = `1.0` — End symbol scale factor
- **BubbleShape** = `Preferences::balloonShape(` enum: Circular, None, Triangle, Inspection, Hexagon, Square, Rectangle, Line — 
- **ShapeScale** = `1.0` — Balloon shape scale
- **TextWrapLen** = `-1` — Text wrap length; -1 means no wrap
- **KinkLength** = `Preferences::balloonKinkLength(` — 

## TechDraw::DrawViewDetail
- **BaseView** = `nullptr), dgroup, App::Prop_None,
                      "2D ` — 
- **ShowHighlight** = `Preferences::showDetailHighlight(` — 

## TechDraw::DrawViewDimension
- **References2D** = `nullptr, nullptr` — Projected Geometry References
- **References3D** = `nullptr, nullptr` — 3D Geometry References
- **FormatSpec** = `getDefaultFormatSpec(` — 
- **FormatSpecOverTolerance** = `getDefaultFormatSpec(true` — 
- **FormatSpecUnderTolerance** = `getDefaultFormatSpec(true` — 
- **Arbitrary** = `false` — Value overridden by user
- **ArbitraryTolerances** = `false` — Tolerance values overridden by user
- **Type** = `(long)0` enum: Distance, DistanceX, DistanceY, DistanceZ, Radius, Diameter, Angle, Angle3Pt, Area — 
- **MeasureType** = `(long)1` enum: True, Projected — 
- **TheoreticalExact** = `false` — If theoretical exact (basic) dimension
- **EqualTolerance** = `true` — If over- and undertolerance are equal
- **OverTolerance** = `0.0),
                      "",
                      App::P` — The dimensional value is displayed inverted
- **ShowSupplementary** = `false` — Toggle supplementary angle\nAngle displayed is dependent on selection order
- **AngleOverride** = `false` — User specified angles
- **LineAngle** = `0.0` — Dimension line angle
- **ExtensionAngle** = `0.0` — Extension line angle
- **UseAreaLeaderPoint** = `false` — If true, the area leader end pt uses user clicked pt instead of face center.
- **AreaLeaderPoint** = `Base::Vector3d(0,0,0` — 
- **SavedGeometry** = `` — Reference Geometry
- **BoxCorners** = `` — Feature bounding box corners as of last reference update.  Used by autocorrect
- **UseActualArea** = `true` — If true, area dimensions return the area of the face minus the areas of any enclosed faces. \                        If false, the area of the face's 
- **ShowUnits** = `Preferences::showUnits(` — 

## TechDraw::DrawViewPart
- **Source** = `nullptr), group, App::Prop_None, "3D Shape to view");
    So` — 
- **CoarseView** = `Preferences::getPreferenceGroup("General")->GetBool("CoarseV` — 
- **SmoothVisible** = `Preferences::getPreferenceGroup("HLR")->GetBool("SmoothViz",` — 
- **SeamVisible** = `Preferences::getPreferenceGroup("HLR")->GetBool("SeamViz", f` — 
- **IsoVisible** = `Preferences::getPreferenceGroup("HLR")->GetBool("IsoViz", fa` — 
- **HardHidden** = `Preferences::getPreferenceGroup("HLR")->GetBool("HardHid", f` — 
- **SmoothHidden** = `Preferences::getPreferenceGroup("HLR")->GetBool("SmoothHid",` — 
- **SeamHidden** = `Preferences::getPreferenceGroup("HLR")->GetBool("SeamHid", f` — 
- **IsoHidden** = `Preferences::getPreferenceGroup("HLR")->GetBool("IsoHid", fa` — 
- **IsoCount** = `Preferences::getPreferenceGroup("HLR")->GetBool("IsoCount", ` — 
- **ScrubCount** = `Preferences::scrubCount(` — 

## TechDraw::DrawViewSection
- **SectionSymbol** = `""),
                      sgroup,
                      App` — 
- **CutSurfaceDisplay** = `prefCutSurface(` enum: Hide, Color, SvgHatch, PatHatch — 
- **FileHatchPattern** = `DrawHatch::prefSvgHatch(` — 
- **FileGeomPattern** = `DrawGeomHatch::prefGeomHatchFile(` — 
- **SvgIncluded** = `""),
                      fgroup,
                      App` — 
- **HatchScale** = `1.0), fgroup, App::Prop_None, "Hatch pattern size adjustment` — 

## TechDraw::DrawViewSpreadsheet
- **Source** = `nullptr), vgroup, App::Prop_None, "Spreadsheet to view");
  ` — 

## TechDraw::FeatureProjection
- **Source** = `nullptr), group, App::Prop_None, "Shape to project");
    AD` — 

## Assembly::AssemblyLink
- **Rigid** = `true),
        "General",
        (App::PropertyType)(App::P` — The linked assembly.

## Assembly::BomObject
- **columnsNames** = `"Index"` — List of the columns of the Bill of Materials.
- **detailSubAssemblies** = `true` — Detail sub-assemblies components.
- **detailParts** = `true` — Detail Parts sub-components.
- **onlyParts** = `false` — Only Part containers will be added. Solids like PartDesign Bodies will be ignored.

## Spreadsheet::Sheet
- **cells** = `` — Cell contents
- **columnWidths** = `` — Column widths
- **rowHeights** = `` — Row heights
- **rowHeights** = `` — Row heights

## Mesh::Curvature
- **Source** = `nullptr` — 
- **CurvInfo** = `CurvatureInfo(` — 

## Mesh::FixDefects
- **Source** = `nullptr` — 
- **Epsilon** = `0` — 

## Mesh::FixDeformations
- **MaxAngle** = `5.0F` — 

## Mesh::FillHoles
- **FillupHolesOfLength** = `0` — 
- **MaxArea** = `0.1F` — 

## Mesh::RemoveComponents
- **RemoveCompOfSize** = `0` — 

## Mesh::Export
- **Source** = `nullptr` — 
- **FileName** = `""` — 
- **Format** = `""` — 

## Mesh::Import
- **FileName** = `""` — 

## Mesh::SegmentByMesh
- **Source** = `nullptr` — 
- **Tool** = `nullptr` — 
- **Base** = `0.0, 0.0, 0.0` — 
- **Normal** = `0.0, 0.0, 1.0` — 

## Mesh::SetOperations
- **Source1** = `nullptr` — 
- **Source2** = `nullptr` — 
- **OperationType** = `"union"` — 

## Mesh::Sphere
- **Radius** = `5.0` — 
- **Sampling** = `50` — 

## Mesh::Ellipsoid
- **Radius1** = `2.0` — 
- **Radius2** = `4.0` — 
- **Sampling** = `50` — 

## Mesh::Cylinder
- **Radius** = `2.0` — 
- **Length** = `10.0` — 
- **EdgeLength** = `1.0` — 
- **Closed** = `true` — 
- **Sampling** = `50` — 

## Mesh::Cone
- **Radius1** = `2.0` — 
- **Radius2** = `4.0` — 
- **Length** = `10.0` — 
- **EdgeLength** = `1.0` — 
- **Closed** = `true` — 
- **Sampling** = `50` — 

## Mesh::Torus
- **Radius1** = `10.0` — 
- **Radius2** = `2.0` — 
- **Sampling** = `50` — 

## Mesh::Cube
- **Length** = `10.0F` — The length of the cube
- **Width** = `10.0F` — The width of the cube
- **Height** = `10.0F` — The height of the cube

## Mesh::Transform
- **Source** = `nullptr` — 
- **Position** = `Matrix4D(` — 

## Mesh::TransformDemolding
- **Source** = `nullptr` — 
- **Rotation** = `0.0` — 
- **Axis** = `0.0, 0.0, 1.0` — 

## Mesh::Feature
- **Mesh** = `MeshObject(` — 


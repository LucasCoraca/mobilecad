"""command_types.json for the app's generic command path: FreeCAD commands whose activated() creates exactly one object type
and whose purpose *is* that object (reviewed list of exclusions below), restricted to modules built for Android."""
import json, sys
par = json.load(open(sys.argv[1]))['rows']
BUILT = {'Assembly', 'Part', 'PartDesign', 'Sketcher', 'Spreadsheet', 'Surface', 'TechDraw', 'Std', 'Mesh', 'Points', 'Draft', 'Measure', 'Material', 'Inspection', 'ReverseEngineering'}
EXCLUDE = {  # helpers / result-computing / mis-detected (see research/PARITY.md)
    'BIM_Trash', 'BIM_Diff', 'BIM_Rewire', 'BIM_Reextrude', 'Draft_AddConstruction', 'Arch_SplitMesh', 'Part_MakeSolid', 'Part_Defeaturing',
    'Part_SimpleCylinder', 'Part_Tube', 'Mesh_Union', 'Mesh_Intersection', 'Mesh_Difference', 'Reen_ViewTriangulation', 'PartDesign_Clone',
    'PartDesign_Migrate', 'PartDesign_CompPrimitiveAdditive', 'PartDesign_CompPrimitiveSubtractive', 'TechDraw_SpreadsheetView',
    'TechDraw_AxoLengthDimension', 'TechDraw_View', 'Std_TestCmdFuncs', 'Std_LinkMakeGroup', 'Std_LinkMakeRelative', 'TemplatePyMod_Cmd2',
    'Sketcher_MergeSketches', 'Sketcher_MirrorSketch', 'Reen_ApproxPlane', 'Reen_ApproxCylinder', 'Reen_ApproxSphere', 'Reen_ApproxPolynomial',
    'Part_ImportCurveNet', 'Std_AnnotationLabel'}
SKIP_PREFIX = ('TechDraw_',)   # views must be added to a DrawPage: handled by the Drawing tab, not the generic path
out = {}
for n, r in par.items():
    if r['kind'] != 'creates' or len(r['what']) != 1 or n in EXCLUDE: continue
    if r['wb'].split(' ')[0] not in BUILT: continue
    if 'FeaturePython' in r['what'][0] or n.startswith(SKIP_PREFIX): continue
    out[n] = r['what'][0]
json.dump(out, open(sys.argv[2], 'w'), indent=0, sort_keys=True)
print(len(out), sorted(out))

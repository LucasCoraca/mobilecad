"""Assembly workbench ops for the mobile Assembly tab.  Mirrors src/Mod/Assembly/Command*.py and JointObject.py:
the same objects (Assembly::AssemblyObject, Assembly::JointGroup, App::Link, JointObject.Joint / GroundedJoint) and the
same reference format ([component, ['Link.Face3', 'Link.Vertex2']]) built with FreeCAD's own UtilsAssembly helpers."""
import FreeCAD as App
from .bridge import op, _doc, _assembly_of

def _asm(name=None):
    d = _doc()
    if name: return d.getObject(name)
    for o in d.Objects:
        if o.TypeId == 'Assembly::AssemblyObject': return o
    return None

@op
def asm_create():
    """Assembly_CreateAssembly (CommandCreateAssembly.py)."""
    assembly = _doc().addObject('Assembly::AssemblyObject', 'Assembly')
    assembly.Type = 'Assembly'
    assembly.newObject('Assembly::JointGroup', 'Joints')
    _doc().recompute()
    return assembly.Name

@op
def asm_parts():
    """Insertable parts (Assembly_InsertLink's list): bodies, Part features and App::Parts outside assemblies."""
    out = []
    for o in _doc().Objects:
        if o.TypeId in ('PartDesign::Body', 'App::Part') or (o.isDerivedFrom('Part::Feature') and not any(p.TypeId in ('PartDesign::Body', 'App::Part') for p in o.InList)):
            if _assembly_of(o): continue
            if o.TypeId.startswith('PartDesign::') and o.TypeId != 'PartDesign::Body': continue
            out.append({'name': o.Name, 'label': o.Label, 'type': o.TypeId})
    return out

@op
def asm_insert(part, assembly=None, ground=None):
    """Assembly_InsertLink: App::Link to the part; the first inserted part is grounded (desktop behaviour)."""
    import JointObject, UtilsAssembly
    asm = _asm(assembly); src = _doc().getObject(part)
    link = asm.newObject('App::Link', src.Label)
    link.LinkedObject = src
    link.Label = src.Label
    joints = UtilsAssembly.getJointGroup(asm)
    if ground if ground is not None else not any(getattr(j, 'ObjectToGround', None) is not None for j in joints.Group):
        g = joints.newObject('App::FeaturePython', 'GroundedJoint')
        JointObject.GroundedJoint(g, link)
    _doc().recompute()
    return link.Name

def _ref(asm, obj, sub, point):
    """TaskAssemblyCreateJoint.addSelection: component reference + closest vertex."""
    import UtilsAssembly
    root = asm.Document.getObject(obj) if obj != asm.Name else asm
    sub_name = sub if root == asm else '%s.%s' % (obj, sub)
    comp, new_sub = UtilsAssembly.getComponentReference(asm, asm, sub_name)
    if not comp: raise RuntimeError('%s.%s is not part of the assembly' % (obj, sub))
    ref = [comp, [new_sub]]
    if point is None:
        # keep an existing connector: its vertex sub-name is the element itself (as for faces on desktop)
        ref = [comp, [new_sub, new_sub]]
    else:
        vertex = UtilsAssembly.findElementClosestVertex(ref, App.Vector(*point))
        ref = UtilsAssembly.addVertexToReference(ref, vertex)
    return ref

@op
def asm_joint(jtype, refs, assembly=None):
    """Assembly_CreateJoint<Type>: refs = [[linkName, 'Face3', [x,y,z]], ...] (2 references)."""
    import JointObject, UtilsAssembly
    asm = _asm(assembly)
    joints = UtilsAssembly.getJointGroup(asm)
    joint = joints.newObject('App::FeaturePython', 'Joint')
    joint.Label = jtype
    JointObject.Joint(joint, JointObject.JointTypes.index(jtype))
    joint.Proxy.setJointConnectors(joint, [_ref(asm, o, s, p) for o, s, p in refs])
    _doc().recompute()
    return joint.Name

@op
def asm_set_refs(joint, refs, assembly=None):
    """Re-pick the joint's references (the joint dialog's selection rows)."""
    asm = _asm(assembly); j = _doc().getObject(joint)
    j.Proxy.setJointConnectors(j, [_ref(asm, o, s, p) for o, s, p in refs])
    _doc().recompute(); return True

@op
def asm_ground(link, assembly=None):
    """Assembly_ToggleGrounded."""
    import JointObject, UtilsAssembly
    asm = _asm(assembly); joints = UtilsAssembly.getJointGroup(asm); obj = _doc().getObject(link)
    for j in joints.Group:
        if getattr(j, 'ObjectToGround', None) == obj:
            _doc().removeObject(j.Name); _doc().recompute(); return False
    g = joints.newObject('App::FeaturePython', 'GroundedJoint'); JointObject.GroundedJoint(g, obj)
    _doc().recompute(); return True

@op
def asm_solve(assembly=None):
    """Assembly_SolveAssembly (AssemblyObject::solve -> OndselSolver)."""
    asm = _asm(assembly); r = asm.solve(); _doc().recompute(); return r

@op
def asm_drag(link, dx, dy, dz, assembly=None):
    """Free-drag a component like the desktop dragger: move it, then let the solver satisfy the joints."""
    asm = _asm(assembly); o = _doc().getObject(link)
    p = o.Placement; p.Base = p.Base + App.Vector(dx, dy, dz); o.Placement = p
    r = asm.solve(); _doc().recompute(); return r

@op
def asm_info(assembly=None):
    asm = _asm(assembly)
    if asm is None: return None
    import UtilsAssembly
    joints = UtilsAssembly.getJointGroup(asm)
    return {'name': asm.Name, 'components': [o.Name for o in asm.Group if o.TypeId == 'App::Link'],
            'joints': [{'name': j.Name, 'label': j.Label, 'type': getattr(j, 'JointType', 'Grounded'),
                        'grounded': getattr(j, 'ObjectToGround', None).Name if getattr(j, 'ObjectToGround', None) else None}
                       for j in joints.Group]}

@op
def asm_select(kind, sel=None):
    """Assembly_SelectConflicting/Redundant/MalformedConstraints, SelectComponentsWithDoFs, SelectJointsOfComponent,
    LinkSelectLinked (Assembly/Gui/Commands.cpp): object names to select, from the last solve's diagnostics."""
    d = _doc()
    asm = next((o for o in d.Objects if o.TypeId == 'Assembly::AssemblyObject'), None)
    if asm is None: raise RuntimeError('No assembly')
    def joints():
        jg = next((o for o in asm.Group if o.TypeId == 'Assembly::JointGroup'), None)
        return list(jg.Group) if jg else []
    if kind in ('Conflicting', 'Redundant', 'Malformed', 'PartiallyRedundant'):
        names = list(getattr(asm, 'Last' + kind, []) or [])
        return [n for n in names if d.getObject(n) is not None]
    if kind == 'ComponentsWithDoFs':                 # components not connected to the grounded parts
        return [o.Name for o in asm.Group if o.TypeId in ('App::Link', 'App::Part', 'Part::Feature', 'PartDesign::Body') and not asm.isPartConnected(o)]
    if kind == 'JointsOfComponent':
        comps = {n for n, _ in (sel or [])}
        out = []
        for j in joints():
            for r in ('Reference1', 'Reference2'):
                ref = getattr(j, r, None)
                obj = ref[0] if isinstance(ref, (list, tuple)) and ref else None
                subs = ref[1] if isinstance(ref, (list, tuple)) and len(ref) > 1 else []
                first = (subs[0].split('.')[0] if subs else '')
                if (obj is not None and obj.Name in comps) or first in comps:
                    out.append(j.Name); break
            if getattr(j, 'ObjectToGround', None) is not None and j.ObjectToGround.Name in comps: out.append(j.Name)
        return out
    if kind == 'LinkSelectLinked':
        n = (sel or [[None]])[0][0]; o = d.getObject(n) if n else None
        t = getattr(o, 'LinkedObject', None)
        return [t.Name] if t is not None else []
    raise RuntimeError(kind)

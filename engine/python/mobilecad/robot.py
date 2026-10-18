"""Robot workbench (src/Mod/Robot/Gui/Command*.cpp): robot, trajectories, waypoints, dress-ups, Kuka export and the
simulation (inverse kinematics along the trajectory).  The robot is drawn as its kinematic chain (VRML models are not
part of this FreeCAD checkout); trajectories as their waypoint polyline."""
import math
import FreeCAD as App
from FreeCAD import Vector as V
from .bridge import op, _doc

# Robot6Axis.cpp KukaIR500: a, alpha, d, theta, rotDir
KUKA_IR500 = [(500, -90, 1045, 0, -1), (1300, 0, 0, 0, 1), (55, 90, 0, -90, 1), (0, -90, -1025, 0, 1), (0, 90, 0, 0, 1), (0, 180, -300, 0, 1)]
_defaults = {'orientation': App.Rotation(), 'velocity': 1000.0, 'acceleration': 100.0, 'type': 'LIN', 'cont': False}

def _dh(a, alpha, d, theta):
    """KDL::Frame::DH: RotZ(theta) * TransZ(d) * TransX(a) * RotX(alpha) as an App.Matrix."""
    ct, st, ca, sa = math.cos(theta), math.sin(theta), math.cos(alpha), math.sin(alpha)
    return App.Matrix(ct, -st * ca, st * sa, a * ct, st, ct * ca, -ct * sa, a * st, 0, sa, ca, d, 0, 0, 0, 1)

def joint_points(rob):
    """Origin of every segment of the chain (base -> flange) for the current axis angles."""
    m = rob.Placement.toMatrix().multiply(rob.Base.toMatrix())
    pts = [m.multVec(V())]
    for i, (a, al, d, th, rd) in enumerate(KUKA_IR500):
        q = math.radians(getattr(rob, 'Axis%d' % (i + 1)) * rd)
        rz = App.Matrix(math.cos(q), -math.sin(q), 0, 0, math.sin(q), math.cos(q), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)
        m = m.multiply(rz).multiply(_dh(a, math.radians(al), d, math.radians(th)))
        pts.append(m.multVec(V()))
    return pts

@op
def robot_create():
    """Robot_Create: Robot::RobotObject (default KUKA IR500 kinematics)."""
    r = _doc().addObject('Robot::RobotObject', 'Robot')
    r.Axis1 = 0.0          # desktop sets RobotKinematicFile here, whose onChanged computes the TCP; an axis change does the same
    _doc().recompute(); return r.Name

def _robot(names):
    d = _doc()
    r = next((d.getObject(n) for n in (names or []) if d.getObject(n) is not None and d.getObject(n).TypeId == 'Robot::RobotObject'), None)
    r = r or next((o for o in d.Objects if o.TypeId == 'Robot::RobotObject'), None)
    if r is None: raise RuntimeError('Create a robot first')
    return r

def _traj(names):
    d = _doc()
    t = next((d.getObject(n) for n in (names or []) if d.getObject(n) is not None and 'Trajectory' in d.getObject(n).TypeId), None)
    t = t or next((o for o in reversed(d.Objects) if o.TypeId == 'Robot::TrajectoryObject'), None)
    if t is None: raise RuntimeError('Create a trajectory first')
    return t

@op
def robot_cmd(kind, names=None, pts=None, opts=None):
    import Robot
    d = _doc(); o = opts or {}
    if kind == 'CreateTrajectory':
        t = d.addObject('Robot::TrajectoryObject', 'Trajectory'); d.recompute(); return t.Name
    if kind in ('InsertWaypoint', 'InsertWaypointPreselect'):      # at the robot TCP / at the picked point
        t = _traj(names)
        if kind == 'InsertWaypoint':
            pl = _robot(names).Tcp
        else:
            pl = App.Placement(V(*pts[0]), _defaults['orientation'])
        w = Robot.Waypoint(pl, type=_defaults['type'], name='Pt', vel=_defaults['velocity'], cont=_defaults['cont'], acc=_defaults['acceleration'])
        tr = t.Trajectory; tr.insertWaypoints(w); t.Trajectory = tr; d.recompute(); return t.Name
    if kind == 'SetDefaultOrientation':
        _defaults['orientation'] = App.Rotation(*o.get('ypr', (0, 0, 0))); return True
    if kind == 'SetDefaultValues':
        _defaults.update({k: o[k] for k in ('velocity', 'acceleration', 'type', 'cont') if k in o}); return True
    if kind == 'Edge2Trac':
        sel = o.get('sel') or []
        e = d.addObject('Robot::Edge2TracObject', 'Edge2Trac')
        e.Source = [(d.getObject(n), tuple(subs)) for n, subs in sel if d.getObject(n) is not None][0]
        d.recompute(); return e.Name
    if kind == 'TrajectoryDressUp':
        t = _traj(names); x = d.addObject('Robot::TrajectoryDressUpObject', 'DressUp'); x.Source = t; d.recompute(); return x.Name
    if kind == 'TrajectoryCompound':
        ts = [d.getObject(n) for n in (names or []) if d.getObject(n) is not None and 'Trajectory' in d.getObject(n).TypeId]
        x = d.addObject('Robot::TrajectoryCompound', 'TrajectoryCompound'); x.Source = ts; d.recompute(); return x.Name
    if kind == 'SetHomePos':
        r = _robot(names); r.Home = [r.Axis1, r.Axis2, r.Axis3, r.Axis4, r.Axis5, r.Axis6]; return True
    if kind == 'RestoreHomePos':
        r = _robot(names)
        for i, a in enumerate(r.Home): setattr(r, 'Axis%d' % (i + 1), a)
        d.recompute(); return True
    if kind == 'AddToolShape':
        r = _robot(names); tool = next((d.getObject(n) for n in names if d.getObject(n) is not None and d.getObject(n) is not r), None)
        if tool is None: raise RuntimeError('Select the robot and the tool shape')
        r.ToolShape = tool; d.recompute(); return True
    if kind in ('ExportKukaCompact', 'ExportKukaFull'):
        import KukaExporter
        r = _robot(names); t = _traj(names)
        (KukaExporter.ExportCompactSub if kind == 'ExportKukaCompact' else KukaExporter.ExportFullSub)(r, t, o['path'])
        return True
    raise RuntimeError(kind)

@op
def robot_simulate(names, t):
    """Robot_Simulate step: TCP at trajectory time t -> inverse kinematics -> axis angles of the robot."""
    r = _robot(names); tr = _traj(names).Trajectory
    t = max(0.0, min(float(t), tr.Duration))
    pl = tr.position(t)
    r.Tcp = pl
    _doc().recompute()
    return {'t': t, 'duration': tr.Duration, 'axes': [r.Axis1, r.Axis2, r.Axis3, r.Axis4, r.Axis5, r.Axis6]}

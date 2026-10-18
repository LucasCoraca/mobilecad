"""Runs FreeCAD's own Python commands (BIM, FEM, CAM, OpenSCAD, Assembly, Draft-based tools ...) on the headless
FreeCADGui (engine/python/shims/FreeCADGui.py): the command's Activated() executes unchanged, with the app's selection;
what it asks the GUI for (edit an object, show a task panel, pick points with the Snapper) comes back to the app."""
import importlib, importlib.util, os, sys
import _gui_finder  # noqa: F401  (placeholder modules for PartGui, MeshGui ...)
import FreeCAD as App
from .bridge import op, _doc
from .command_modules import MODULES

class _GuiNames(importlib.abc.MetaPathFinder):
    """Workbench modules imported lazily inside a command (ArchSchedule from BimSchedule.Activated ...) ran their
    `if FreeCAD.GuiUp: import FreeCADGui` with GuiUp off; give every Mod module the GUI names up front instead."""
    def find_spec(self, name, path=None, target=None):
        if self.busy: return None
        self.busy = True
        try: spec = importlib.util.find_spec(name) if path is None else importlib.machinery.PathFinder.find_spec(name, path)
        except Exception: spec = None
        finally: self.busy = False
        o = getattr(spec, 'origin', None) or ''
        if spec is None or '/Mod/' not in o or not hasattr(spec.loader, 'exec_module'): return None
        inner = spec.loader
        class L(importlib.abc.Loader):
            def create_module(self, sp): return inner.create_module(sp)
            def exec_module(self, m):
                import FreeCADGui
                from PySide import QtCore, QtGui, QtWidgets
                for k, v in (('FreeCADGui', FreeCADGui), ('QtCore', QtCore), ('QtGui', QtGui), ('QtWidgets', QtWidgets)):
                    m.__dict__.setdefault(k, v)
                gui_up = App.GuiUp
                App.GuiUp = True                 # module-level `if FreeCAD.GuiUp:` task panels / view providers
                try: inner.exec_module(m)
                finally: App.GuiUp = gui_up
        spec.loader = L()
        return spec
    busy = False

import importlib.abc, importlib.machinery
if not any(isinstance(f, _GuiNames) for f in sys.meta_path): sys.meta_path.insert(0, _GuiNames())

# ------------------------------------------------------------------ modal dialogs
# A command's QDialog.exec() / QMessageBox.question / QInputDialog.getText must wait for the user.  The command runs on
# a worker thread; exec() hands control back to the engine thread (the op returns with the dialog as a panel) and blocks
# until the user answers (taskpanel.panel_button resumes it).  Only one of the two threads runs at a time.
import threading
_co = {'state': None}

class _ConsoleTap:
    """FreeCAD.Console with the command's messages / warnings / errors also collected for the app (desktop: the
    report view); print() output is collected the same way."""
    def __init__(self, real): self._real = real; self.buf = []
    def _tap(self, name):
        f = getattr(self._real, name)
        def w(*a, **k):
            if a and isinstance(a[-1], str) and _co.get('capture'): self.buf.append(a[-1])
            return f(*a, **k)
        return w
    def __getattr__(self, n):
        if n in ('PrintMessage', 'PrintWarning', 'PrintError', 'PrintNotification', 'PrintCritical', 'PrintUserWarning', 'PrintUserError'):
            return self._tap(n)
        return getattr(self._real, n)

class _Out:
    def __init__(self, real, tap): self.real = real; self.tap = tap
    def write(self, t):
        if _co.get('capture') and threading.current_thread() is not threading.main_thread(): self.tap.buf.append(t)
        return self.real.write(t)
    def flush(self): return self.real.flush()
    def __getattr__(self, n): return getattr(self.real, n)

if not isinstance(App.Console, _ConsoleTap):
    App.Console = _ConsoleTap(App.Console)
    sys.stdout = _Out(sys.stdout, App.Console)

def console_text():
    t = ''.join(App.Console.buf).strip(); App.Console.buf.clear(); return t

def coop(fn):
    """Run fn on a worker; return when it finishes or blocks in a modal dialog.  Re-raises its exception."""
    finish_blocked()
    _co['capture'] = True
    st = {'ev': threading.Event(), 'exc': None, 'finished': False, 'resume': None, 'thread': None}
    def body():
        try: fn()
        except BaseException as e: st['exc'] = e
        finally: st['finished'] = True; st['ev'].set()
    _co['state'] = st
    # OCCT (HLR, booleans, meshing) recurses deeply: give the worker the stack the engine thread has
    old = threading.stack_size(64 * 1024 * 1024)
    try: t = threading.Thread(target=body, daemon=True)
    finally: threading.stack_size(old)
    st['thread'] = t
    t.start(); st['ev'].wait(); st['ev'].clear()
    if st['finished']:
        _co['state'] = None
        if st['exc'] is not None: raise st['exc']

def _modal(dlg):
    """QDialog.exec() hook: None when not on a command worker (then the dialog takes its defaults)."""
    st = _co['state']
    if st is None or threading.current_thread() is not st['thread']: return None
    import FreeCADGui
    FreeCADGui.requests.append(('modal', dlg))
    st['resume'] = threading.Event()
    st['ev'].set()                       # back to the engine thread
    st['resume'].wait()
    return getattr(dlg, '_exec_result', 0)

def resume(dlg, result):
    """The user answered the modal dialog: continue the command until it ends or blocks again."""
    st = _co['state']
    if st is None or st['resume'] is None: return
    dlg._exec_result = result
    ev = st['resume']; st['resume'] = None
    ev.set(); st['ev'].wait(); st['ev'].clear()
    if st['finished']:
        _co['state'] = None
        if st['exc'] is not None: raise st['exc']

def finish_blocked():
    """A new action while a dialog is still waiting: cancel it (exec() returns Rejected) and let the command end."""
    for _ in range(20):
        st = _co['state']
        if st is None or st['resume'] is None: return
        import FreeCADGui
        dlg = next((r[1] for r in FreeCADGui.requests if r[0] == 'modal'), None)
        try: resume(dlg or object(), 0)
        except BaseException: pass

import io
class NoExe:                        # desktop helper programs (CAMotics, openscad ...) do not exist on the device:
    returncode = 1                   # behave like a process that ran and failed with no output
    def __init__(self, *a, **k): self.args = a[0] if a else ''; self.stdout = io.BytesIO(); self.stderr = io.BytesIO(); self.stdin = None; self.pid = 0
    def communicate(self, *a, **k): return (b'', b'')
    def wait(self, *a, **k): return 1
    def poll(self): return 1
    def kill(self): pass
    def terminate(self): pass
    def __enter__(self): return self
    def __exit__(self, *a): return False

# names FreeCAD's GUI start-up puts in __main__ (src/Gui/FreeCADGuiInit.py): workbench modules subclass __main__.Workbench
import __main__
if not hasattr(__main__, 'Workbench'):
    import FreeCADGui as _G
    __main__.Workbench = _G.Workbench

_loaded = set()
_pending_points = []          # Snapper callbacks waiting for a point

def _mod_dir(wb):
    for base in (os.path.join(App.getHomePath(), 'Mod', wb), os.path.join(App.getResourceDir(), 'Mod', wb)):
        if os.path.isdir(base): return base
    return None

def _ensure(name):
    import FreeCADGui as G
    if name in G._commands: return True
    wb, rel = MODULES.get(name, (None, None))
    if wb is None or (wb, rel) in _loaded: return name in G._commands
    _loaded.add((wb, rel))
    base = _mod_dir(wb)
    if base is None: return False
    if base not in sys.path: sys.path.append(base)
    # Draft's parameter defaults must be read headless (with GuiUp they come from Qt widgets) before GuiUp flips
    try:
        import Draft  # noqa: F401
    except Exception:
        pass
    gui_up = App.GuiUp
    App.GuiUp = True                     # modules define their commands only "if FreeCAD.GuiUp"
    try:
        if os.path.basename(rel) == 'InitGui.py':
            spec = importlib.util.spec_from_file_location('InitGui_' + wb, os.path.join(base, rel))
            mod = importlib.util.module_from_spec(spec)
            # FreeCAD runs InitGui.py with these names already defined (src/Gui/FreeCADGuiInit.py)
            import FreeCADGui as G
            mod.__dict__.update({'Workbench': G.Workbench, 'Gui': G, 'FreeCADGui': G, 'App': App, 'FreeCAD': App})
            spec.loader.exec_module(mod)
            if name not in G._commands:      # registered when the workbench activates: run its Initialize()
                import subprocess
                real_popen = subprocess.Popen; subprocess.Popen = NoExe
                try:
                    for v in list(mod.__dict__.values()):
                        if isinstance(v, type) and issubclass(v, G.Workbench) and v is not G.Workbench:
                            try: v().Initialize()
                            except Exception: pass
                finally:
                    subprocess.Popen = real_popen
        else:
            importlib.import_module(rel[:-3].replace('/', '.'))
    finally:
        App.GuiUp = gui_up
    return name in G._commands

def _set_selection(sel):
    import FreeCADGui as G
    d = _doc(); items = []
    for n, subs in (sel or []):
        for s in (subs or ['']): items.append((d.Name, n, s, None))
    G.Selection._set(items)

def _sig(o):
    """Cheap fingerprint of what a command can change on an object (placement, shape extent, label, visibility)."""
    try:
        sh = getattr(o, 'Shape', None)
        bb = str(sh.BoundBox) + str(len(sh.Faces)) if sh is not None and not sh.isNull() else ''
    except Exception:
        bb = ''
    return (o.Label, str(getattr(o, 'Placement', '')), bb, getattr(o, 'Visibility', None), str(getattr(o, 'Group', ''))[:200])

def _snapshot():
    try: return {o.Name: _sig(o) for o in _doc().Objects}
    except Exception: return {}

def _view_value(k, v):
    """A view property write -> what the app draws: colour [r, g, b] (0..1) or transparency (0..100)."""
    try:
        if k == 'ShapeAppearance':
            m = v[0] if isinstance(v, (list, tuple)) else v
            v, k = m.DiffuseColor, 'ShapeColor'
        if k in ('ShapeColor', 'DiffuseColor'):
            if isinstance(v, (list, tuple)) and v and isinstance(v[0], (list, tuple)): v = v[0]      # per-face list
            c = [float(x) for x in tuple(v)[:3]]
            if max(c) > 1.0: c = [x / 255.0 for x in c]
            return 'color', c
        if k == 'Transparency': return 'transparency', int(v)
    except Exception:
        pass
    return None

def _outcome(before):
    """New / changed / deleted objects, selection after the command, and the GUI requests the app has to serve."""
    import FreeCADGui as G
    d = _doc()
    created = [o.Name for o in d.Objects if o.Name not in before]
    changed = [o.Name for o in d.Objects if isinstance(before, dict) and o.Name in before and _sig(o) != before[o.Name]]
    removed = [n for n in before if d.getObject(n) is None]
    out = {'created': created, 'changed': changed, 'removed': removed, 'edit': None, 'point': None, 'commands': [],
           'selection': [[n, s] for (_, n, s, _) in G.Selection._get()]}
    for r in G.requests:
        if r[0] == 'edit' and r[1]: out['edit'] = r[1]
        elif r[0] == 'dialog':
            p = r[1]
            obj = next((getattr(p, a) for a in ('obj', 'Object', 'object', 'feature', 'fp', 'Feature') if hasattr(p, a) and hasattr(getattr(p, a), 'Name')), None)
            out['edit'] = getattr(obj, 'Name', None) or (created[-1] if created else out['edit'])
            out['dialog'] = type(p).__name__
            from . import taskpanel
            if taskpanel._forms(p): out['panel_id'] = taskpanel.register(p, 'dialog')
        elif r[0] == 'widget':
            from . import taskpanel
            if r[1]._visible: out['panel_id'] = taskpanel.register(r[1], 'widget')       # (not if it closed again meanwhile)
            else: taskpanel._drop(r[1])
        elif r[0] == 'close':
            from . import taskpanel
            taskpanel._close(r[1]); out['closed'] = True
        elif r[0] == 'message':
            out.setdefault('messages', []).append(r[1])
        elif r[0] == 'view':
            v = _view_value(r[2], r[3])
            if v is not None: out.setdefault('view', {}).setdefault(r[1], {})[v[0]] = v[1]
        elif r[0] == 'align':
            out['align'] = r[1]
        elif r[0] == 'url':
            out['url'] = r[1]
        elif r[0] == 'modal':
            from . import taskpanel
            out['panel_id'] = taskpanel.register(r[1], 'modal')
        elif r[0] == 'point':
            _pending_points.append(r[1]); out['point'] = {'title': r[3], 'last': r[2]}
        elif r[0] == 'command':
            out['commands'].append(r[1])
    G.requests.clear()
    try:
        c = console_text()
        if c: out['console'] = c[-2000:]
    except Exception:
        pass
    if out.get('panel_id'):
        from . import taskpanel
        out['panel'] = taskpanel.serialize(out['panel_id']) if out['panel_id'] in taskpanel._panels else None
        out['edit'] = None if out['panel'] else out['edit']      # the panel is the editing UI
    if out['edit'] is None and created and not out.get('panel'):
        # a new feature opens its editor; solver output (results, VTK pipelines) is shown, not edited
        objs = [o for o in (_doc().getObject(n) for n in created) if o is not None]
        if not any(o.isDerivedFrom('Fem::FemResultObject') or o.isDerivedFrom('Fem::FemPostObject') for o in objs):
            out['edit'] = created[-1]
    if G._view_events: out['events'] = True          # the command waits for taps in the 3D view
    # 3D view background (View preferences: simple / linear gradient / radial gradient; BIM_Background cycles them)
    vp = App.ParamGet('User parameter:BaseApp/Preferences/View')
    out['background'] = 0 if vp.GetBool('Simple', True) else (2 if vp.GetBool('RadialGradient', False) else 1)
    # the global Draft working plane the command may have set (BIM_SetWPTop / Front / Side / WPView ...)
    try:
        import WorkingPlane
        wp = WorkingPlane.get_working_plane(update=False)
        out['wp'] = {'pos': list(wp.position), 'u': list(wp.u), 'v': list(wp.v), 'w': list(wp.axis)}
    except Exception:
        pass
    return out

# ------------------------------------------------------------------ headless replacements for modal Qt dialogs
def _cam_create_job(*a, **k):
    """CAM JobCreate dialog (Path/Main/Gui/JobDlg.py) -> Path.Main.Job.Create with the selected models (all visible
    solids when nothing is selected), default template — what the dialog's OK does with its defaults."""
    import FreeCADGui as G
    import Path.Main.Job as PathJob
    models = [o for o in G.Selection.getSelection() if hasattr(o, 'Shape')]
    if not models:
        models = [o for o in _doc().Objects if getattr(o, 'Visibility', False) and hasattr(o, 'Shape') and o.Shape.Solids
                  and not o.TypeId.startswith(('App::', 'Path::'))]
    job = PathJob.Create('Job', models, None)
    import FreeCADGui
    FreeCADGui.requests.append(('edit', job.Name))
    return job

_the_wp = []

def _patch_gui_modules():
    """Point the GUI-only helpers the commands call at headless versions."""
    try:
        from . import fem; fem.patch_solver_tools()
    except Exception:
        pass
    try:
        # headless get_working_plane() returns a fresh plane per call; the app has one working plane (per 3D view)
        import WorkingPlane
        if not getattr(WorkingPlane.get_working_plane, '_mobilecad', False):
            def get_working_plane(update=True):
                if not _the_wp: _the_wp.append(WorkingPlane.PlaneGui())
                return _the_wp[0]
            get_working_plane._mobilecad = True
            WorkingPlane.get_working_plane = get_working_plane
            def align_view(self):          # "align the view to the working plane": the app's camera does it
                import FreeCADGui
                FreeCADGui.requests.append(('align', [list(self.axis), list(self.position)]))
            WorkingPlane.PlaneGui.align_view = align_view
    except Exception:
        pass
    try:
        import draftutils.gui_utils as gu
        import FreeCADGui as G
        gu.get_3d_view = lambda: G._the_view
    except Exception:
        pass
    try:
        import PathScripts.PathUtilsGui as pug
        pug.createJob = _cam_create_job
        import Path.Main.Gui.JobCmd as jc
        jc.CommandJobCreate.Activated = lambda self, *a: _cam_create_job()
    except Exception:
        pass

@op
def gui_available(names):
    """Which of these Python commands can be loaded and run (group commands are dropdowns, not runnable)."""
    import FreeCADGui as G
    ok = []
    for n in names:
        try:
            if _ensure(n) and hasattr(G._commands.get(n), 'Activated'): ok.append(n)
        except Exception:
            pass
    _patch_gui_modules()
    return ok

@op
def gui_command(name, sel=None, view_dir=None, wp=None, page=None, colors=None):
    import FreeCADGui as G
    G._page[0] = page
    # the colours the app shows are what the commands read back from the view providers (BIM_ResetCloneColors ...)
    for n, c in (colors or {}).items():
        if _doc().getObject(n) is not None: G._vp_state.setdefault((_doc().Name, n), {})['ShapeColor'] = tuple(c) + (0.0,)
    if wp:                                   # the app's working plane is FreeCAD's global one for the command
        try:
            import WorkingPlane
            g = WorkingPlane.get_working_plane(update=False)
            g.u, g.v, g.axis, g.position = App.Vector(*wp['u']), App.Vector(*wp['v']), App.Vector(*wp['w']), App.Vector(*wp['pos'])
        except Exception:
            pass
    if not _ensure(name): raise RuntimeError('%s is not available' % name)
    _patch_gui_modules()
    if view_dir: G._view_dir = tuple(view_dir)
    _set_selection(sel)
    G.requests.clear(); _pending_points.clear()
    if G.Control.current is not None:        # starting another tool closes the open task dialog (as the app's UI does)
        from . import taskpanel
        taskpanel._close(G.Control.current)
    cmd = G._commands.get(name)
    try:
        active = cmd.IsActive() if hasattr(cmd, 'IsActive') else True
    except Exception:
        active = True
    if active is False and not sel and isinstance(getattr(cmd, 'is_active', None), str):
        # FEM commands that want "the solver" / "the mesh" / "the result" selected: when exactly one object of the
        # document enables the command, it is the one meant (what the user would tap in the tree first)
        hits = []
        for o in _doc().Objects[:300]:
            _set_selection([[o.Name, []]])
            try:
                if cmd.IsActive(): hits.append(o.Name)
            except Exception:
                pass
            if len(hits) > 1: break
        _set_selection([[hits[0], []]] if len(hits) == 1 else None)
        if len(hits) == 1: active = True
    if active is False:                  # the desktop greys the tool out: say what it needs
        res = getattr(cmd, 'GetResources', lambda: {})()
        raise RuntimeError('%s needs a different selection (%s)' % (str(res.get('MenuText', name)), str(res.get('ToolTip', '')).split('.')[0]))
    before = _snapshot()
    coop(lambda: G.runCommand(name))
    _doc().recompute()
    return _outcome(before)

@op
def gui_point(x, y, z):
    """A point for the command waiting on Snapper.getPoint (Draft callback signature: callback(point, obj))."""
    if not _pending_points: return None
    cb = _pending_points.pop(0)
    before = _snapshot()
    try: cb(App.Vector(x, y, z), None)
    except TypeError: cb(App.Vector(x, y, z))
    _doc().recompute()
    return _outcome(before)

@op
def gui_point_cancel():
    """Escape: Draft tools treat a None point as cancel/finish."""
    if not _pending_points: return None
    cb = _pending_points.pop(0)
    before = _snapshot()
    try: cb(None, None)
    except Exception:
        try: cb(None)
        except Exception: pass
    _doc().recompute()
    return _outcome(before)


@op
def gui_workbench_toolbars(wb):
    """The toolbars a workbench's InitGui.Initialize() appends (BIM, CAM ... build them in code)."""
    import FreeCADGui as G
    base = _mod_dir(wb)
    if base is None or not os.path.exists(os.path.join(base, 'InitGui.py')): return []
    try:
        import Draft  # noqa: F401
    except Exception:
        pass
    if base not in sys.path: sys.path.append(base)
    spec = importlib.util.spec_from_file_location('InitGuiTB_' + wb, os.path.join(base, 'InitGui.py'))
    mod = importlib.util.module_from_spec(spec)
    mod.__dict__.update({'Workbench': G.Workbench, 'Gui': G, 'FreeCADGui': G, 'App': App, 'FreeCAD': App})
    gui_up = App.GuiUp; App.GuiUp = True
    import subprocess
    real_popen = subprocess.Popen
    subprocess.Popen = NoExe
    try:
        spec.loader.exec_module(mod)
        for v in list(mod.__dict__.values()):
            if isinstance(v, type) and issubclass(v, G.Workbench) and v is not G.Workbench:
                G.toolbars.pop(v.__name__, None)
                try: v().Initialize()
                except Exception:
                    import traceback; App.Console.PrintWarning('Initialize %s: %s\n' % (wb, traceback.format_exc()[-600:]))
                    if not G.toolbars.get(v.__name__): raise
                if G.toolbars.get(v.__name__): return G.toolbars[v.__name__]
    finally:
        App.GuiUp = gui_up; subprocess.Popen = real_popen
    return []


@op
def gui_groups(names):
    """Members of FreeCAD's Python group commands (toolbar dropdowns: GetCommands())."""
    import FreeCADGui as G
    out = {}
    for n in names:
        try:
            _ensure(n)
            c = G._commands.get(n)
            if c is not None and hasattr(c, 'GetCommands'): out[n] = [x for x in c.GetCommands() if isinstance(x, str)]
        except Exception:
            pass
    return out


@op
def gui_view_event(kind='tap', point=None, x=0, y=0):
    """A tap (mouse button down + up at the 3D point) or Escape for the View.addEventCallback handlers."""
    import FreeCADGui as G
    if point is not None: G._last_point[0] = App.Vector(*point)
    base = {'Position': (int(x), int(y)), 'ShiftDown': False, 'CtrlDown': False, 'AltDown': False, 'Time': 0}
    if kind == 'escape':
        evs = [('SoKeyboardEvent', dict(base, Type='SoKeyboardEvent', Key='ESCAPE', State='DOWN')),
               ('SoKeyboardEvent', dict(base, Type='SoKeyboardEvent', Key='ESCAPE', State='UP'))]
    else:
        evs = [('SoMouseButtonEvent', dict(base, Type='SoMouseButtonEvent', Button='BUTTON1', State='DOWN')),
               ('SoMouseButtonEvent', dict(base, Type='SoMouseButtonEvent', Button='BUTTON1', State='UP'))]
    before = _snapshot()
    def go():
        for t, ev in evs:
            for kind_, cb in list(G._view_events):
                if kind_ in (t, 'SoEvent') and (kind_, cb) in G._view_events: cb(ev)
    coop(go)
    if kind == 'escape': G._view_events.clear()
    _doc().recompute()
    return _outcome(before)

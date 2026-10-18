"""Headless FreeCADGui for the mobile app.

FreeCAD's Python workbenches (BIM, FEM, CAM, OpenSCAD, Draft-based tools ...) implement their commands as classes that
call FreeCADGui: addCommand / doCommand / Selection / Control.showDialog / ActiveDocument.setEdit / Snapper.getPoint.
This module provides those entry points on top of the mobile app, so the workbenches' own command code runs unchanged:

* commands register in a registry (`addCommand`) and are run by mobilecad.guicmd;
* the Selection is the app's selection (set before a command runs, read back after);
* doCommand executes the generated Python like the desktop console does — statements that only touch view providers
  (ViewObject, Gui.*) are skipped because no 3D scene graph exists here;
* setEdit / showDialog / Snapper.getPoint become requests the app fulfils with its own UI (property dialog, point picker).

Anything else resolves to an inert placeholder (see _stub.py)."""
import ast
import FreeCAD as App
from _stub import Stub, module_getattr as _stub_getattr

# ------------------------------------------------------------------ requests to the app (drained by mobilecad.guicmd)
requests = []

# ------------------------------------------------------------------ commands
_commands = {}

def addCommand(name, cmd=None, *args):
    _commands[name] = cmd

def listCommands():
    return list(_commands)

def isCommandActive(name):
    c = _commands.get(name)
    try:
        return bool(c.IsActive()) if hasattr(c, 'IsActive') else True
    except Exception:
        return True

def runCommand(name, arg=0):
    c = _commands.get(name)
    if c is None:
        requests.append(('command', name)); return
    import inspect
    try:
        n = len(inspect.signature(c.Activated).parameters)
    except (TypeError, ValueError):
        n = 1
    c.Activated(arg) if n >= 1 else c.Activated()

class Command:
    """Gui.Command static helpers some modules use."""
    @staticmethod
    def get(name): return _commands.get(name)

# ------------------------------------------------------------------ doCommand: the desktop's "console" for generated code
# the desktop runs doCommand code in the Python console, i.e. in __main__ (BIM_Wall defines helpers there and calls
# them through __main__.new_baseless_wall ...)
import __main__ as _main
_ns = _main.__dict__
_ns.setdefault('App', App); _ns.setdefault('FreeCAD', App)

def _gui_only(src):
    import re
    return bool(re.search(r'ViewObject|\b(FreeCADGui|Gui)\.|ViewProvider|getMainWindow', src))

def doCommand(code):
    import sys
    _ns['Gui'] = sys.modules[__name__]; _ns['FreeCADGui'] = sys.modules[__name__]
    try:
        tree = ast.parse(code)
    except SyntaxError:
        # generated code with a raw newline inside a "\n" literal (CAM_Copy's console code): escape it like Qt's console would
        import re
        fixed = re.sub(r'"\n"', r'"\\n"', code)
        if fixed == code: exec(code, _ns); return
        return doCommand(fixed)
    for node in tree.body:
        src = ast.get_source_segment(code, node) or ''
        try:
            exec(compile(ast.Module([node], []), '<doCommand>', 'exec'), _ns)
        except Exception:
            if _gui_only(src): continue          # view-provider statement: no scene graph on mobile
            raise

doCommandGui = doCommand
doCommandSkip = doCommand
doCommandEval = lambda code: eval(code, _ns)

def addModule(name):
    import importlib
    mod = importlib.import_module(name)
    _ns[name.split('.')[0]] = importlib.import_module(name.split('.')[0])
    _ns[name] = mod

def updateGui(): pass
def updateLocale(): pass
def SendMsgToActiveView(*a, **k): return None

# ------------------------------------------------------------------ selection = the app's selection
class _SelObj:
    def __init__(self, obj, subs, pts):
        self.Object = obj; self.ObjectName = obj.Name; self.DocumentName = obj.Document.Name; self.Document = obj.Document
        self.SubElementNames = tuple(subs); self.HasSubObjects = bool(subs); self.TypeName = obj.TypeId
        self.FullName = '%s#%s' % (obj.Document.Name, obj.Name)
        self.PickedPoints = tuple(App.Vector(*p) for p in pts)
    @property
    def SubObjects(self):
        out = []
        for s in self.SubElementNames:
            try: out.append(self.Object.getSubObject(s))
            except Exception: pass
        return tuple(out)

class _Selection:
    def __init__(self): self._items = []; self._observers = []          # [(doc, obj, sub, point)]
    # -- set by the bridge
    def _set(self, items): self._items = list(items)
    def _get(self): return list(self._items)
    def _objects(self, doc=None):
        d = App.ActiveDocument
        out = []
        for (dn, on, sub, pt) in self._items:
            o = (App.getDocument(dn) if dn else d).getObject(on) if (dn or d) else None
            if o is not None: out.append((o, sub, pt))
        return out
    def getSelection(self, doc=None, resolve=1, single=False):
        seen = []
        for o, _, _ in self._objects(doc):
            if o not in seen: seen.append(o)
        return seen
    def getSelectionEx(self, doc=None, resolve=1, single=False):
        groups = {}
        for o, sub, pt in self._objects(doc):
            g = groups.setdefault(o.Name, [o, [], []])
            if sub: g[1].append(sub); g[2].append(pt or (0, 0, 0))
        return [_SelObj(o, s, p) for o, s, p in groups.values()]
    def getCompleteSelection(self, resolve=1): return self.getSelectionEx()
    def getSelectionObject(self, doc, obj, sub, pt=None): return _SelObj(App.getDocument(doc).getObject(obj), [sub] if sub else [], [pt] if pt else [])
    def addSelection(self, *a, **k):
        if a and hasattr(a[0], 'Name') and hasattr(a[0], 'Document'):
            o = a[0]; sub = a[1] if len(a) > 1 and isinstance(a[1], str) else ''
            subs = a[1] if len(a) > 1 and isinstance(a[1], (list, tuple)) else [sub]
            for s in subs: self._items.append((o.Document.Name, o.Name, s, None))
        elif len(a) >= 2:
            self._items.append((a[0], a[1], a[2] if len(a) > 2 else '', None))
    def removeSelection(self, *a, **k):
        name = a[0].Name if a and hasattr(a[0], 'Name') else (a[1] if len(a) > 1 else None)
        self._items = [x for x in self._items if x[1] != name]
    def clearSelection(self, *a, **k): self._items = []
    def isSelected(self, obj, sub='', resolve=1): return any(x[1] == obj.Name and (not sub or x[2] == sub) for x in self._items)
    def hasSelection(self, *a, **k): return bool(self._items)
    def countObjectsOfType(self, t='App::DocumentObject', doc=None, resolve=1):
        return sum(1 for o in self.getSelection() if o.isDerivedFrom(t))
    def getPreselection(self): return None
    def setPreselection(self, *a, **k): pass
    def clearPreselection(self, *a, **k): pass
    def addObserver(self, o, *a, **k):
        if o not in self._observers: self._observers.append(o)
    def removeObserver(self, o, *a, **k):
        if o in self._observers: self._observers.remove(o)
    def _notify(self, items):
        """The app's selection changed while a task panel is open: tell the observers (Gui::SelectionObserverPython)."""
        old = set(self._items); self._items = list(items)
        for ob in list(self._observers):
            try:
                if not items and hasattr(ob, 'clearSelection'): ob.clearSelection(None)
                for (dn, on, sub, pt) in items:
                    if (dn, on, sub, pt) not in old and hasattr(ob, 'addSelection'):
                        p = tuple(pt) if pt else (0.0, 0.0, 0.0)
                        try: ob.addSelection(dn, on, sub, p)
                        except TypeError: ob.addSelection(dn, on, sub)
            except Exception as e:
                App.Console.PrintWarning('selection observer: %s\n' % e)
    def addSelectionGate(self, *a, **k): pass
    def removeSelectionGate(self, *a, **k): pass
    def setSelectionStyle(self, *a, **k): pass
    def pushSelStack(self, *a, **k): pass
    def getSelectionFromStack(self, *a, **k): return []

Selection = _Selection()

# ------------------------------------------------------------------ documents / views
_active = {}

_page = [None]          # set by mobilecad.guicmd from the app (current Drawing-tab page)
_view_events = []       # [(event type, callback)] registered with View.addEventCallback
_last_point = [App.Vector()]

class _View(Stub):
    def getViewDirection(self): return App.Vector(*_view_dir)
    def getActiveObject(self, name, *a):
        """Active body / part / assembly (desktop: the activated container).  The app has no activation step, so the
        document's (last) matching container is the active one."""
        o = _active.get(name)
        if o is not None and getattr(o, 'Document', None) is App.ActiveDocument: return o
        d = App.ActiveDocument
        if d is None: return None
        want = {'pdbody': ('PartDesign::Body',), 'part': ('Assembly::AssemblyObject', 'App::Part')}.get(name, ())
        for t in want:
            hits = [x for x in d.Objects if x.TypeId == t]
            if hits: return hits[-1]
        return None
    def setActiveObject(self, name, obj, *a): _active[name] = obj
    def getCameraOrientation(self): return App.Rotation()
    # Coin event callbacks (macros / example commands that react to clicks in the view): the app delivers its taps
    # as SoMouseButtonEvent / SoKeyboardEvent dicts (mobilecad.guicmd.gui_view_event); getPoint gives the tapped point.
    def addEventCallback(self, kind, cb, *a, **k):
        _view_events.append((str(kind), cb)); return cb
    def removeEventCallback(self, kind, cb=None, *a, **k):
        _view_events[:] = [(t, c) for t, c in _view_events if not (t == str(kind) and (cb is None or c is cb or c == cb))]
    def addEventCallbackPivy(self, *a, **k): return None
    def removeEventCallbackPivy(self, *a, **k): pass
    def getObjectInfo(self, *a, **k): return None
    def getObjectsInfo(self, *a, **k): return None
    def getCursorPos(self): return (0, 0)
    def getSize(self): return (1000, 1000)
    def getPoint(self, *a, **k): return App.Vector(_last_point[0])
    def getSceneGraph(self): return Stub()
    def getPage(self):
        """The TechDraw page shown in the app's Drawing tab (desktop: an MDI page view is active), else None."""
        d = App.ActiveDocument
        return d.getObject(_page[0]) if d is not None and _page[0] else None
    def getViewer(self): return Stub()
    def redraw(self): pass
    def fitAll(self, *a): pass
    def viewIsometric(self): pass
    def viewTop(self): pass
    def viewFront(self): pass
    def isOrthographic(self): return True
    def getActiveCamera(self): return Stub()

_view_dir = (0.0, 0.0, -1.0)
_the_view = _View()

_vp_state = {}       # (document, object) -> view properties written by commands (ShapeColor, Transparency ...)
_VIEW_PROPS = ('ShapeColor', 'DiffuseColor', 'ShapeAppearance', 'Transparency', 'LineColor', 'PointColor', 'DisplayMode', 'LineWidth', 'PointSize')

class _VP(Stub):
    """ViewProvider stand-in: the view properties commands write are kept per object and reported to the app (which
    draws the colours / transparency); Visibility is the object's own; other reads give harmless defaults."""
    def __init__(self, obj=None): object.__setattr__(self, 'Object', obj)
    def _state(self):
        o = object.__getattribute__(self, 'Object')
        return _vp_state.setdefault((o.Document.Name, o.Name), {}) if o is not None else {}
    def __setattr__(self, k, v):
        if k == 'Visibility' and self.Object is not None: self.Object.Visibility = bool(v); return
        self._state()[k] = v
        if k in _VIEW_PROPS and self.Object is not None: requests.append(('view', self.Object.Name, k, v))
    def __getattr__(self, k):
        st = self._state()
        if k in st: return st[k]
        if k == 'Visibility': return getattr(self.Object, 'Visibility', True)
        if k == 'Transparency': return 0
        if k in ('ShapeColor', 'LineColor', 'PointColor', 'LinePrintColor'): return (0.8, 0.8, 0.8, 0.0)
        if k == 'DiffuseColor':
            c = st.get('ShapeColor', (0.8, 0.8, 0.8, 0.0))
            n = len(self.Object.Shape.Faces) if self.Object is not None and hasattr(self.Object, 'Shape') else 1
            return [tuple(c)] * max(1, n)
        if k in ('LineWidth', 'PointSize'): return 2.0
        if k == 'DrawStyle': return 'Solid'
        if k == 'DisplayMode': return 'Flat Lines'
        if k == 'ShapeAppearance':
            try: return (App.Material(),)
            except Exception: return ()
        if k == 'PropertiesList': return list(_VIEW_PROPS) + ['Visibility']
        if k == 'Document' and self.Object is not None: return _GuiDoc(self.Object.Document)   # vobj.Document.setEdit(vobj)
        if k == 'Proxy':                 # NativeIFC objects: their view provider (expand children, lock ...)
            o = self.Object
            if o is not None and 'IfcClass' in getattr(o, 'PropertiesList', ()):
                try:
                    from nativeifc import ifc_viewproviders as V
                    vp = V.ifc_vp_object(); vp.Object = o; st['Proxy'] = vp; return vp
                except Exception:
                    pass
            return Stub()
        return Stub.__getattr__(self, k)
    def show(self): self.Object and setattr(self.Object, 'Visibility', True)
    def hide(self): self.Object and setattr(self.Object, 'Visibility', False)
    def isVisible(self): return getattr(self.Object, 'Visibility', True)

def _vp_class(o):
    """The Python view provider the desktop attaches to a FEM Python object (femobjects.<m>.<C> ->
    femviewprovider.view_<m>.VP<C>)."""
    import importlib
    px = getattr(o, 'Proxy', None)
    mod = getattr(type(px), '__module__', '') if px is not None else ''
    if not mod.startswith('femobjects.'): return None
    try:
        vm = importlib.import_module('femviewprovider.view_' + mod.split('.', 1)[1])
    except Exception:
        return None
    return getattr(vm, 'VP' + type(px).__name__, None)

def _vp_edit(o, mode=0):
    """setEdit through the object's view provider (its setEdit shows the task panel) when it has one."""
    cls = _vp_class(o)
    vobj = _VP(o)
    if cls is None:
        # view providers the command attached itself (CAM operations / jobs: Path.Op.Gui.Base.ViewProvider)
        vp = vobj._state().get('Proxy')
        if vp is None or not getattr(type(vp), '__module__', '').startswith('Path.') or not hasattr(vp, 'setEdit'): return False
        cls = type(vp)
    try:
        vp = vobj._state().get('Proxy')
        if not isinstance(vp, cls):
            vp = cls(vobj); vobj._state()['Proxy'] = vp
            if hasattr(vp, 'attach'): vp.attach(vobj)
        return vp.setEdit(vobj, mode) is not False and Control.current is not None
    except Exception as e:
        App.Console.PrintWarning('setEdit %s: %s\n' % (o.Name, e)); return False

class _GuiDoc(Stub):
    def __init__(self, doc): object.__setattr__(self, 'Document', doc)
    @property
    def ActiveView(self): return _the_view
    def activeView(self): return _the_view
    def getObject(self, name):
        o = self.Document.getObject(name)
        return _VP(o) if o is not None else None
    def setEdit(self, obj, mode=0, sub=''):
        name = obj if isinstance(obj, str) else getattr(obj, 'Name', None) or getattr(getattr(obj, 'Object', None), 'Name', None)
        o = self.Document.getObject(name) if name else None
        if o is not None and _vp_edit(o, mode): return True      # the object's own task panel (FEM meshers, constraints ...)
        requests.append(('edit', name)); return True
    def resetEdit(self): pass
    def getInEdit(self): return None
    def toggleTreeItem(self, *a, **k): pass
    def mdiViewsOfType(self, *a): return []
    def hide(self, name): o = self.Document.getObject(name); o and setattr(o, 'Visibility', False)
    def show(self, name): o = self.Document.getObject(name); o and setattr(o, 'Visibility', True)
    def update(self): pass

def getDocument(name=None):
    d = App.getDocument(name) if isinstance(name, str) else (name or App.ActiveDocument)
    return _GuiDoc(d) if d else None

def activeDocument():
    return _GuiDoc(App.ActiveDocument) if App.ActiveDocument else None

def activeView(*a): return _the_view

def hide(name):
    o = App.ActiveDocument.getObject(name)
    if o: o.Visibility = False

def show(name):
    o = App.ActiveDocument.getObject(name)
    if o: o.Visibility = True

# ------------------------------------------------------------------ task dialogs
class _TaskDialog(Stub):
    """Gui::TaskView::TaskDialogPython returned by showDialog (setDocumentName, setAutoCloseOnDeletedDocument ...)."""
    def __init__(self, panel): object.__setattr__(self, 'panel', panel)
    def getDialogContent(self): return getattr(self.panel, 'form', None)
    def __getattr__(self, n):
        try: return getattr(object.__getattribute__(self, 'panel'), n)
        except AttributeError: return Stub.__getattr__(self, n)

class _Control:
    current = None
    def showDialog(self, panel):
        self.current = panel; requests.append(('dialog', panel)); return _TaskDialog(panel)
    def closeDialog(self, *a):
        if self.current is not None: requests.append(('close', self.current))
        self.current = None
    def activeDialog(self): return self.current is not None
    def activeTaskDialog(self): return self.current
    def showTaskView(self, *a): pass
    def isAllowedAlterDocument(self): return True
    def isAllowedAlterView(self): return True
    def isAllowedAlterSelection(self): return True

Control = _Control()

# ------------------------------------------------------------------ point input (Draft Snapper)
class _Snapper(Stub):
    def getPoint(self, last=None, callback=None, movecallback=None, extradlg=None, title=None, mode='point', **kw):
        requests.append(('point', callback, list(last) if last is not None else None, title or 'Pick point'))
        return None
    def off(self, *a, **k): pass
    def on(self, *a, **k): pass
    def setGrid(self, *a, **k): pass
    def setSelectMode(self, *a, **k): pass
    def setTrackers(self, *a, **k): pass
    def restack(self, *a, **k): pass
    def show(self, *a, **k): pass
    def hide(self, *a, **k): pass
    def snap(self, *a, **k): return App.Vector()
    @property
    def ui(self): return Stub()

Snapper = _Snapper()

class _ToolBar(Stub):
    """Draft's task-panel toolbar: the text entry (Draft/BIM text on a page) and the popup menu (BIM_MoveView) become
    modal app dialogs that answer the source command like the desktop toolbar does."""
    sourceCmd = None
    continueMode = False
    def taskUi(self, *a, **k): pass
    def textUi(self, *a, **k):
        from PySide import QtGui
        t, ok = QtGui.QInputDialog.getText(None, 'Text', 'Text (new lines with \\n):')
        cmd = self.sourceCmd
        if ok and t and cmd is not None:
            cmd.text = t.replace('\\n', '\n')
            if hasattr(cmd, 'createObject'): cmd.createObject()
        self.sourceCmd = None
    def popupMenu(self, labels, *a, **k):
        from PySide import QtGui
        cmd = self.sourceCmd
        if not labels: App.Console.PrintWarning('Nothing to choose\n'); return
        lab, ok = QtGui.QInputDialog.getItem(None, 'Choose', 'Page', list(labels), 0)
        if ok and cmd is not None and hasattr(cmd, 'proceed'): cmd.proceed(lab)
    def offUi(self, *a, **k): pass
    def number_length(self, v, *a, **k): return str(v)
    def checkSpecialChars(self, t): return t
    def isConstructionMode(self): return False
    def getDefaultColor(self, *a): return (0.0, 0.0, 0.0)

draftToolBar = _ToolBar()

# ------------------------------------------------------------------ misc entry points
class _WB(Stub):
    def __init__(self, name='PartDesignWorkbench'): object.__setattr__(self, 'name', name)
    def name(self): return 'PartDesignWorkbench'
    def getToolbarItems(self): return {}
    def listToolbars(self): return []
    def listMenus(self): return []
    def getClassName(self): return 'Gui::PythonWorkbench'

toolbars = {}       # workbench class name -> [(toolbar, [commands])] recorded from Initialize()
menus = {}

class Workbench(object):
    """Base class used by workbench InitGui modules."""
    MenuText = ''; ToolTip = ''; Icon = ''
    def appendToolbar(self, name, cmds=(), *a, **k):
        toolbars.setdefault(type(self).__name__, []).append((str(name), [c for c in (cmds or []) if isinstance(c, str)]))
    def appendMenu(self, name, cmds=(), *a, **k):
        menus.setdefault(type(self).__name__, []).append((str(name if isinstance(name, str) else '/'.join(map(str, name))), [c for c in (cmds or []) if isinstance(c, str)]))
    def appendContextMenu(self, *a, **k): pass
    def removeToolbar(self, *a, **k): pass
    def removeMenu(self, *a, **k): pass
    def appendCommandbar(self, *a, **k): pass
    def reloadActive(self): pass
    def name(self): return type(self).__name__

def activeWorkbench(): return _WB()
def activateWorkbench(name): return True
def addWorkbench(wb): pass
def listWorkbenches(): return {}
def getWorkbench(name): return _WB(name)
def addIconPath(*a): pass
def addResourcePath(*a): pass
def addLanguagePath(*a): pass
def addPreferencePage(*a): pass
def addIcon(*a): pass
def getIcon(*a): return Stub()
def getMainWindow():
    from PySide._widgets import _main_window
    return _main_window()
def getSoDBVersion(): return ''
def isIconCached(*a): return False
def getLocale(): return 'en'
def getMarkerIndex(*a, **k): return 0

class _UserInput(Stub):
    MouseLeft = 1; MouseRight = 2; MouseMiddle = 3; KeyEnter = 4; KeyEscape = 5; KeyShift = 6; KeyControl = 7; KeyAlt = 8

UserInput = _UserInput()
InputHint = Stub()
HintManager = Stub()
from PySide._ui import PySideUic, UiLoader  # noqa: E402  (.ui forms -> PySide/_widgets)

def __getattr__(name):
    if name == 'ActiveDocument':
        return activeDocument()
    return _stub_getattr(name)

# FreeCAD.Gui is FreeCADGui on the desktop (BIM_Library ...)
import sys as _sys
App.Gui = _sys.modules[__name__]

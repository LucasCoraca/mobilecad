"""Import hook: C++ GUI extension modules (PartGui, MeshGui, SketcherGui, ...) and GUI-only helpers do not exist on
mobile; workbench command modules import them only for icons/registration side effects.  When such a module is not
found, a placeholder module is supplied (attributes resolve to inert stubs, see _stub.py)."""
import importlib.abc, importlib.machinery, re, sys, types
from _stub import module_getattr

_GUI_ONLY = {'CAMSimulator', 'camotics', 'addonmanager_utilities', 'addonmanager_freecad_interface', 'SoQt', 'shiboken6', 'shiboken2', 'matplotlib.backends.backend_qtagg', 'vtkmodules'}

class _Loader(importlib.abc.Loader):
    def create_module(self, spec):
        m = types.ModuleType(spec.name); m.__getattr__ = module_getattr; m.__path__ = []; return m
    def exec_module(self, module): pass

class _Finder(importlib.abc.MetaPathFinder):
    def find_spec(self, name, path=None, target=None):
        top = name.split('.')[0]
        if name in _GUI_ONLY or top in ('vtkmodules', 'shapely') or re.fullmatch(r'\w+_rc', top) or (re.fullmatch(r'[A-Z]\w*Gui', top) and top != 'FreeCADGui'):
            # only if nothing real provides it
            for f in sys.meta_path:
                if f is self or not hasattr(f, 'find_spec'): continue
                try:
                    if f.find_spec(name, path, target): return None
                except Exception:
                    pass
            return importlib.machinery.ModuleSpec(name, _Loader(), is_package=True)
        return None

if not any(isinstance(f, _Finder) for f in sys.meta_path):
    sys.meta_path.append(_Finder())

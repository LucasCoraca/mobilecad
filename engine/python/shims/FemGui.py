"""Headless FemGui: the active-analysis bookkeeping FEM's Python commands use (Gui/AppFemGuiPy.cpp)."""
import FreeCAD as App
_active = {}

def setActiveAnalysis(analysis=None):
    _active[App.ActiveDocument.Name if App.ActiveDocument else ''] = analysis

def getActiveAnalysis():
    d = App.ActiveDocument
    a = _active.get(d.Name if d else '')
    if a is None and d is not None:
        a = next((o for o in d.Objects if o.isDerivedFrom('Fem::FemAnalysis')), None)
    return a

def open(*a, **k): pass
def insert(*a, **k): pass

from _stub import module_getattr as __getattr__  # noqa: E402,F401

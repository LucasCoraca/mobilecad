"""MatGui on mobile: MaterialTreeWidget wraps the placeholder widget a .ui file reserves for FreeCAD's material tree
(FEM materials, CAM job stock / tool bit materials).  It fills that widget with a combo of the material library (filtered
like the desktop tree) and emits the container's onMaterial(uuid) signal on a choice, which the panels connect to."""
from PySide import _widgets as W


class MaterialTreeWidget:
    def __init__(self, container=None):
        self._box = container if isinstance(container, W.QWidget) else W.QWidget()
        self._box.__dict__['onMaterial'] = W._Bound(self._box, 'onMaterial')
        self._filter = None
        self._uuids = []
        self._combo = W.QComboBox(self._box)
        self._combo.setObjectName('material')
        self._combo.setToolTip('Material from the FreeCAD material library')
        lay = W.QVBoxLayout(); lay.addWidget(self._combo); self._box.setLayout(lay)
        self.expanded = False
        self.IncludeFavorites = True
        self.IncludeRecent = True
        self.IncludeEmptyFolders = False
        self.IncludeEmptyLibraries = False
        self.IncludeLegacy = False
        self._fill()
        self._combo.currentIndexChanged.connect(self._chosen)

    def _materials(self):
        import Materials
        mats = Materials.MaterialManager().Materials
        req = list(getattr(self._filter, 'RequiredModels', None) or []) if self._filter is not None else []
        req_c = list(getattr(self._filter, 'RequiredCompleteModels', None) or []) if self._filter is not None else []
        out = []
        for uuid, m in mats.items():
            try:
                if any(not m.hasPhysicalModel(r) for r in req): continue
                if any(not m.isPhysicalModelComplete(r) for r in req_c): continue
                out.append((m.Name or uuid, uuid, getattr(m, 'Directory', '') or getattr(m, 'LibraryName', '') or ''))
            except Exception:
                continue
        # the desktop tree groups by library / folder; here same-named materials are told apart by their folder
        names = [n for n, _, _ in out]
        out = [(n + (' (%s)' % lib if lib and names.count(n) > 1 else ''), u) for n, u, lib in out]
        return sorted(set(out), key=lambda x: x[0].lower())

    def _fill(self, keep=None):
        try: items = self._materials()
        except Exception: items = []
        self._combo.blockSignals(True)
        self._combo.clear()
        self._uuids = [''] + [u for _, u in items]
        self._combo.addItems(['(choose a material)'] + [n for n, _ in items])
        self._combo.setCurrentIndex(self._uuids.index(keep) if keep in self._uuids else 0)
        self._combo.blockSignals(False)

    def _chosen(self, i):
        if 0 < i < len(self._uuids): self._box.onMaterial.emit(self._uuids[i])

    @property
    def UUID(self):
        i = self._combo.currentIndex()
        return self._uuids[i] if 0 <= i < len(self._uuids) else ''

    @UUID.setter
    def UUID(self, uuid):
        self._combo.blockSignals(True)
        self._combo.setCurrentIndex(self._uuids.index(uuid) if uuid in self._uuids else 0)
        self._combo.blockSignals(False)

    def setFilter(self, f):
        self._filter = f[0] if isinstance(f, (list, tuple)) and f else f
        self._fill(self.UUID)

    def resetFilter(self):
        self._filter = None; self._fill(self.UUID)

    def __getattr__(self, name):
        if name.startswith('_'): raise AttributeError(name)
        from _stub import Stub
        return Stub()

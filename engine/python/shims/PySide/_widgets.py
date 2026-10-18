"""A small, real Qt widget model for headless FreeCAD on Android.

FreeCAD's Python task panels and dialogs (BIM, CAM, FEM, Draft ...) build Qt forms (often from .ui files), connect signals
to their logic and read values back in accept().  These classes keep that logic working: widgets hold their state, signals
call their slots, layouts keep order.  The app renders a panel's widget tree as a mobile form (mobilecad.taskpanel) and
sends the user's edits back as setValue / click events, which emit the same signals Qt would.  Nothing is drawn here."""
import itertools
from _stub import Stub as _Stub

_ids = itertools.count(1)

def _noop(*a, **k): return _Stub()

def _fallback(name):
    """Qt API this model does not keep (geometry, style, focus, size hints ...): accepted, returns a placeholder."""
    if name.startswith('_'): raise AttributeError(name)
    return _Stub()

# ------------------------------------------------------------------ signals
def _arity(fn, n):
    """How many of the signal's n arguments the slot accepts."""
    import inspect
    try:
        ps = inspect.signature(fn).parameters.values()
    except (TypeError, ValueError):
        return n
    if any(p.kind == p.VAR_POSITIONAL for p in ps): return n
    return min(n, sum(1 for p in ps if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)))

class _Bound:
    def __init__(self, owner, name):
        self.owner = owner; self.name = name; self.slots = []
    def connect(self, slot, *a, **k):
        if callable(slot): self.slots.append(slot)
        return True
    def disconnect(self, slot=None, *a):
        if slot is None: self.slots.clear()
        elif slot in self.slots: self.slots.remove(slot)
    def emit(self, *args):
        if getattr(self.owner, '_blocked', False): return
        for s in list(self.slots):
            s(*args[:_arity(s, len(args))])               # Qt passes only as many arguments as the slot takes
    def __call__(self, *args): self.emit(*args)
    def __getitem__(self, _types): return self           # sig[int].connect(...)

class Signal:
    """QtCore.Signal: a per-instance bound signal."""
    def __init__(self, *types, **kw): self.name = None
    def __set_name__(self, owner, name): self.name = name
    def __get__(self, obj, objtype=None):
        if obj is None: return self
        d = obj.__dict__.setdefault('_signals', {})
        if self.name not in d: d[self.name] = _Bound(obj, self.name)
        return d[self.name]

def Slot(*a, **k):
    return lambda f: f

def Property(*a, **k):
    return property(*a) if a and callable(a[0]) else (lambda f: property(f))

# ------------------------------------------------------------------ object / widget
class _QtMeta(type):
    """Qt enum values the model does not list (QFileDialog.ExistingFiles, QLineEdit.Normal ...) resolve to placeholder
    enums that combine in flag expressions."""
    def __getattr__(cls, name):
        if name[:1].isupper():
            from _stub import _StubMeta, Stub
            return _StubMeta(name, (Stub,), {})
        raise AttributeError(name)

def SIGNAL(s): return str(s).split('(')[0]
def SLOT(s): return str(s).split('(')[0]

class QObject(metaclass=_QtMeta):
    @staticmethod
    def connect(sender, signal, slot, *a):
        """Old-style QObject.connect(sender, SIGNAL('clicked()'), slot)."""
        sig = getattr(sender, SIGNAL(signal), None)
        if hasattr(sig, 'connect') and callable(slot): sig.connect(slot)
        return True
    destroyed = Signal()
    objectNameChanged = Signal(str)
    def __init__(self, parent=None, *a, **k):
        self._id = next(_ids); self._name = ''; self._parent = None; self._children = []; self._props = {}; self._blocked = False
        if isinstance(parent, QObject): self.setParent(parent)
    def setParent(self, p, *a):
        if self._parent is not None and self in self._parent._children: self._parent._children.remove(self)
        self._parent = p
        if p is not None and self not in p._children: p._children.append(self)
    def parent(self): return self._parent
    def children(self): return list(self._children)
    def objectName(self): return self._name
    def setObjectName(self, n): self._name = str(n)
    def property(self, name): return self._props.get(name)
    def setProperty(self, name, value): self._props[name] = value; return True
    def blockSignals(self, b): old = self._blocked; self._blocked = bool(b); return old
    def signalsBlocked(self): return self._blocked
    def installEventFilter(self, *a): pass
    def removeEventFilter(self, *a): pass
    def deleteLater(self): pass
    def tr(self, t, *a): return t
    def metaObject(self): return _Meta(self)
    def __getattr__(self, name): return _fallback(name)
    def findChild(self, cls=None, name=''):
        for c in self.findChildren(cls, name): return c
        return None
    def findChildren(self, cls=None, name=''):
        out = []
        for c in self._all():
            if c is self: continue
            if (cls is None or isinstance(c, cls)) and (not name or c._name == name): out.append(c)
        return out
    def _all(self):
        yield self
        seen = set()
        for c in self._children:
            if id(c) in seen: continue
            seen.add(id(c))
            if isinstance(c, QObject): yield from c._all()
        lay = getattr(self, '_layout', None)
        if lay is not None:
            for w in lay._widgets():
                if id(w) not in seen: seen.add(id(w)); yield from w._all()

class _Meta:
    def __init__(self, o): self.o = o
    def className(self): return type(self.o).__name__

class QWidget(QObject):
    def __init__(self, parent=None, *a, **k):
        super().__init__(parent if isinstance(parent, QObject) else None)
        self._visible = True; self._enabled = True; self._tooltip = ''; self._title = ''; self._layout = None
    # visibility / state
    def show(self): self._visible = True
    def hide(self): self._visible = False
    def setVisible(self, v): self._visible = bool(v)
    def setHidden(self, h): self._visible = not h
    def isVisible(self): return self._visible
    def isHidden(self): return not self._visible
    def setEnabled(self, e): self._enabled = bool(e)
    def setDisabled(self, d): self._enabled = not d
    def isEnabled(self): return self._enabled
    def setToolTip(self, t): self._tooltip = str(t)
    def toolTip(self): return self._tooltip
    def setWindowTitle(self, t): self._title = str(t)
    def windowTitle(self): return self._title
    def setLayout(self, l): self._layout = l; l._owner = self
    def addAction(self, *a, **k):
        act = a[0] if a and isinstance(a[0], QAction) else QAction(*[x for x in a if isinstance(x, str)][:1])
        self.__dict__.setdefault('_acts', []).append(act); return act
    def actions(self): return list(self.__dict__.get('_acts', []))
    def layout(self): return self._layout
    def close(self): self._visible = False; return True
    def parentWidget(self): return self._parent if isinstance(self._parent, QWidget) else None
    def window(self):
        w = self
        while w.parentWidget() is not None: w = w.parentWidget()
        return w
    # geometry: nothing is laid out; widgets report a roomy size and a zero size hint so "does it fit" checks pass
    def width(self): return 10000
    def height(self): return 10000
    def sizeHint(self): return _Size(0, 0)
    def minimumSizeHint(self): return _Size(0, 0)
    def size(self): return _Size(10000, 10000)
    def __getattr__(self, name): return _fallback(name)

class _Size:
    def __init__(self, w, h): self._w = w; self._h = h
    def width(self): return self._w
    def height(self): return self._h
    def isValid(self): return True

# ------------------------------------------------------------------ layouts
class _Item:
    def __init__(self, w=None, l=None): self._w = w; self._l = l
    def widget(self): return self._w
    def layout(self): return self._l
    def spacerItem(self): return None

class QLayout(QObject):
    def __init__(self, parent=None, *a, **k):
        super().__init__(None); self._items = []
        if isinstance(parent, QWidget): parent.setLayout(self)
    def addWidget(self, w, *a, **k):
        if w is not None: self._items.append(('w', w, a))
    def insertWidget(self, i, w, *a, **k): self._items.insert(i, ('w', w, a))
    def addLayout(self, l, *a, **k): self._items.append(('l', l, a))
    def insertLayout(self, i, l, *a, **k): self._items.insert(i, ('l', l, a))
    def addItem(self, it, *a, **k): pass
    def addStretch(self, *a): pass
    def addSpacing(self, *a): pass
    def addSpacerItem(self, *a): pass
    def removeWidget(self, w): self._items = [x for x in self._items if x[1] is not w]
    def count(self): return len(self._items)
    def itemAt(self, i): k, o, _ = self._items[i]; return _Item(o if k == 'w' else None, o if k == 'l' else None)
    def takeAt(self, i): it = self.itemAt(i); del self._items[i]; return it
    def setContentsMargins(self, *a): pass
    def setSpacing(self, *a): pass
    def setAlignment(self, *a): pass
    def setStretch(self, *a): pass
    def _widgets(self):
        for k, o, _ in self._items:
            if k == 'w': yield o
            else: yield from o._widgets()
    def __getattr__(self, name):
        return _fallback(name)

class QBoxLayout(QLayout): pass
class QVBoxLayout(QBoxLayout): pass
class QHBoxLayout(QBoxLayout): pass
class QGridLayout(QLayout):
    def addWidget(self, w, row=0, col=0, *a, **k): self._items.append(('w', w, (row, col)))
    def addLayout(self, l, row=0, col=0, *a, **k): self._items.append(('l', l, (row, col)))
class QFormLayout(QLayout):
    def addRow(self, a, b=None):
        if b is None:
            (self.addLayout if isinstance(a, QLayout) else self.addWidget)(a)
        else:
            lab = QLabel(a) if isinstance(a, str) else a
            self._items.append(('w', lab, ('label',)))
            (self.addLayout if isinstance(b, QLayout) else self.addWidget)(b)
    def insertRow(self, i, a, b=None): self.addRow(a, b)
    def setWidget(self, row, role, w): self.addWidget(w)
    def rowCount(self): return len(self._items)
class QStackedLayout(QLayout): pass
class QSpacerItem:
    def __init__(self, *a, **k): pass

# ------------------------------------------------------------------ widgets
class QFrame(QWidget): pass
class QLabel(QFrame):
    linkActivated = Signal(str)
    def __init__(self, text='', parent=None, *a, **k):
        if isinstance(text, QObject): parent, text = text, ''
        super().__init__(parent); self._text = str(text)
    def text(self): return self._text
    def setText(self, t): self._text = str(t)
    def setPixmap(self, *a): pass
    def clear(self): self._text = ''

class QLineEdit(QWidget):
    textChanged = Signal(str); textEdited = Signal(str); editingFinished = Signal(); returnPressed = Signal()
    def __init__(self, text='', parent=None, *a, **k):
        if isinstance(text, QObject): parent, text = text, ''
        super().__init__(parent); self._text = str(text); self._ro = False; self._ph = ''
    def text(self): return self._text
    def setText(self, t):
        t = str(t)
        if t != self._text: self._text = t; self.textChanged.emit(t)
    def clear(self): self.setText('')
    def setReadOnly(self, r): self._ro = bool(r)
    def isReadOnly(self): return self._ro
    def setPlaceholderText(self, t): self._ph = str(t)
    def _user(self, v): self.setText(v); self.textEdited.emit(self._text); self.editingFinished.emit(); self.returnPressed.emit()

class QAbstractSpinBox(QWidget):
    editingFinished = Signal()
    def __init__(self, parent=None, *a, **k):
        super().__init__(parent); self._v = 0; self._min = 0; self._max = 99; self._step = 1; self._suffix = ''; self._prefix = ''; self._dec = 0
    def value(self): return self._v
    def setValue(self, v):
        v = self._coerce(v)
        if v != self._v: self._v = v; self._changed()
    def _coerce(self, v): return max(self._min, min(self._max, int(round(float(v)))))
    def _changed(self): self.valueChanged.emit(self._v)
    def minimum(self): return self._min
    def maximum(self): return self._max
    def setMinimum(self, v): self._min = v
    def setMaximum(self, v): self._max = v
    def setRange(self, a, b): self._min, self._max = a, b
    def setSingleStep(self, s): self._step = s
    def singleStep(self): return self._step
    def setSuffix(self, s): self._suffix = str(s)
    def suffix(self): return self._suffix
    def setPrefix(self, s): self._prefix = str(s)
    def text(self): return '%s%s%s' % (self._prefix, self._v, self._suffix)
    def cleanText(self): return str(self._v)
    def setReadOnly(self, *a): pass
    def setKeyboardTracking(self, *a): pass
    def _user(self, v): self.setValue(v); self.editingFinished.emit()

class QSpinBox(QAbstractSpinBox):
    valueChanged = Signal(int); textChanged = Signal(str)

class QDoubleSpinBox(QAbstractSpinBox):
    valueChanged = Signal(float); textChanged = Signal(str)
    def __init__(self, parent=None, *a, **k):
        super().__init__(parent); self._dec = 2; self._v = 0.0; self._max = 99.99
    def _coerce(self, v): return max(self._min, min(self._max, round(float(v), self._dec)))
    def setDecimals(self, d): self._dec = int(d)
    def decimals(self): return self._dec

class QuantitySpinBox(QAbstractSpinBox):
    """Gui::QuantitySpinBox / Gui::InputField: a Quantity with unit; FreeCAD code reads property('rawValue') / value()
    / property('quantity') and sets setProperty('rawValue', x) or setValue(Quantity)."""
    valueChanged = Signal(object); textChanged = Signal(str)
    # _v is FreeCAD's internal value (mm, kg, s ... based), _unit the unit shown: none (as in Qt) until a .ui / setUnit / a Quantity sets it
    def __init__(self, parent=None, *a, **k):
        super().__init__(parent); self._v = 0.0; self._min = -1e100; self._max = 1e100; self._unit = ''; self._uset = False; self._dec = 2
    def _coerce(self, v):
        try:
            import FreeCAD
            if isinstance(v, str): v = FreeCAD.Units.Quantity(v)
            if hasattr(v, 'Value') and hasattr(v, 'Unit'):
                if not self._uset:                   # an unconfigured field takes the unit of what it is given
                    self._unit = '' if str(v.Unit) in ('', 'Unit: ') or v.Unit == FreeCAD.Units.Unit() else self._unit_of(v)
                return float(v.Value)
        except Exception:
            pass
        return float(v)
    @staticmethod
    def _unit_of(q):
        try:
            u = q.getUserPreferred()[2]
            return u if u else ''
        except Exception:
            return 'mm'
    def _unit_obj(self):
        import FreeCAD
        return FreeCAD.Units.Quantity('1 ' + self._unit).Unit if self._unit else FreeCAD.Units.Unit()
    def value(self):
        try:
            import FreeCAD
            return FreeCAD.Units.Quantity(self._v, self._unit_obj())
        except Exception:
            return self._v
    def _display(self):
        """(number, unit) as the user sees it: the internal value converted to the field's unit."""
        if self._unit in ('', 'mm', 'deg', '°'): return self._v, self._unit
        try:                                         # the user-preferred scale, as the desktop field shows (210 GPa)
            _, f, u = self.value().getUserPreferred()
            if f and u: return self._v / f, u
        except Exception:
            pass
        try:
            import FreeCAD
            f = FreeCAD.Units.Quantity('1 ' + self._unit).Value
            return (self._v / f if f else self._v), self._unit
        except Exception:
            return self._v, self._unit
    def rawValue(self): return self._v
    def setUnit(self, u):
        self._uset = True
        if isinstance(u, str): self._unit = u
        else:
            try:
                import FreeCAD
                self._unit = self._unit_of(FreeCAD.Units.Quantity(1, u))
            except Exception:
                self._unit = str(u)
    def unit(self): return self._unit_obj() if self._unit else self._unit
    def property(self, name):
        if name == 'rawValue': return self._v
        if name in ('value', 'quantity'): return self.value()
        if name == 'unit': return self._unit
        return super().property(name)
    def setProperty(self, name, value):
        if name in ('rawValue', 'value', 'quantity'): self.setValue(value); return True
        if name == 'unit': self.setUnit(value); return True
        return super().setProperty(name, value)
    def text(self): return ('%g %s' % self._display()).strip()
    def setText(self, t): self.setValue(t)
    def bind(self, *a, **k): pass
    def setExpression(self, *a): pass
    def _changed(self): self.valueChanged.emit(self.value()); self.textChanged.emit(self.text())

class QAbstractButton(QWidget):
    clicked = Signal(bool); toggled = Signal(bool); pressed = Signal(); released = Signal()
    def __init__(self, text='', parent=None, *a, **k):
        if isinstance(text, QObject): parent, text = text, ''
        super().__init__(parent); self._text = str(text) if not hasattr(text, 'pixmap') else ''; self._checkable = False; self._checked = False
    def text(self): return self._text
    def setText(self, t): self._text = str(t)
    def setCheckable(self, c): self._checkable = bool(c)
    def isCheckable(self): return self._checkable
    def isChecked(self): return self._checked
    def setChecked(self, c):
        c = bool(c)
        if c != self._checked:
            self._checked = c; self.toggled.emit(c)
            if hasattr(self, 'stateChanged'): self.stateChanged.emit(2 if c else 0)
    def click(self):
        if self._checkable: self.setChecked(not self._checked)
        self.clicked.emit(self._checked)
    def setIcon(self, *a): pass
    def setMenu(self, m): self._menu = m
    def menu(self): return getattr(self, '_menu', None)
    def _user(self, v=None):
        if self._checkable and v is not None: self.setChecked(bool(v)); self.clicked.emit(self._checked)
        else: self.click()

class QPushButton(QAbstractButton): pass
class QToolButton(QAbstractButton): pass
class QCheckBox(QAbstractButton):
    stateChanged = Signal(int); checkStateChanged = Signal(int)
    def __init__(self, *a, **k): super().__init__(*a, **k); self._checkable = True
    def checkState(self): return 2 if self._checked else 0
    def setCheckState(self, s): self.setChecked(int(getattr(s, 'value', s) or 0) != 0)
    def setTristate(self, *a): pass
class QRadioButton(QAbstractButton):
    def __init__(self, *a, **k): super().__init__(*a, **k); self._checkable = True
    def setChecked(self, c):
        super().setChecked(c)
        if c and self._parent is not None:                # auto-exclusive among siblings
            for s in self._parent._children:
                if isinstance(s, QRadioButton) and s is not self and s._checked: s._checked = False; s.toggled.emit(False)

class QComboBox(QWidget):
    currentIndexChanged = Signal(int); currentTextChanged = Signal(str); activated = Signal(int); textActivated = Signal(str)
    editTextChanged = Signal(str)
    def __init__(self, parent=None, *a, **k): super().__init__(parent); self._items = []; self._cur = -1; self._editable = False
    def addItem(self, *a, **k):
        if 'userData' in k: a = a + (k['userData'],)
        a = [x for x in a if not (hasattr(x, 'pixmap') or type(x).__name__ in ('QIcon', '_StubMeta'))] or ['']
        self._items.append([str(a[0]), a[1] if len(a) > 1 else None])
        if self._cur < 0: self.setCurrentIndex(0)
    def addItems(self, ts):
        for t in ts: self.addItem(t)
    def insertItem(self, i, *a): self._items.insert(i, [str(a[-1] if isinstance(a[-1], str) else a[0]), None])
    def removeItem(self, i): del self._items[i]; self._cur = min(self._cur, len(self._items) - 1)
    def clear(self): self._items = []; self._cur = -1
    def count(self): return len(self._items)
    def currentIndex(self): return self._cur
    def setCurrentIndex(self, i):
        i = int(i)
        if i != self._cur and -1 <= i < len(self._items):
            self._cur = i; self.currentIndexChanged.emit(i); self.currentTextChanged.emit(self.currentText())
    def currentText(self): return self._items[self._cur][0] if 0 <= self._cur < len(self._items) else ''
    def setCurrentText(self, t):
        i = self.findText(t)
        if i >= 0: self.setCurrentIndex(i)
    def currentData(self, *a): return self._items[self._cur][1] if 0 <= self._cur < len(self._items) else None
    def itemText(self, i): return self._items[i][0] if 0 <= i < len(self._items) else ''
    def itemData(self, i, *a): return self._items[i][1] if 0 <= i < len(self._items) else None
    def setItemText(self, i, t): self._items[i][0] = str(t)
    def setItemData(self, i, d, *a): self._items[i][1] = d
    def findText(self, t, *a): return next((i for i, x in enumerate(self._items) if x[0] == t), -1)
    def findData(self, d, *a): return next((i for i, x in enumerate(self._items) if x[1] == d), -1)
    def setEditable(self, e): self._editable = bool(e)
    def setEditText(self, t): pass
    def model(self): return _Model(self)
    def view(self): return self
    def _user(self, v): self.setCurrentIndex(int(v)); self.activated.emit(self._cur); self.textActivated.emit(self.currentText())


class QFont(metaclass=_QtMeta):
    def __init__(self, family='Sans', size=10, *a):
        self._f = family if isinstance(family, str) else 'Sans'; self._s = size if isinstance(size, (int, float)) else 10
    def family(self): return self._f
    def setFamily(self, f): self._f = str(f)
    def defaultFamily(self): return 'Sans'
    def pointSize(self): return int(self._s)
    def pointSizeF(self): return float(self._s)
    def setPointSize(self, s): self._s = s
    setPointSizeF = setPointSize
    def setStyleHint(self, *a): pass
    def setBold(self, *a): pass
    def setItalic(self, *a): pass
    def bold(self): return False
    def toString(self): return self._f
    def __getattr__(self, n): return _fallback(n)

class QFontComboBox(QComboBox):
    currentFontChanged = Signal(object)
    def __init__(self, *a, **k): super().__init__(*a, **k); self.addItems(['Sans', 'Serif', 'Monospace'])
    def currentFont(self): return QFont(self.currentText())
    def setCurrentFont(self, f): self.setCurrentText(f.family() if hasattr(f, 'family') else str(f))

class _Model:
    def __init__(self, w): self.w = w
    def rowCount(self, *a): return self.w.count()
    def item(self, i, *a): return _ListItemProxy(self.w, i)
    def __getattr__(self, n): return _fallback(n)
class _ListItemProxy:
    def __init__(self, w, i): self.w = w; self.i = i
    def __getattr__(self, n): return _fallback(n)

class QListWidgetItem:
    def __init__(self, text='', parent=None, *a):
        if not isinstance(text, str): parent, text = text, ''
        self._text = str(text); self._data = {}; self._check = None; self._selected = False; self._flags = 0; self._tooltip = ''
        if isinstance(parent, QListWidget): parent.addItem(self)
    def text(self): return self._text
    def setText(self, t): self._text = str(t)
    def data(self, role=0): return self._data.get(int(getattr(role, 'value', role) or 0), self._text if not role else None)
    def setData(self, role, v): self._data[int(getattr(role, 'value', role) or 0)] = v
    def checkState(self): return self._check or 0
    def setCheckState(self, s): self._check = int(getattr(s, 'value', s) or 0)
    def isSelected(self): return self._selected
    def setSelected(self, s): self._selected = bool(s)
    def setFlags(self, f): self._flags = f
    def flags(self): return self._flags
    def setToolTip(self, *a): self._tooltip = str(a[-1])
    def setIcon(self, *a): pass
    def __getattr__(self, n): return _fallback(n)

class QListWidget(QWidget):
    currentRowChanged = Signal(int); itemSelectionChanged = Signal(); itemClicked = Signal(object); itemDoubleClicked = Signal(object)
    currentItemChanged = Signal(object, object); itemChanged = Signal(object)
    def __init__(self, parent=None, *a, **k): super().__init__(parent); self._items = []; self._cur = -1
    def addItem(self, it):
        if isinstance(it, str): it = QListWidgetItem(it)
        self._items.append(it)
    def addItems(self, ts):
        for t in ts: self.addItem(t)
    def insertItem(self, i, it): self._items.insert(i, it if not isinstance(it, str) else QListWidgetItem(it))
    def item(self, i): return self._items[i] if 0 <= i < len(self._items) else None
    def count(self): return len(self._items)
    def clear(self): self._items = []; self._cur = -1
    def takeItem(self, i): return self._items.pop(i) if 0 <= i < len(self._items) else None
    def row(self, it): return self._items.index(it) if it in self._items else -1
    def currentRow(self): return self._cur
    def setCurrentRow(self, r):
        if r != self._cur:
            prev = self.item(self._cur); self._cur = r; self.currentRowChanged.emit(r); self.currentItemChanged.emit(self.item(r), prev)
    def currentItem(self): return self.item(self._cur)
    def setCurrentItem(self, it): self.setCurrentRow(self.row(it))
    def selectedItems(self): return [i for i in self._items if i._selected] or ([self.item(self._cur)] if self._cur >= 0 else [])
    def findItems(self, t, *a): return [i for i in self._items if i._text == t]
    def _user(self, v):
        r = int(v)
        for i, it in enumerate(self._items): it._selected = (i == r)
        self.setCurrentRow(r); self.itemSelectionChanged.emit()
        if self.item(r) is not None: self.itemClicked.emit(self.item(r))
    def _check(self, r, on):
        it = self.item(int(r))
        if it is not None: it.setCheckState(2 if on else 0); self.itemChanged.emit(it)

class QTreeWidgetItem(QListWidgetItem):
    def __init__(self, parent=None, texts=None, *a):
        if isinstance(parent, (list, tuple)): parent, texts = None, parent
        super().__init__((texts or [''])[0] if texts else ''); self._texts = [str(t) for t in (texts or [])]; self._kids = []
        if isinstance(parent, QTreeWidgetItem): parent.addChild(self)
        elif isinstance(parent, QTreeWidget): parent.addTopLevelItem(self)
    def text(self, col=0): return self._texts[col] if col < len(self._texts) else ''
    def setText(self, col, t=None):
        if t is None: col, t = 0, col
        while len(self._texts) <= col: self._texts.append('')
        self._texts[col] = str(t)
    def addChild(self, c): self._kids.append(c)
    def child(self, i): return self._kids[i] if i < len(self._kids) else None
    def childCount(self): return len(self._kids)
    def setExpanded(self, *a): pass
    def checkState(self, col=0): return self._check or 0
    def setCheckState(self, col, s=None): self._check = int(getattr(s if s is not None else col, 'value', s if s is not None else col) or 0)
    def data(self, col=0, role=0): return self._data.get((col, int(getattr(role, 'value', role) or 0)))
    def setData(self, col, role, v=None): self._data[(col, int(getattr(role, 'value', role) or 0))] = v

class QTreeWidget(QListWidget):
    def addTopLevelItem(self, it): self._items.append(it)
    def addTopLevelItems(self, its): self._items.extend(its)
    def topLevelItem(self, i): return self.item(i)
    def topLevelItemCount(self): return self.count()
    def setHeaderLabels(self, *a): pass
    def setColumnCount(self, *a): pass
    def invisibleRootItem(self): return _Root(self)
class _Root:
    def __init__(self, t): self.t = t
    def childCount(self): return self.t.count()
    def child(self, i): return self.t.item(i)
    def addChild(self, c): self.t.addTopLevelItem(c)

class QTableWidgetItem(QListWidgetItem): pass
class QTableWidget(QWidget):
    cellChanged = Signal(int, int); itemChanged = Signal(object); cellClicked = Signal(int, int)
    def __init__(self, rows=0, cols=0, parent=None, *a, **k):
        if isinstance(rows, QObject): parent, rows = rows, 0
        super().__init__(parent); self._r = int(rows); self._c = int(cols); self._cells = {}; self._hh = []
    def setRowCount(self, r): self._r = int(r)
    def setColumnCount(self, c): self._c = int(c)
    def rowCount(self): return self._r
    def columnCount(self): return self._c
    def setItem(self, r, c, it): self._cells[(r, c)] = it
    def item(self, r, c): return self._cells.get((r, c))
    def setHorizontalHeaderLabels(self, ls): self._hh = list(ls)
    def insertRow(self, r):
        self._cells = {((rr + 1 if rr >= r else rr), c): v for (rr, c), v in self._cells.items()}; self._r += 1
    def removeRow(self, r):
        self._cells = {((rr - 1 if rr > r else rr), c): v for (rr, c), v in self._cells.items() if rr != r}; self._r = max(0, self._r - 1)
    def insertColumn(self, c):
        self._cells = {(r, (cc + 1 if cc >= c else cc)): v for (r, cc), v in self._cells.items()}; self._c += 1
    def removeColumn(self, c):
        self._cells = {(r, (cc - 1 if cc > c else cc)): v for (r, cc), v in self._cells.items() if cc != c}; self._c = max(0, self._c - 1)
    def setHorizontalHeaderItem(self, c, it):
        while len(self._hh) <= c: self._hh.append('')
        self._hh[c] = it.text() if hasattr(it, 'text') else str(it)
    def horizontalHeaderItem(self, c): return QTableWidgetItem(self._hh[c]) if c < len(self._hh) else None
    def setVerticalHeaderItem(self, r, it): self._props.setdefault('_rows', {})[r] = it.text() if hasattr(it, 'text') else str(it)
    def setVerticalHeaderLabels(self, ls): self._props['_rows'] = dict(enumerate(ls))
    def currentRow(self): return self._props.get('_cur', (0, 0))[0]
    def currentColumn(self): return self._props.get('_cur', (0, 0))[1]
    def setCurrentCell(self, r, c): self._props['_cur'] = (r, c)
    def takeItem(self, r, c): return self._cells.pop((r, c), None)
    def clearContents(self): self._cells = {}
    def setCellWidget(self, r, c, w): self._cells[(r, c)] = w
    def cellWidget(self, r, c): return self._cells.get((r, c))
    def _user_cell(self, r, c, text):
        it = self._cells.get((r, c)) or QTableWidgetItem('')
        it.setText(text); self._cells[(r, c)] = it; self.cellChanged.emit(r, c); self.itemChanged.emit(it)

class QGroupBox(QWidget):
    toggled = Signal(bool); clicked = Signal(bool)
    def __init__(self, title='', parent=None, *a, **k):
        if isinstance(title, QObject): parent, title = title, ''
        super().__init__(parent); self._title = str(title); self._checkable = False; self._checked = True
    def title(self): return self._title
    def setTitle(self, t): self._title = str(t)
    def setCheckable(self, c): self._checkable = bool(c)
    def isCheckable(self): return self._checkable
    def isChecked(self): return self._checked
    def setChecked(self, c):
        if bool(c) != self._checked: self._checked = bool(c); self.toggled.emit(self._checked)
    def _user(self, v): self.setChecked(bool(v)); self.clicked.emit(self._checked)

class QTabWidget(QWidget):
    currentChanged = Signal(int)
    def __init__(self, parent=None, *a, **k): super().__init__(parent); self._tabs = []; self._cur = 0
    def addTab(self, w, *label):
        self._tabs.append((w, str(label[-1]) if label else '')); w.setParent(self); return len(self._tabs) - 1
    def insertTab(self, i, w, *label): self._tabs.insert(i, (w, str(label[-1]) if label else '')); w.setParent(self)
    def widget(self, i): return self._tabs[i][0] if 0 <= i < len(self._tabs) else None
    def count(self): return len(self._tabs)
    def currentIndex(self): return self._cur
    def setCurrentIndex(self, i):
        if i != self._cur: self._cur = int(i); self.currentChanged.emit(self._cur)
    def currentWidget(self): return self.widget(self._cur)
    def tabText(self, i): return self._tabs[i][1]
    def setTabText(self, i, t): self._tabs[i] = (self._tabs[i][0], str(t))
    def setTabEnabled(self, *a): pass
    def removeTab(self, i): del self._tabs[i]
    def setTabToolTip(self, i, t): self.__dict__.setdefault('_tips', {})[i] = str(t)
    def tabToolTip(self, i): return self.__dict__.get('_tips', {}).get(i, '')
    def tabBar(self): return _TabBar(self)
class _TabBar:
    """QTabWidget.tabBar(): the tab texts/tool tips live on the tab widget (CAM sets labels through the bar)."""
    def __init__(self, tw): self._tw = tw
    def setTabText(self, i, t): self._tw.setTabText(i, t)
    def tabText(self, i): return self._tw.tabText(i)
    def setTabToolTip(self, i, t): self._tw.setTabToolTip(i, t)
    def tabToolTip(self, i): return self._tw.tabToolTip(i)
    def count(self): return self._tw.count()
    def sizeHint(self): return _Size(0, 0)
    def __getattr__(self, name): return _fallback(name)
class QToolBox(QTabWidget):
    def addItem(self, w, *label): return self.addTab(w, *label)
    def setItemText(self, i, t): self.setTabText(i, t)
class QStackedWidget(QTabWidget):
    def addWidget(self, w): return self.addTab(w, '')
    def setCurrentWidget(self, w): self.setCurrentIndex(next((i for i, (x, _) in enumerate(self._tabs) if x is w), 0))
class QScrollArea(QWidget):
    def setWidget(self, w): self._w = w; w.setParent(self)
    def widget(self): return getattr(self, '_w', None)
    def setWidgetResizable(self, *a): pass

class QTextEdit(QWidget):
    textChanged = Signal()
    def __init__(self, text='', parent=None, *a, **k):
        if isinstance(text, QObject): parent, text = text, ''
        super().__init__(parent); self._text = str(text)
    def toPlainText(self): return self._text
    def setPlainText(self, t): self._text = str(t); self.textChanged.emit()
    def setText(self, t): self.setPlainText(t)
    def setHtml(self, t): import re; self.setPlainText(re.sub(r'<[^>]+>', '', str(t)))
    def toHtml(self): return self._text
    def append(self, t): self.setPlainText((self._text + '\n' if self._text else '') + str(t))
    def insertPlainText(self, t): self.setPlainText(self._text + str(t))
    def clear(self): self.setPlainText('')
    def setReadOnly(self, *a): pass
    def document(self): return QTextDocument(self)
    def textCursor(self): return QTextCursor(self)
    def ensureCursorVisible(self): pass
    def _user(self, v): self.setPlainText(v)
class QTextDocument(QObject):
    """The document of a QTextEdit (text lives on the edit)."""
    def __init__(self, edit=None, *a, **k):
        super().__init__(None); self._edit = edit if isinstance(edit, QTextEdit) else None; self._own = edit if isinstance(edit, str) else ''
    def toPlainText(self): return self._edit.toPlainText() if self._edit else self._own
    def setPlainText(self, t):
        if self._edit: self._edit.setPlainText(t)
        else: self._own = str(t)
    def isEmpty(self): return not self.toPlainText()
    def __getattr__(self, name): return _fallback(name)
class QTextCursor:
    """Appending text through a cursor (FEM solver / mesher logs: movePosition(End) + insertText)."""
    End = 11; Start = 1; MoveAnchor = 0; KeepAnchor = 1
    def __init__(self, doc=None, *a):
        self._doc = doc if isinstance(doc, QTextDocument) else QTextDocument(doc) if isinstance(doc, QTextEdit) else None
    def insertText(self, t, *a):
        if self._doc is not None: self._doc.setPlainText(self._doc.toPlainText() + str(t))
    def insertHtml(self, t):
        import re; self.insertText(re.sub(r'<[^>]+>', '', str(t)))
    def movePosition(self, *a): return True
    def beginEditBlock(self): pass
    def endEditBlock(self): pass
    def mergeCharFormat(self, *a): pass
    def setCharFormat(self, *a): pass
    def __getattr__(self, name): return _fallback(name)
class QTextCharFormat:
    def __init__(self, *a, **k): pass
    def __getattr__(self, name): return _fallback(name)
class QPlainTextEdit(QTextEdit):
    def appendPlainText(self, t): self.append(t)
class QTextBrowser(QTextEdit): pass

class QAbstractSlider(QWidget):
    valueChanged = Signal(int); sliderReleased = Signal(); sliderMoved = Signal(int)
    def __init__(self, *a, **k): super().__init__(a[0] if a and isinstance(a[0], QObject) else None); self._v = 0; self._min = 0; self._max = 99
    def value(self): return self._v
    def setValue(self, v):
        v = int(max(self._min, min(self._max, v)))
        if v != self._v: self._v = v; self.valueChanged.emit(v)
    def setRange(self, a, b): self._min, self._max = a, b
    def setMinimum(self, v): self._min = v
    def setMaximum(self, v): self._max = v
    def minimum(self): return self._min
    def maximum(self): return self._max
    def _user(self, v): self.setValue(v); self.sliderReleased.emit()
class QSlider(QAbstractSlider): pass
class QDial(QAbstractSlider): pass
class QProgressBar(QAbstractSlider): pass

class QDialogButtonBox(QWidget):
    accepted = Signal(); rejected = Signal(); clicked = Signal(object); helpRequested = Signal()
    Ok = 0x400; Cancel = 0x400000; Apply = 0x2000000; Close = 0x200000; Yes = 0x4000; No = 0x10000; Reset = 0x4000000; Help = 0x1000000
    class StandardButton:
        Ok = 0x400; Cancel = 0x400000; Apply = 0x2000000; Close = 0x200000; Yes = 0x4000; No = 0x10000; Reset = 0x4000000; Help = 0x1000000; NoButton = 0
    class ButtonRole:
        AcceptRole = 0; RejectRole = 1; ApplyRole = 8; ResetRole = 7; ActionRole = 3
    def __init__(self, *a, **k): super().__init__(a[-1] if a and isinstance(a[-1], QObject) else None)
    def button(self, which): b = QPushButton(); return b
    def addButton(self, *a): return QPushButton(a[0] if a and isinstance(a[0], str) else '')
    def setStandardButtons(self, *a): pass

class QDialog(QWidget):
    accepted = Signal(); rejected = Signal(); finished = Signal(int)
    Accepted = 1; Rejected = 0
    class DialogCode:
        Accepted = 1; Rejected = 0
    def exec(self):
        """Modal: the app shows the dialog and this returns the user's answer (mobilecad.guicmd.coop); outside a command
        worker the dialog is accepted with its current values."""
        r = _modal(self)
        if r is None: self.accept(); return 1
        return r
    exec_ = exec
    def accept(self): self._visible = False; self.accepted.emit(); self.finished.emit(1)
    def reject(self): self._visible = False; self.rejected.emit(); self.finished.emit(0)
    def close(self): self._visible = False; return True
    def done(self, r): self.finished.emit(r)
    def show(self):
        """A non-modal dialog (BIM Layers, Project manager ...): the app shows it as a form until it closes itself."""
        self._visible = True
        if self._parent is None:
            try:
                import FreeCADGui
                FreeCADGui.requests.append(('widget', self))
            except Exception:
                pass
    def open(self): self.show()
    def raise_(self):
        """Already open (BIM_Layers keeps its dialog): bring it back on screen."""
        if self._parent is None: self.show()
    activateWindow = raise_
    def result(self): return 1

class QMessageBox(QDialog):
    """Messages go to the app; questions take their default answer (Yes/Ok), as the user most often would."""
    Ok = 0x400; Cancel = 0x400000; Yes = 0x4000; No = 0x10000; Save = 0x800; Discard = 0x800000; Abort = 0x40000; Retry = 0x80000; Ignore = 0x100000
    NoButton = 0; Close = 0x200000; YesToAll = 0x8000; NoToAll = 0x20000; Apply = 0x2000000
    Information = 1; Warning = 2; Critical = 3; Question = 4; NoIcon = 0
    class StandardButton:
        Ok = 0x400; Cancel = 0x400000; Yes = 0x4000; No = 0x10000; Save = 0x800; Discard = 0x800000; Abort = 0x40000; Retry = 0x80000
        Ignore = 0x100000; NoButton = 0; Close = 0x200000; YesToAll = 0x8000; NoToAll = 0x20000; Apply = 0x2000000
    class Icon:
        Information = 1; Warning = 2; Critical = 3; Question = 4; NoIcon = 0
    class ButtonRole:
        AcceptRole = 0; RejectRole = 1; ActionRole = 3; YesRole = 5; NoRole = 6
    def __init__(self, *a, **k):
        super().__init__(None); self._text = ''; self._buttons = []; self._clicked = None; self._std = 0x400
        if len(a) >= 2 and isinstance(a[1], str): self._title = a[1]
        if len(a) >= 3: self._text = str(a[2])
        if len(a) >= 4 and isinstance(a[3], int): self._std = a[3]
        self._lab = QLabel(self._text, self)
    def setWindowTitle(self, t): self._title = str(t)
    def setStandardButtons(self, b): self._std = int(b) if isinstance(b, int) else self._std
    def _button_names(self):
        names = [b._text or 'ok' for b in self._buttons]
        return names + [n for f, n in _BTN_NAMES if self._std & f] if not names else names
    def _answer(self, which):
        for b in self._buttons:
            if (b._text or 'ok') == which: self._clicked = b; return self._buttons.index(b)
        return next((f for f, n in _BTN_NAMES if n == which), 0)
    def setText(self, t): self._text = str(t); self._lab._text = self._text
    def text(self): return self._text
    def setInformativeText(self, t): self._text += '\n' + str(t); self._lab._text = self._text
    def setDetailedText(self, t): pass
    def setIcon(self, *a): pass
    def setDefaultButton(self, b): self._clicked = b
    def addButton(self, *a):
        b = a[0] if a and isinstance(a[0], QAbstractButton) else QPushButton(a[0] if a and isinstance(a[0], str) else '')
        self._buttons.append(b); return b
    def button(self, *a): return QPushButton()
    def clickedButton(self): return self._clicked or (self._buttons[-1] if self._buttons else None)
    def exec(self):
        r = _modal(self)
        if r is None: _message(self._text); return (self._buttons.index(self._clicked) if self._clicked in self._buttons else 0) if self._buttons else self.Ok
        return r
    exec_ = exec
    @staticmethod
    def information(parent, title, text, *a, **k): _message(text, title); return QMessageBox.Ok
    @staticmethod
    def warning(parent, title, text, buttons=0x400, *a, **k):
        return _ask(title, text, buttons, 0x400) if isinstance(buttons, int) and buttons != 0x400 else (_message(text, title) or QMessageBox.Ok)
    @staticmethod
    def critical(parent, title, text, *a, **k): _message(text, title); return QMessageBox.Ok
    @staticmethod
    def question(parent, title, text, buttons=0x4000 | 0x10000, default=None, *a, **k):
        return _ask(title, text, buttons if isinstance(buttons, int) else 0x4000 | 0x10000, 0x4000)
    @staticmethod
    def about(parent, title, text): _message(text, title)

def _modal(dlg):
    try:
        from mobilecad import guicmd
        return guicmd._modal(dlg)
    except ImportError:
        return None

_BTN_NAMES = [(0x400, 'ok'), (0x4000, 'yes'), (0x8000, 'yes to all'), (0x800, 'save'), (0x2000000, 'apply'), (0x80000, 'retry'),
              (0x100000, 'ignore'), (0x10000, 'no'), (0x20000, 'no to all'), (0x800000, 'discard'), (0x40000, 'abort'),
              (0x200000, 'close'), (0x400000, 'cancel')]

def _ask(title, text, buttons, default):
    """Message box on a command worker: the user's button (standard-button code), or `default` off-worker."""
    m = QMessageBox(None, title, text, buttons)
    r = _modal(m)
    return default if r is None else r

def _message(text, title=''):
    try:
        import FreeCADGui
        FreeCADGui.requests.append(('message', (str(title) + ': ' if title else '') + str(text)))
    except Exception:
        pass

class QInputDialog(QDialog):
    """getText / getDouble / getInt / getItem: a small form the user fills (on a command worker), else the default."""
    @staticmethod
    def _form(title, label, field):
        d = QDialog(); d.setWindowTitle(title); lay = QVBoxLayout(d); lay.addWidget(QLabel(label)); lay.addWidget(field)
        r = _modal(d)
        return r is None or bool(r)
    @staticmethod
    def getText(parent, title, label, *a, **k):
        default = next((x for x in a if isinstance(x, str)), k.get('text', ''))
        f = QLineEdit(default); ok = QInputDialog._form(title, label, f)
        return f.text(), ok
    @staticmethod
    def getDouble(parent, title, label, value=0.0, mn=-2147483647.0, mx=2147483647.0, decimals=1, *a, **k):
        f = QDoubleSpinBox(); f._min, f._max, f._dec, f._v = mn, mx, decimals, float(value); ok = QInputDialog._form(title, label, f)
        return f.value(), ok
    @staticmethod
    def getInt(parent, title, label, value=0, mn=-2147483647, mx=2147483647, *a, **k):
        f = QSpinBox(); f._min, f._max, f._v = mn, mx, int(value); ok = QInputDialog._form(title, label, f)
        return f.value(), ok
    @staticmethod
    def getItem(parent, title, label, items, current=0, *a, **k):
        f = QComboBox(); f.addItems([str(i) for i in items]); f._cur = current if items else -1; ok = QInputDialog._form(title, label, f)
        return f.currentText(), ok

class _FileAsk(QDialog):
    """A file the command asks for: the app opens Android's picker (open) or 'save as' (the engine writes a cache file
    the app then copies to the chosen document)."""
    def __init__(self, mode, caption='', path='', filt=''):
        super().__init__(); self._title = str(caption or ('Save file' if mode == 'save' else 'Open file'))
        import os
        self._file = {'mode': mode, 'filter': str(filt or ''), 'name': os.path.basename(str(path or ''))}; self._value = ''
    def _answer(self, which): return 1 if which == 'ok' and self._value else 0

def _ask_file(mode, a, k):
    caption = k.get('caption', a[1] if len(a) > 1 else ''); path = k.get('dir', a[2] if len(a) > 2 else ''); filt = k.get('filter', a[3] if len(a) > 3 else '')
    d = _FileAsk(mode, caption, path, filt)
    r = _modal(d)
    return d._value if r else ''

class QFileDialog(QDialog):
    @staticmethod
    def getOpenFileName(*a, **k): return _ask_file('open', a, k), ''
    @staticmethod
    def getSaveFileName(*a, **k): return _ask_file('save', a, k), ''
    @staticmethod
    def getExistingDirectory(*a, **k):
        import os, tempfile
        d = os.path.join(tempfile.gettempdir(), 'fc_dir'); os.makedirs(d, exist_ok=True); return d
    @staticmethod
    def getOpenFileNames(*a, **k):
        f = _ask_file('open', a, k); return ([f] if f else []), ''

class QColorDialog(QDialog):
    @staticmethod
    def getColor(initial=None, *a, **k): return initial

class QMenu(QWidget):
    triggered = Signal(object); aboutToShow = Signal()
    def __init__(self, *a, **k): super().__init__(next((x for x in a if isinstance(x, QWidget)), None)); self._acts = []
    def addAction(self, *a, **k):
        act = a[0] if a and isinstance(a[0], QAction) else QAction(*[x for x in a if isinstance(x, str)][:1])
        cb = next((x for x in a if callable(x) and not isinstance(x, QObject)), None)
        if cb is not None: act.triggered.connect(cb)
        self._acts.append(act); return act
    def addActions(self, acts):
        for x in acts: self.addAction(x)
    def addMenu(self, *a): m = QMenu(); self._acts.append(m); return m
    def addSeparator(self): pass
    def exec(self, *a): return None
    exec_ = exec
    def popup(self, *a): pass
    def actions(self): return [x for x in self._acts if isinstance(x, QAction)]
    def clear(self): self._acts = []

class QAction(QObject):
    """A toolbar / menu action; the app shows the actions a panel keeps as buttons (mobilecad.taskpanel)."""
    triggered = Signal(bool); toggled = Signal(bool)
    def __init__(self, *a, **k):
        super().__init__(next((x for x in a if isinstance(x, QObject)), None)); self._text = next((x for x in a if isinstance(x, str)), '')
        self._checked = False; self._checkable = False; self._enabled = True; self._visible = True; self._tooltip = ''
    def setText(self, t): self._text = str(t)
    def text(self): return self._text
    def setCheckable(self, c): self._checkable = bool(c)
    def isCheckable(self): return self._checkable
    def setChecked(self, c):
        if bool(c) != self._checked: self._checked = bool(c); self.toggled.emit(self._checked)
    def isChecked(self): return self._checked
    def setEnabled(self, e): self._enabled = bool(e)
    def isEnabled(self): return self._enabled
    def setVisible(self, v): self._visible = bool(v)
    def isVisible(self): return self._visible
    def setToolTip(self, t): self._tooltip = str(t)
    def trigger(self):
        if self._checkable: self.setChecked(not self._checked)
        self.triggered.emit(self._checked)
    def _user(self, v=None): self.trigger()
    def __getattr__(self, n): return _fallback(n)

class QApplication(QObject):
    translate = staticmethod(lambda context, text, *a, **k: text)
    @staticmethod
    def instance(): return None
    @staticmethod
    def processEvents(*a): pass
    @staticmethod
    def setOverrideCursor(*a): pass
    @staticmethod
    def restoreOverrideCursor(*a): pass
    @staticmethod
    def clipboard(): return _Clip()
    @staticmethod
    def focusWidget(): return None
    @staticmethod
    def keyboardModifiers(): return _NoMods()
    @staticmethod
    def queryKeyboardModifiers(): return _NoMods()
    @staticmethod
    def topLevelWidgets(): return []
    @staticmethod
    def font(*a): return QFont()
    @staticmethod
    def palette(*a): return _Stub()
    @staticmethod
    def activeWindow(): return _main_window()
    @staticmethod
    def activeModalWidget(): return None
    @staticmethod
    def desktop(): return _Stub()
    @staticmethod
    def primaryScreen(): return _Stub()
    @staticmethod
    def style(): return _Stub()
    @staticmethod
    def doubleClickInterval(): return 400
class _NoMods(int):
    """Qt.KeyboardModifiers with nothing pressed (touch has no Shift / Ctrl)."""
    def __new__(cls): return int.__new__(cls, 0)
    value = 0
    def __and__(self, o): return _NoMods()
    __rand__ = __and__

class _Clip:
    _t = ''
    def setText(self, t): _Clip._t = t
    def text(self): return _Clip._t

class QTimer(QObject):
    """Deferred calls run at once (there is no event loop between the app's requests).  A repeating timer gives one
    last tick when it is stopped after at least an interval (elapsed-time labels show the final time)."""
    timeout = Signal()
    def __init__(self, *a, **k): super().__init__(None); self._single = False; self._t0 = None; self._ms = 0
    _running = set()
    @staticmethod
    def singleShot(ms, *a):
        fn = a[-1] if a else None
        key = getattr(fn, '__func__', fn)
        if key in QTimer._running: return          # a periodic self-rescheduling update: once is enough
        QTimer._running.add(key)
        try:
            if callable(fn): fn()
            elif hasattr(fn, 'emit'): fn.emit()
        finally:
            QTimer._running.discard(key)
    def setSingleShot(self, s): self._single = bool(s)
    def start(self, *a):
        if a and isinstance(a[0], (int, float)): self._ms = a[0]
        if self._single: self.timeout.emit(); return
        import time; self._t0 = time.monotonic()
    def stop(self):
        import time
        t0, self._t0 = self._t0, None
        if t0 is not None and (time.monotonic() - t0) * 1000 >= self._ms: self.timeout.emit()
    def isActive(self): return self._t0 is not None
    def setInterval(self, ms, *a): self._ms = ms
    def interval(self): return self._ms

class QButtonGroup(QObject):
    buttonClicked = Signal(object); idClicked = Signal(int); buttonToggled = Signal(object, bool)
    def __init__(self, *a, **k): super().__init__(None); self._b = []
    def addButton(self, b, i=-1):
        self._b.append((b, i if i >= 0 else len(self._b)))
        b.clicked.connect(lambda *_: (self.buttonClicked.emit(b), self.idClicked.emit(self.id(b))))
    def buttons(self): return [b for b, _ in self._b]
    def id(self, b): return next((i for x, i in self._b if x is b), -1)
    def button(self, i): return next((x for x, j in self._b if j == i), None)
    def checkedButton(self): return next((b for b, _ in self._b if b.isChecked()), None)
    def checkedId(self): return self.id(self.checkedButton()) if self.checkedButton() else -1
    def setExclusive(self, *a): pass

class QMainWindow(QWidget):
    def findChild(self, cls=None, name=''):
        # the desktop main window always has its MDI area; other widgets exist only once something added them
        # menu actions (Std_Save ... looked up by NativeIFC's lock toggle) exist on the desktop's main window too
        if cls is QAction or (isinstance(cls, type) and issubclass(cls, QAction)):
            acts = self.__dict__.setdefault('_named_actions', {})
            if name not in acts: acts[name] = QAction(name); acts[name].setObjectName(name)
            return acts[name]
        found = super().findChild(cls, name)
        if found is None and name in ('Model', 'Report view', 'Python console', 'Combo View', 'Property view', 'Selection view'):
            # the desktop's standard docks (tree, report ...): present but empty
            found = QDockWidget(self); found.setObjectName(name); found._visible = False
            inner = QWidget(found); inner.setObjectName(name)
        return found or (QMdiArea() if cls is QMdiArea else None)
    def statusBar(self):
        """Status bar with BIM's status widget (BimStatus.py): its nudge button holds the BIM_Nudge_* step."""
        if not hasattr(self, '_status'):
            self._status = QWidget(); tb = QToolBar(self._status); tb.setObjectName('BIMStatusWidget')
            tb.nudge = QPushButton('10 mm', tb); tb.nudge.setObjectName('nudgeButton')
        return self._status
    def addDockWidget(self, *a):
        dock = next((x for x in a if isinstance(x, QWidget)), None)
        if dock is not None:
            dock.setParent(self); dock._visible = True
            try:
                import FreeCADGui
                FreeCADGui.requests.append(('widget', dock))
            except Exception:
                pass
    def tabifyDockWidget(self, *a): pass
    def removeDockWidget(self, *a): pass

class QDesktopServices:
    @staticmethod
    def openUrl(url):
        try:
            import FreeCADGui
            FreeCADGui.requests.append(('url', url.toString() if hasattr(url, 'toString') else str(url)))
        except Exception:
            pass
        return True

class QUrl(str):
    def __new__(cls, s='', *a): return str.__new__(cls, str(s))
    def toString(self, *a): return str(self)
    def toLocalFile(self): return str(self)[7:] if str(self).startswith('file://') else str(self)
    def isValid(self): return bool(str(self))
    @staticmethod
    def fromLocalFile(p): return QUrl('file://' + str(p))
    def scheme(self): return str(self).split(':')[0]

class QToolBar(QWidget):
    actionTriggered = Signal(object)
    def addWidget(self, w): w.setParent(self); return QAction()
    def addSeparator(self): pass

class QStatusBar(QWidget):
    def showMessage(self, *a): pass
    def clearMessage(self): pass
    def addPermanentWidget(self, w, *a): w.setParent(self)
    def addWidget(self, w, *a): w.setParent(self)

class QMdiArea(QWidget):
    def subWindowList(self, *a): return []
    def activeSubWindow(self): return None
    def addSubWindow(self, w, *a): return w

_mw = []
def _main_window():
    if not _mw: _mw.append(QMainWindow())
    return _mw[0]
class QDockWidget(QWidget):
    def setWidget(self, w): self._w = w
    def widget(self): return getattr(self, '_w', None)


# Qt API reached through super() (bypasses __getattr__): accepted and ignored
for _n in ['setDragEnabled', 'setAcceptDrops', 'setDragDropMode', 'setDropIndicatorShown', 'setDefaultDropAction', 'setSelectionMode', 'setSelectionBehavior', 'setAlternatingRowColors', 'setSortingEnabled', 'setMinimumWidth', 'setMinimumHeight', 'setMaximumWidth', 'setMaximumHeight', 'setFixedWidth', 'setFixedHeight', 'setFixedSize', 'setMinimumSize', 'setMaximumSize', 'resize', 'move', 'setSizePolicy', 'setStyleSheet', 'setFocus', 'setFocusPolicy', 'setContextMenuPolicy', 'setUpdatesEnabled', 'update', 'repaint', 'adjustSize', 'setFont', 'setPalette', 'setCursor', 'unsetCursor', 'setMouseTracking', 'setAttribute', 'setWindowFlags', 'setWindowModality', 'setWindowIcon', 'setIconSize', 'setHeaderHidden', 'setRootIsDecorated', 'setUniformRowHeights', 'setItemDelegate', 'setViewMode', 'setWordWrap', 'setTextFormat', 'setOpenExternalLinks', 'setTextInteractionFlags', 'setFrameShape', 'setFrameShadow', 'setLineWrapMode', 'raise_', 'activateWindow', 'mousePressEvent', 'mouseReleaseEvent', 'mouseMoveEvent', 'mouseDoubleClickEvent', 'keyPressEvent', 'keyReleaseEvent', 'paintEvent', 'resizeEvent', 'showEvent', 'hideEvent', 'closeEvent', 'dragEnterEvent', 'dragMoveEvent', 'dropEvent', 'startDrag', 'contextMenuEvent', 'focusInEvent', 'focusOutEvent', 'wheelEvent', 'changeEvent', 'setColumnWidth', 'setEditTriggers', 'setShowGrid', 'resizeColumnsToContents', 'resizeRowsToContents', 'setDefault', 'setAutoDefault', 'setFlat', 'setAutoRaise', 'setToolButtonStyle', 'setPopupMode', 'setShortcut', 'setStatusTip', 'setWhatsThis', 'setAccessibleName', 'setGeometry', 'setContentsMargins', 'setIndentation', 'setItemsExpandable', 'setExpandsOnDoubleClick', 'setAutoExclusive', 'setTabOrder', 'setBuddy', 'setScaledContents', 'setMargin', 'setIndent', 'setInsertPolicy', 'setMaxVisibleItems', 'setSizeAdjustPolicy', 'setDuplicatesEnabled', 'setFrame', 'setClearButtonEnabled', 'setValidator', 'setMaxLength', 'setEchoMode', 'setInputMask', 'setCompleter', 'setAlignment', 'setButtonSymbols', 'setAccelerated', 'setCorrectionMode', 'setWrapping', 'setSpecialValueText', 'setTracking', 'setTickPosition', 'setTickInterval', 'setOrientation', 'setInvertedAppearance', 'setTextVisible', 'setFormat', 'setUsesScrollButtons', 'setTabsClosable', 'setMovable', 'setDocumentMode', 'setTabPosition', 'setElideMode', 'setVerticalScrollBarPolicy', 'setHorizontalScrollBarPolicy', 'setVerticalScrollMode', 'setHorizontalScrollMode', 'setResizeMode', 'setGridSize', 'setUniformItemSizes', 'setBatchSize', 'setLayoutMode', 'setMovement', 'setFlow', 'setTabKeyNavigation', 'setTextElideMode', 'setAutoScroll', 'setWordWrapMode', 'setAcceptRichText', 'setTabStopDistance', 'setUndoRedoEnabled', 'setOverwriteMode', 'setCursorWidth', 'setCenterButtons', 'setSizeGripEnabled', 'setModal', 'setOption', 'setTabChangesFocus']:
    if not hasattr(QWidget, _n): setattr(QWidget, _n, lambda self, *a, **k: None)

class QColor:
    """QColor with the accessors FreeCAD's colour buttons / preferences use."""
    def __init__(self, *a):
        self._c = [0, 0, 0, 255]
        if len(a) == 1 and isinstance(a[0], QColor): self._c = list(a[0]._c)
        elif len(a) == 1 and isinstance(a[0], str) and a[0].startswith('#'):
            h = a[0][1:]; self._c = [int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255]
        elif len(a) == 1 and isinstance(a[0], int): v = a[0]; self._c = [(v >> 16) & 255, (v >> 8) & 255, v & 255, 255]
        elif len(a) >= 3: self._c = [int(x) for x in a[:4]] + ([255] if len(a) == 3 else [])
    @staticmethod
    def fromRgbF(r, g, b, a=1.0): return QColor(int(r * 255), int(g * 255), int(b * 255), int(a * 255))
    @staticmethod
    def fromRgb(*a): return QColor(*a)
    def red(self): return self._c[0]
    def green(self): return self._c[1]
    def blue(self): return self._c[2]
    def alpha(self): return self._c[3]
    def redF(self): return self._c[0] / 255.0
    def greenF(self): return self._c[1] / 255.0
    def blueF(self): return self._c[2] / 255.0
    def alphaF(self): return self._c[3] / 255.0
    def rgb(self): return (0xff << 24) | (self._c[0] << 16) | (self._c[1] << 8) | self._c[2]
    def rgba(self): return (self._c[3] << 24) | (self._c[0] << 16) | (self._c[1] << 8) | self._c[2]
    def getRgb(self): return tuple(self._c)
    def getRgbF(self): return tuple(x / 255.0 for x in self._c)
    def name(self, *a): return '#%02x%02x%02x' % tuple(self._c[:3])
    def isValid(self): return True
    def setRgbF(self, r, g, b, a=1.0): self._c = [int(r * 255), int(g * 255), int(b * 255), int(a * 255)]
    def setRgb(self, r, g, b, a=255): self._c = [r, g, b, a]
    def __getattr__(self, n): return _fallback(n)

class ColorButton(QPushButton):
    """Gui::ColorButton / PrefColorButton: property('color') is a QColor."""
    changed = Signal()
    def __init__(self, *a, **k): super().__init__(*a, **k); self._color = QColor(0, 0, 0)
    def color(self): return self._color
    def setColor(self, c): self._color = QColor(c) if not isinstance(c, QColor) else c; self.changed.emit()
    def property(self, name):
        if name == 'color': return self._color
        return super().property(name)
    def setProperty(self, name, v):
        if name == 'color': self.setColor(v); return True
        return super().setProperty(name, v)

# Gui:: classes produced by FreeCADGui.UiLoader / .ui files
GUI_CLASSES = {
    'Gui::QuantitySpinBox': QuantitySpinBox, 'Gui::InputField': QuantitySpinBox, 'Gui::PrefQuantitySpinBox': QuantitySpinBox,
    'Gui::IntSpinBox': QSpinBox, 'Gui::UIntSpinBox': QSpinBox, 'Gui::PrefSpinBox': QSpinBox, 'Gui::DoubleSpinBox': QDoubleSpinBox,
    'Gui::PrefDoubleSpinBox': QDoubleSpinBox, 'Gui::PrefCheckBox': QCheckBox, 'Gui::PrefRadioButton': QRadioButton,
    'Gui::PrefComboBox': QComboBox, 'Gui::PrefUnitSpinBox': QuantitySpinBox, 'Gui::PrefLineEdit': QLineEdit,
    'Gui::PrefFileChooser': QLineEdit, 'Gui::FileChooser': QLineEdit, 'Gui::ColorButton': ColorButton, 'Gui::PrefColorButton': ColorButton,
    'Gui::PrefSlider': QSlider, 'Gui::PrefFontBox': QFontComboBox, 'Gui::ExpressionLineEdit': QLineEdit, 'Gui::AccelLineEdit': QLineEdit,
    'Gui::ActionSelector': QWidget, 'Gui::UrlLabel': QLabel, 'Gui::StatefulLabel': QLabel,
}

"""Qt Designer (.ui) loader for the widget model in PySide/_widgets.py: FreeCADGui.PySideUic.loadUi and
FreeCADGui.UiLoader.  Builds the widget tree with its properties (texts, values, ranges, units, combo items, tab titles)
and, like PySide's QUiLoader, makes every named child an attribute of the top-level widget (form.lineEdit ...)."""
import os
import xml.etree.ElementTree as ET
from PySide import _widgets as W

_QTRES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'qtres')

def _resolve(path):
    path = str(path)
    if path.startswith(':'):
        return os.path.join(_QTRES, path.lstrip(':').lstrip('/'))
    return path

def _class(name, custom):
    if name in W.GUI_CLASSES: return W.GUI_CLASSES[name]
    short = name.split('::')[-1]
    from PySide import _models as M
    c = getattr(W, short, None) or getattr(M, short, None)
    if isinstance(c, type) and issubclass(c, W.QObject): return c
    if name in custom: return _class(custom[name], {})
    if short.endswith(('SpinBox', 'InputField')): return W.QuantitySpinBox if 'Quantity' in short or 'Input' in short or 'Unit' in short else W.QDoubleSpinBox
    return W.QWidget

def _value(p):
    """<property><string>/<bool>/<number>/<double>/<enum>/<set>/<cstring> ...</property> -> Python value."""
    for v in p:
        t = v.tag
        if t in ('string', 'cstring', 'enum', 'set'): return v.text or ''
        if t == 'bool': return (v.text or '').strip() == 'true'
        if t == 'number': return int(float(v.text or 0))
        if t in ('double', 'float'): return float(v.text or 0)
        return None
    return None

def _apply(w, name, val):
    if val is None: return
    try:
        if name == 'text' and hasattr(w, 'setText'):
            if isinstance(w, W.QTextEdit): w._text = str(val)
            elif isinstance(w, W.QLineEdit): w._text = str(val)
            else: w.setText(val)
        elif name == 'title' and hasattr(w, 'setTitle'): w.setTitle(val)
        elif name == 'windowTitle': w.setWindowTitle(val)
        elif name == 'toolTip': w.setToolTip(val)
        elif name in ('plainText', 'html') and isinstance(w, W.QTextEdit): w._text = str(val)
        elif name == 'checkable' and hasattr(w, 'setCheckable'): w.setCheckable(val)
        elif name == 'checked' and hasattr(w, 'setChecked'):
            w._checked = bool(val)
        elif name == 'enabled': w.setEnabled(val)
        elif name == 'visible': w.setVisible(val)
        elif name == 'readOnly' and isinstance(w, W.QLineEdit): w.setReadOnly(val)
        elif name == 'placeholderText' and isinstance(w, W.QLineEdit): w.setPlaceholderText(val)
        elif isinstance(w, W.QAbstractSlider):
            if name == 'minimum': w._min = int(val)
            elif name == 'maximum': w._max = int(val)
            elif name in ('value', 'sliderPosition'): w._v = int(val)
            else: w._props[name] = val
        elif isinstance(w, W.QAbstractSpinBox):
            if name == 'minimum': w._min = val
            elif name == 'maximum': w._max = val
            elif name == 'singleStep': w._step = val
            elif name == 'decimals' and hasattr(w, '_dec'): w._dec = int(val)
            elif name == 'suffix' and hasattr(w, '_suffix'): w._suffix = str(val)
            elif name == 'prefix' and hasattr(w, '_prefix'): w._prefix = str(val)
            elif name == 'unit' and isinstance(w, W.QuantitySpinBox): w.setUnit(val)
            elif name in ('value', 'rawValue', 'quantity'):
                w._v = w._coerce(val) if not isinstance(w, W.QuantitySpinBox) else float(val) if not isinstance(val, str) else w._coerce(val)
        elif name == 'currentIndex' and hasattr(w, '_cur'):
            w._cur = int(val)
        else:
            w._props[name] = val
    except Exception:
        pass

class _Loader:
    def __init__(self):
        self.custom = {}; self.top = None; self.named = {}; self.buddies = []

    def widget(self, el, parent):
        cls = _class(el.get('class', 'QWidget'), self.custom)
        try: w = cls(parent) if parent is not None else cls()
        except TypeError: w = cls()
        if parent is not None and w._parent is not parent: w.setParent(parent)
        name = el.get('name', '')
        w.setObjectName(name)
        if name: self.named[name] = w
        if self.top is None: self.top = w
        for ch in el:
            if ch.tag == 'property':
                pn = ch.get('name')
                if pn == 'buddy': self.buddies.append((w, _value(ch)))
                elif pn == 'currentIndex': cur = _value(ch); w._pending_cur = cur
                else: _apply(w, pn, _value(ch))
            elif ch.tag == 'attribute':
                w._props['_attr_' + ch.get('name')] = _value(ch)
            elif ch.tag == 'layout':
                lay = self.layout(ch, w); w.setLayout(lay)
            elif ch.tag == 'widget':
                c = self.widget(ch, w)
                if isinstance(w, W.QToolBox):
                    w.addItem(c, c._props.get('_attr_label') or '')
                elif isinstance(w, W.QTabWidget) and not isinstance(w, W.QStackedWidget):
                    w.addTab(c, c._props.get('_attr_title') or '')
                elif isinstance(w, W.QStackedWidget):
                    w.addWidget(c)
                elif isinstance(w, W.QScrollArea):
                    w.setWidget(c)
            elif ch.tag == 'item':                          # QComboBox / QListWidget items
                txt = next((_value(p) for p in ch if p.tag == 'property' and p.get('name') == 'text'), '')
                if isinstance(w, W.QComboBox): w._items.append([str(txt or ''), None])
                elif isinstance(w, W.QListWidget): w.addItem(str(txt or ''))
            elif ch.tag == 'column' and isinstance(w, (W.QTreeWidget, W.QTableWidget)):
                txt = next((_value(p) for p in ch if p.tag == 'property' and p.get('name') == 'text'), '')
                w._props.setdefault('_columns', []).append(str(txt or ''))
        if isinstance(w, W.QComboBox):
            w._cur = getattr(w, '_pending_cur', 0 if w._items else -1) if w._items else -1
        elif hasattr(w, '_pending_cur') and hasattr(w, '_cur'):
            w._cur = w._pending_cur
        return w

    def layout(self, el, owner):
        cls = getattr(W, el.get('class', 'QVBoxLayout'), W.QVBoxLayout)
        lay = cls(); lay._owner = owner
        if el.get('name'): lay.setObjectName(el.get('name'))
        for it in el:
            if it.tag != 'item': continue
            row, col = int(it.get('row', -1)), int(it.get('column', -1))
            for ch in it:
                if ch.tag == 'widget':
                    w = self.widget(ch, owner)
                    lay._items.append(('w', w, (row, col) if row >= 0 else ()))
                elif ch.tag == 'layout':
                    lay._items.append(('l', self.layout(ch, owner), (row, col) if row >= 0 else ()))
        return lay

    def load(self, path, parent=None):
        root = ET.parse(_resolve(path)).getroot()
        for cw in root.iter('customwidget'):
            c = cw.findtext('class'); e = cw.findtext('extends')
            if c: self.custom[c] = e or 'QWidget'
        top = root.find('widget')
        w = self.widget(top, parent)
        for name, child in self.named.items():
            if child is not w: w.__dict__.setdefault(name, child)
        for lab, buddy in self.buddies:
            if buddy in self.named: lab._props['_buddy'] = self.named[buddy]
        return w

class _PySideUic:
    def loadUi(self, path, parent=None, *a, **k):
        return _Loader().load(path, parent)
    def loadUiType(self, path):
        top = ET.parse(_resolve(path)).getroot().find('widget')
        base = _class(top.get('class', 'QWidget'), {})
        class Form:
            def setupUi(self, widget):
                f = _Loader().load(path)
                for k, v in f.__dict__.items():
                    if isinstance(v, W.QObject) and not k.startswith('_'): setattr(self, k, v)
                widget._layout = f._layout
                for c in f._children: c._parent = widget; widget._children.append(c)
            def retranslateUi(self, *a): pass
        return Form, base

class UiLoader:
    def __init__(self, *a, **k): pass
    def createWidget(self, cls, parent=None, name=''):
        w = _class(cls, {})(); w.setObjectName(name)
        if parent is not None: w.setParent(parent)
        return w
    def load(self, path, parent=None):
        return _Loader().load(getattr(path, '_path', path), parent)
    def setWorkingDirectory(self, *a): pass
    def availableWidgets(self): return list(W.GUI_CLASSES)
    def __getattr__(self, n):
        if n.startswith('_'): raise AttributeError(n)
        return lambda *a, **k: None

PySideUic = _PySideUic()

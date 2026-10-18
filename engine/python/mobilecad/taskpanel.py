"""Task panels and dialogs of FreeCAD's Python commands on mobile.

A command's Gui.Control.showDialog(panel) (or a non-modal QDialog.show()) gives us a widget tree built by the PySide
emulation (shims/PySide/_widgets.py, often from the module's .ui file).  `serialize` turns it into a flat, ordered form
the app renders (sections, labelled inputs, buttons, lists); the user's edits come back through `panel_event` and fire
the widgets' own signals, so the panel's logic runs unchanged.  OK / Cancel / Apply call the panel's accept() / reject()
/ clicked(), as the desktop task view does."""
import FreeCAD as App
from .bridge import op, _doc

_panels = {}          # panel id -> {'panel', 'kind', 'widgets': {id: widget}}
_next = [1]

def _W():
    from PySide import _widgets as W
    return W

def _forms(panel):
    W = _W()
    if isinstance(panel, W.QWidget): f = panel.__dict__.get('form', panel)
    else: f = getattr(panel, 'form', None)
    if isinstance(f, (list, tuple)): return [x for x in f if x is not None]
    return [f] if f is not None else []

def _label_text(w):
    W = _W()
    return w.text() if isinstance(w, W.QLabel) else ''

def _strip(t):
    import re
    return re.sub(r'<[^>]+>', '', str(t or '')).replace('&amp;', '&').replace('&nbsp;', ' ').strip()

def _node(w, reg):
    """One widget -> its JSON node (containers carry children)."""
    W = _W()
    if not w._visible: return None
    reg[w._id] = w
    n = {'id': w._id, 'name': w._name, 'enabled': w._enabled}
    if w._tooltip: n['tip'] = _strip(w._tooltip)
    if isinstance(w, W.QuantitySpinBox):
        v, u = w._display()
        n.update(t='qty', value=v, unit=u, min=w._min, max=w._max)
    elif isinstance(w, W.QDoubleSpinBox):
        n.update(t='float', value=w._v, min=w._min, max=w._max, dec=w._dec, suffix=w._suffix)
    elif isinstance(w, W.QSpinBox):
        n.update(t='int', value=w._v, min=w._min, max=w._max, suffix=w._suffix)
    elif isinstance(w, W.QAbstractSlider):
        n.update(t='int', value=w._v, min=w._min, max=w._max)
    elif isinstance(w, W.QCheckBox):
        n.update(t='check', text=_strip(w._text), value=w._checked)
    elif isinstance(w, W.QRadioButton):
        n.update(t='radio', text=_strip(w._text), value=w._checked)
    elif isinstance(w, W.QAbstractButton):
        n.update(t='button', text=_strip(w._text) or w._name, checkable=w._checkable, value=w._checked)
    elif isinstance(w, W.QComboBox):
        n.update(t='combo', items=[i[0] for i in w._items], value=w._cur)
    elif isinstance(w, W.QLineEdit):
        n.update(t='line', value=w._text, ro=w._ro)
    elif isinstance(w, W.QTextEdit):
        n.update(t='text', value=w._text)
    elif isinstance(w, W.QTreeWidget):
        rows = []
        def walk(items, depth):
            for it in items:
                rows.append({'text': ' ' * (2 * depth) + ' | '.join(t for t in it._texts if t), 'sel': it._selected,
                             'check': it._check}); walk(it._kids, depth + 1)
        walk(w._items, 0)
        n.update(t='list', items=rows, value=w._cur)
    elif isinstance(w, W.QListWidget):
        n.update(t='list', items=[{'text': i._text, 'sel': i._selected, 'check': i._check} for i in w._items], value=w._cur)
    elif _is_view(w):
        n.update(_grid(w))
    elif isinstance(w, W.QTableWidget):
        cols = w._hh or w._props.get('_columns') or [''] * w._c
        cells = [[(lambda c: c.text() if isinstance(c, W.QListWidgetItem) else '')(w._cells.get((r, c))) for c in range(w._c)] for r in range(w._r)]
        n.update(t='table', cols=list(cols), cells=cells)
    elif isinstance(w, W.QLabel):
        t = _strip(w._text)
        if not t: return None
        n.update(t='label', text=t)
    elif isinstance(w, W.QDialogButtonBox):
        n.update(t='buttonbox')
    elif isinstance(w, W.QTabWidget):
        if isinstance(w, W.QStackedWidget):
            c = w.currentWidget()
            n.update(t='group', title='', kids=_kids_of(c, reg) if c is not None else [])
        else:
            n.update(t='tabs', value=w._cur, tabs=[{'title': _strip(t or w.tabToolTip(i)), 'kids': _kids_of(c, reg)} for i, (c, t) in enumerate(w._tabs) if c._visible])
    elif isinstance(w, W.QScrollArea):
        c = w.widget()
        n.update(t='group', title='', kids=_kids_of(c, reg) if c is not None else [])
    elif isinstance(w, W.QGroupBox):
        n.update(t='group', title=_strip(w._title), checkable=w._checkable, value=w._checked, kids=_kids_of(w, reg))
    else:                                           # plain container (QWidget / QFrame)
        kids = _kids_of(w, reg)
        if not kids: return None
        n.update(t='group', title='', kids=kids)
    return n

def _layout_widgets(lay):
    """Layout items in order with their grid row (to pair a label with the field next to it)."""
    out = []
    items = list(lay._items)
    # grid / form layouts: reading order is (row, column), not the order the .ui lists the cells in
    if items and all(len(p) == 2 and all(isinstance(x, int) for x in p) for _, _, p in items):
        items.sort(key=lambda it: (it[2][0], it[2][1]))
    for k, o, pos in items:
        if k == 'w': out.append((o, pos[0] if pos and isinstance(pos[0], int) else None))
        else:
            for w, r in _layout_widgets(o): out.append((w, None))
    return out

def _is_view(w):
    from PySide import _models as M
    return isinstance(w, M.QAbstractItemView)

def _grid(v):
    """A QTreeView / QTableView / QListView over a QStandardItemModel: header, rows of cells (text, check box,
    editable), selected rows."""
    m = v._model
    from PySide import _models as M
    if isinstance(m, M.QSortFilterProxyModel) and m.sourceModel() is not None: m = m.sourceModel()
    if not isinstance(m, M.QStandardItemModel): return {'t': 'grid', 'cols': [], 'rows': []}
    if m is None: return {'t': 'grid', 'cols': [], 'rows': []}
    ncol = max(1, m.columnCount()); rows = []
    def cell(it):
        if it is None: return {'text': ''}
        d = {'text': _strip(it.text())}
        if it._checkable or it._check is not None and it._check != 0: d['check'] = it._check or 0
        if it._editable and not d.get('check') is not None: d['edit'] = True
        return d
    def walk(items, depth):
        for r, row in enumerate(items):
            cells = [cell(row[c] if c < len(row) else None) for c in range(ncol)]
            if depth: cells[0]['text'] = '  ' * depth + cells[0]['text']
            rows.append({'cells': cells, 'sel': depth == 0 and r in v._sel, 'top': depth == 0})
            if row and row[0]._rows: walk(row[0]._rows, depth + 1)
    walk(m._root._rows, 0)
    return {'t': 'grid', 'cols': [_strip(h) for h in m._hh] + [''] * max(0, ncol - len(m._hh)), 'rows': rows}

def _kids_of(w, reg):
    W = _W()
    if w._layout is not None:
        items = _layout_widgets(w._layout)
    else:
        items = [(c, None) for c in w._children if isinstance(c, W.QWidget) and not isinstance(c, (W.QDialog,))]
    nodes = []; pending = None                       # (label text, row)
    for c, row in items:
        if isinstance(c, W.QLabel) and not isinstance(c, W.QAbstractButton):
            if not c._visible: continue
            if pending: nodes.append({'id': 0, 't': 'label', 'text': pending[0]})
            t = _strip(c._text)
            pending = (t, row) if t else None
            continue
        n = _node(c, reg)
        if n is None: continue
        if pending and n['t'] in ('qty', 'float', 'int', 'combo', 'line', 'text', 'list', 'table', 'grid') and (pending[1] is None or row is None or pending[1] == row):
            n['label'] = pending[0]; pending = None
        elif pending:
            nodes.append({'id': 0, 't': 'label', 'text': pending[0]}); pending = None
        nodes.append(n)
    if pending: nodes.append({'id': 0, 't': 'label', 'text': pending[0]})
    return nodes

def _buttons(panel, kind):
    """Standard buttons of the task dialog (getStandardButtons) or the dialog's QDialogButtonBox."""
    W = _W()
    if kind == 'widget':
        return ['ok', 'cancel']
    if kind == 'modal':
        return panel._button_names() if hasattr(panel, '_button_names') else ['ok', 'cancel']
    b = getattr(panel, 'getStandardButtons', None)
    if b is None: return ['ok', 'cancel']
    try: v = int(b())
    except Exception: return ['ok', 'cancel']
    names = [('ok', W.QDialogButtonBox.Ok), ('apply', W.QDialogButtonBox.Apply), ('close', W.QDialogButtonBox.Close),
             ('cancel', W.QDialogButtonBox.Cancel), ('yes', W.QDialogButtonBox.Yes), ('no', W.QDialogButtonBox.No),
             ('reset', W.QDialogButtonBox.Reset)]
    return [n for n, f in names if v & f] or ['ok', 'cancel']

def _button_labels(panel):
    """Texts the panel gives its standard buttons in modifyStandardButtons(box) (FEM mesher / solver: Apply -> 'Run')."""
    if not hasattr(panel, 'modifyStandardButtons'): return {}
    W = _W(); names = {W.QDialogButtonBox.Ok: 'ok', W.QDialogButtonBox.Apply: 'apply', W.QDialogButtonBox.Cancel: 'cancel',
                       W.QDialogButtonBox.Close: 'close', W.QDialogButtonBox.Reset: 'reset'}
    got = {}
    class _Btn:
        def __init__(self, n): self.n = n
        def setText(self, t): got[self.n] = _strip(t).replace('&', '')
        def __getattr__(self, k): return lambda *a, **kw: None
    class _Box:
        def button(self, which):
            for k, n in names.items():
                try:
                    if int(which) == int(k): return _Btn(n)
                except Exception: pass
            return _Btn(str(which))
        def __getattr__(self, k): return lambda *a, **kw: None
    try: panel.modifyStandardButtons(_Box())
    except Exception: pass
    return got

def serialize(pid):
    e = _panels[pid]; reg = {}
    forms = []
    for f in _forms(e['panel']):
        title = _strip(f._title)
        forms.append({'title': title, 'kids': _kids_of(f, reg) if f._visible else []})
    # QActions the panel keeps (toolbar / menu / context-menu actions: dialog.buttonX, form.toolbar.actions()) -> buttons
    W = _W(); acts = []
    def add(a):
        if isinstance(a, W.QAction) and a._visible and a._text and a not in acts: acts.append(a)
    for f in _forms(e['panel']):
        for w in [f] + f.findChildren(W.QWidget):
            for a in w.actions(): add(a)
            m = getattr(w, '_menu', None)
            if m is not None:
                for a in m.actions(): add(a)
            for v in list(w.__dict__.values()):
                if isinstance(v, W.QMenu):
                    for a in v.actions(): add(a)
                else: add(v)
    for v in list(getattr(e['panel'], '__dict__', {}).values()): add(v)
    if acts:
        kids = []
        for a in acts:
            reg[a._id] = a
            kids.append({'id': a._id, 't': 'button', 'text': _strip(a._text), 'checkable': a._checkable, 'value': a._checked, 'enabled': a._enabled,
                         'tip': _strip(a._tooltip)})
        forms.append({'title': 'Actions', 'kids': [{'id': 0, 't': 'group', 'title': 'Actions', 'kids': kids}]})
    e['widgets'] = reg
    title = next((f['title'] for f in forms if f['title']), '') or type(e['panel']).__name__
    out = {'id': pid, 'title': title, 'forms': forms, 'buttons': _buttons(e['panel'], e['kind'])}
    labels = _button_labels(e['panel'])
    if labels: out['labels'] = labels
    if getattr(e['panel'], '_file', None): out['file'] = e['panel']._file
    return out

def register(panel, kind='dialog'):
    for pid, e in _panels.items():
        if e['panel'] is panel: return pid
    pid = _next[0]; _next[0] += 1
    _panels[pid] = {'panel': panel, 'kind': kind, 'widgets': {}}
    return pid

def _drop(panel):
    for pid in [p for p, e in _panels.items() if e['panel'] is panel]: del _panels[pid]

def e_kind(pid):
    return _panels.get(pid, {}).get('kind')

def _run(pid, fn, sel=None):
    """Run a panel action, then report the panel's new state and what the action created / requested."""
    import FreeCADGui as G
    from . import guicmd
    if sel is not None: guicmd._set_selection(sel)
    before = {o.Name for o in _doc().Objects}
    G.requests.clear()
    err = None
    try:
        if e_kind(pid) == 'modal': fn()          # edits inside a waiting dialog run on this thread
        else: guicmd.coop(fn)
    except Exception as ex:
        import traceback; traceback.print_exc()
        err = str(ex)
    try: _doc().recompute()
    except Exception: pass
    out = guicmd._outcome(before)
    if err: out['error'] = err
    if pid in _panels:
        e = _panels[pid]
        if e['kind'] in ('widget', 'modal') and not e['panel']._visible: del _panels[pid]
    if not out.get('panel'):
        out['panel'] = serialize(pid) if pid in _panels else None
    return out

@op
def panel_state(pid):
    return serialize(pid) if pid in _panels else None

@op
def panel_event(pid, wid, kind='set', value=None, sel=None):
    """The user edited widget `wid`: 'set' (value / text / index / checked / click), 'check' (list row, on), 'cell'."""
    e = _panels.get(pid)
    if e is None: raise RuntimeError('The dialog is closed')
    w = e['widgets'].get(wid)
    if w is None: raise RuntimeError('Widget gone')
    W = _W()
    def go():
        if kind == 'check': w._check(*value)
        elif kind == 'cell': w._user_cell(value[0], value[1], value[2])
        elif kind == 'tab': w.setCurrentIndex(int(value))
        elif isinstance(w, W.QListWidget) and isinstance(value, list):
            for i, it in enumerate(w._items): it._selected = i in value
            if value: w.setCurrentRow(value[-1])
            w.itemSelectionChanged.emit()
        else: w._user(value)
    return _run(pid, go, sel)

@op
def panel_selection(pid, sel):
    """The selection changed while the panel is open: Gui.Selection observers (panels that pick references)."""
    import FreeCADGui as G
    d = _doc(); items = []
    for n, subs in (sel or []):
        for s in (subs or ['']): items.append((d.Name, n, s, None))
    return _run(pid, lambda: G.Selection._notify(items))

@op
def panel_button(pid, which, sel=None, value=None):
    """OK / Cancel / Apply ... of the task dialog."""
    import FreeCADGui as G
    e = _panels.get(pid)
    if e is None: return {'panel': None}
    p = e['panel']; W = _W()
    if e['kind'] == 'modal':                       # QDialog.exec() / QMessageBox / QInputDialog waiting on a worker
        from . import guicmd
        if value is not None: p._value = value
        def go():
            code = p._answer(which) if hasattr(p, '_answer') else (1 if which in ('ok', 'yes') else 0)
            if hasattr(p, '_answer'): pass
            elif code: p.accept()
            else: p.reject()
            p._visible = False; _drop(p)
            guicmd.resume(p, code)
        go._resume = True
        pid_ = pid
        out = _run(pid_, go, sel)
        return out
    def go():
        if e['kind'] == 'widget':
            own = _own_button(p, which)
            if own is not None:                           # the dialog's own OK / Cancel / Close push button
                own.click(); return
            bb = next((c for c in p.findChildren(W.QDialogButtonBox)), None)
            if which in ('ok', 'yes'):
                if bb is not None and bb.accepted.slots: bb.accepted.emit()
                else: p.accept()
            else:
                if bb is not None and bb.rejected.slots: bb.rejected.emit()
                else: p.reject()
            p._visible = False
            return
        if which in ('ok', 'yes'):
            r = p.accept() if hasattr(p, 'accept') else True
            if r is not False: _close(p)
        elif which in ('cancel', 'close', 'no'):
            r = p.reject() if hasattr(p, 'reject') else True
            if r is not False: _close(p)
        else:
            flag = {'apply': W.QDialogButtonBox.Apply, 'reset': W.QDialogButtonBox.Reset}.get(which, 0)
            if hasattr(p, 'clicked'): p.clicked(flag)
    out = _run(pid, go, sel)
    return out

def _own_button(p, which):
    W = _W()
    names = {'ok': ('ok', 'apply', 'accept', 'close'), 'yes': ('ok', 'yes'), 'cancel': ('cancel', 'close'), 'close': ('close', 'cancel', 'ok'),
             'no': ('no', 'cancel')}.get(which, (which,))
    btns = [b for b in p.findChildren(W.QAbstractButton) if b._visible and not isinstance(b, (W.QCheckBox, W.QRadioButton))]
    for n in names:
        for b in btns:
            if _strip(b._text).replace('&', '').strip().lower() == n: return b
    return None

def _close(p):
    import FreeCADGui as G
    if G.Control.current is p: G.Control.current = None
    _drop(p)
    try: G.Selection._observers.clear()
    except Exception: pass

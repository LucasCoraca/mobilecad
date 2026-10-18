"""Qt's item models and views for the widget model (PySide/_widgets.py): QStandardItemModel / QStandardItem with
QTreeView / QTableView / QListView, QModelIndex, and the Qt enum namespace the panels compare against (roles, check
states, item flags).  Task panels such as BIM Layers / Classification / Schedule keep their data in these models; the
app shows a view's model as an editable table (mobilecad.taskpanel)."""
from PySide._widgets import QObject, QWidget, Signal, _QtMeta, _fallback


class _E(int):
    """A Qt enum value: an int with .value / .name (Qt6 style) that combines in flag expressions."""
    def __new__(cls, v, name=''):
        o = int.__new__(cls, v); o._name = name; return o
    @property
    def value(self): return int(self)
    @property
    def name(self): return self._name
    def __or__(self, o): return _E(int(self) | int(o))
    __ror__ = __or__
    def __and__(self, o): return _E(int(self) & int(o))
    __rand__ = __and__
    def __invert__(self): return _E(~int(self))


def _enum(name, **vals):
    return _QtMeta(name, (), {k: _E(v, k) for k, v in vals.items()})


_ROLES = dict(DisplayRole=0, DecorationRole=1, EditRole=2, ToolTipRole=3, StatusTipRole=4, WhatsThisRole=5, FontRole=6,
              TextAlignmentRole=7, BackgroundRole=8, ForegroundRole=9, CheckStateRole=10, SizeHintRole=13, UserRole=256)
_CHECK = dict(Unchecked=0, PartiallyChecked=1, Checked=2)
_FLAGS = dict(NoItemFlags=0, ItemIsSelectable=1, ItemIsEditable=2, ItemIsDragEnabled=4, ItemIsDropEnabled=8,
              ItemIsUserCheckable=16, ItemIsEnabled=32, ItemIsAutoTristate=64, ItemNeverHasChildren=128, ItemIsUserTristate=256)
_ALIGN = dict(AlignLeft=1, AlignRight=2, AlignHCenter=4, AlignJustify=8, AlignTop=32, AlignBottom=64, AlignVCenter=128,
              AlignCenter=132)
_MISC = dict(Horizontal=1, Vertical=2, AscendingOrder=0, DescendingOrder=1, MatchExactly=0, MatchContains=1,
             MatchFixedString=8, MatchRecursive=64, CustomContextMenu=3, NoContextMenu=0, ScrollBarAlwaysOff=1,
             ScrollBarAsNeeded=0, WaitCursor=3, ArrowCursor=0, LeftButton=1, RightButton=2, NoModifier=0,
             ShiftModifier=0x02000000, ControlModifier=0x04000000, AltModifier=0x08000000, Key_Return=0x01000004,
             Key_Enter=0x01000005, Key_Escape=0x01000000, Key_Delete=0x01000007, WindowStaysOnTopHint=0x00040000,
             Tool=0x0b, Dialog=0x03, Window=0x01, Popup=0x09, FramelessWindowHint=0x0800, WA_DeleteOnClose=55,
             ApplicationModal=2, WindowModal=1, NonModal=0, ElideRight=1, ElideLeft=0, ElideMiddle=2, RichText=1,
             PlainText=0, AutoText=2, TextSelectableByMouse=1, TextBrowserInteraction=13, ToolButtonTextBesideIcon=2,
             ToolButtonIconOnly=0, ToolButtonTextUnderIcon=3, transparent=19, white=3, black=2, red=7, green=8, blue=9,
             gray=5, darkGray=4, lightGray=6, yellow=12, NoPen=0, SolidLine=1, DashLine=2, DotLine=3, NoBrush=0,
             SolidPattern=1, KeepAspectRatio=1, IgnoreAspectRatio=0, SmoothTransformation=1, FastTransformation=0,
             UniqueConnection=0x80, QueuedConnection=2, DirectConnection=1, AutoConnection=0, MouseFocus=2,
             StrongFocus=11, NoFocus=0, ClickFocus=2, TabFocus=1, ItemIsEnabled=32)


class Qt(metaclass=_QtMeta):
    """QtCore.Qt: the enum values task panels use (flat Qt5 names and nested Qt6 enum classes)."""
    ItemDataRole = _enum('ItemDataRole', **_ROLES)
    CheckState = _enum('CheckState', **_CHECK)
    ItemFlag = _enum('ItemFlag', **_FLAGS)
    AlignmentFlag = _enum('AlignmentFlag', **_ALIGN)
    Orientation = _enum('Orientation', Horizontal=1, Vertical=2)
    SortOrder = _enum('SortOrder', AscendingOrder=0, DescendingOrder=1)
    MatchFlag = _enum('MatchFlag', MatchExactly=0, MatchContains=1, MatchFixedString=8, MatchRecursive=64)
    ContextMenuPolicy = _enum('ContextMenuPolicy', CustomContextMenu=3, NoContextMenu=0, DefaultContextMenu=1)
    KeyboardModifier = _enum('KeyboardModifier', NoModifier=0, ShiftModifier=0x02000000, ControlModifier=0x04000000, AltModifier=0x08000000)
    MouseButton = _enum('MouseButton', LeftButton=1, RightButton=2, MiddleButton=4)
    WindowType = _enum('WindowType', Tool=0x0b, Dialog=0x03, Window=0x01, Popup=0x09, WindowStaysOnTopHint=0x00040000, FramelessWindowHint=0x0800)
    WindowModality = _enum('WindowModality', ApplicationModal=2, WindowModal=1, NonModal=0)
    TextFormat = _enum('TextFormat', RichText=1, PlainText=0, AutoText=2)
    GlobalColor = _enum('GlobalColor', transparent=19, white=3, black=2, red=7, green=8, blue=9, gray=5, darkGray=4, lightGray=6, yellow=12)
    Key = _enum('Key', Key_Return=0x01000004, Key_Enter=0x01000005, Key_Escape=0x01000000, Key_Delete=0x01000007)
    CursorShape = _enum('CursorShape', WaitCursor=3, ArrowCursor=0)
    WidgetAttribute = _enum('WidgetAttribute', WA_DeleteOnClose=55)
    ConnectionType = _enum('ConnectionType', UniqueConnection=0x80, QueuedConnection=2, DirectConnection=1, AutoConnection=0)
    DockWidgetArea = _enum('DockWidgetArea', LeftDockWidgetArea=1, RightDockWidgetArea=2, TopDockWidgetArea=4, BottomDockWidgetArea=8)
    ToolButtonStyle = _enum('ToolButtonStyle', ToolButtonIconOnly=0, ToolButtonTextOnly=1, ToolButtonTextBesideIcon=2, ToolButtonTextUnderIcon=3)
    TextElideMode = _enum('TextElideMode', ElideLeft=0, ElideRight=1, ElideMiddle=2, ElideNone=3)
    FocusPolicy = _enum('FocusPolicy', NoFocus=0, TabFocus=1, ClickFocus=2, StrongFocus=11)
    ScrollBarPolicy = _enum('ScrollBarPolicy', ScrollBarAsNeeded=0, ScrollBarAlwaysOff=1, ScrollBarAlwaysOn=2)
    AspectRatioMode = _enum('AspectRatioMode', IgnoreAspectRatio=0, KeepAspectRatio=1)
    TransformationMode = _enum('TransformationMode', FastTransformation=0, SmoothTransformation=1)
    PenStyle = _enum('PenStyle', NoPen=0, SolidLine=1, DashLine=2, DotLine=3)
    TextInteractionFlag = _enum('TextInteractionFlag', TextSelectableByMouse=1, TextBrowserInteraction=13)
    LeftDockWidgetArea = _E(1); RightDockWidgetArea = _E(2); TopDockWidgetArea = _E(4); BottomDockWidgetArea = _E(8)
    LeftToolBarArea = _E(1); RightToolBarArea = _E(2); TopToolBarArea = _E(4); BottomToolBarArea = _E(8)
    MiddleButton = _E(4); ItemIsTristate = _E(64)


for _d in (_ROLES, _CHECK, _FLAGS, _ALIGN, _MISC):
    for _k, _v in _d.items(): setattr(Qt, _k, _E(_v, _k))


def _role(r):
    try: return int(getattr(r, 'value', r))
    except Exception: return 0


class QIcon(metaclass=_QtMeta):
    Normal = _E(0); Disabled = _E(1); Active = _E(2); Selected = _E(3); On = _E(0); Off = _E(1)
    class Mode:
        Normal = _E(0); Disabled = _E(1); Active = _E(2); Selected = _E(3)
    class State:
        On = _E(0); Off = _E(1)
    @staticmethod
    def fromTheme(*a): return QIcon(a[0] if a else '')
    @staticmethod
    def hasThemeIcon(*a): return False
    def __init__(self, *a): self._null = not a or not a[0]
    def addFile(self, *a): self._null = False
    def addPixmap(self, *a): self._null = False
    def availableSizes(self, *a): return []
    def isNull(self): return self._null
    def pixmap(self, *a): return self
    def __getattr__(self, n): return _fallback(n)


class QModelIndex:
    def __init__(self, model=None, row=-1, col=-1, parent_item=None):
        self._m = model; self._r = row; self._c = col; self._p = parent_item
    def row(self): return self._r
    def column(self): return self._c
    def isValid(self): return self._m is not None and self._r >= 0
    def model(self): return self._m
    def parent(self): return QModelIndex()
    def data(self, role=0):
        it = self._m.itemFromIndex(self) if self._m is not None else None
        return it.data(role) if it is not None else None
    def sibling(self, r, c): return self._m.index(r, c) if self._m is not None else QModelIndex()
    def siblingAtColumn(self, c): return self.sibling(self._r, c)
    def internalPointer(self): return self._m.itemFromIndex(self) if self._m is not None else None
    def __eq__(self, o): return isinstance(o, QModelIndex) and (o._m, o._r, o._c) == (self._m, self._r, self._c)
    def __hash__(self): return hash((id(self._m), self._r, self._c))


class QStandardItem:
    def __init__(self, *a):
        txt = next((x for x in a if isinstance(x, str)), '')
        self._data = {0: txt}; self._check = None; self._checkable = False; self._editable = True; self._rows = []
        self._parent = None; self._model = None; self._icon = next((x for x in a if isinstance(x, QIcon)), None)
        self._enabled = True; self._flags = None
    # text / data
    def text(self): v = self._data.get(0); return '' if v is None else str(v)
    def setText(self, t): self._data[0] = str(t); self._changed()
    def data(self, role=257):
        r = _role(role); r = 0 if r == 2 else r
        if r == 10: return _E(self._check or 0)
        return self._data.get(r)
    def setData(self, v, role=257):
        r = _role(role); r = 0 if r == 2 else r
        if r == 10: self._check = int(getattr(v, 'value', v) or 0)
        else: self._data[r] = v
        self._changed()
    def toolTip(self): return self._data.get(3) or ''
    def setToolTip(self, t): self._data[3] = str(t)
    def icon(self): return self._icon or QIcon()
    def setIcon(self, i): self._icon = i if isinstance(i, QIcon) else QIcon(i)
    def checkState(self): return _E(self._check or 0)
    def setCheckState(self, s): self._check = int(getattr(s, 'value', s) or 0); self._changed()
    def setCheckable(self, c): self._checkable = bool(c); self._check = self._check or 0
    def isCheckable(self): return self._checkable
    def setEditable(self, e): self._editable = bool(e)
    def isEditable(self): return self._editable
    def setEnabled(self, e): self._enabled = bool(e)
    def isEnabled(self): return self._enabled
    def setFlags(self, f): self._flags = int(f); self._editable = bool(int(f) & 2); self._checkable = bool(int(f) & 16)
    def flags(self): return _E(self._flags if self._flags is not None else (33 | (2 if self._editable else 0) | (16 if self._checkable else 0)))
    def setSelectable(self, *a): pass
    def setForeground(self, *a): pass
    def setBackground(self, *a): pass
    def setFont(self, *a): pass
    def setTextAlignment(self, *a): pass
    def setSizeHint(self, *a): pass
    def setDropEnabled(self, *a): pass
    def setDragEnabled(self, *a): pass
    # tree
    def appendRow(self, items):
        items = list(items) if isinstance(items, (list, tuple)) else [items]
        for it in items: it._parent = self; it._model = self._model
        self._rows.append(items)
    def insertRow(self, r, items):
        items = list(items) if isinstance(items, (list, tuple)) else [items]
        for it in items: it._parent = self; it._model = self._model
        self._rows.insert(r, items)
    def appendRows(self, items):
        for it in items: self.appendRow(it)
    def child(self, r, c=0): return self._rows[r][c] if 0 <= r < len(self._rows) and c < len(self._rows[r]) else None
    def rowCount(self): return len(self._rows)
    def columnCount(self): return max((len(r) for r in self._rows), default=0)
    def hasChildren(self): return bool(self._rows)
    def removeRow(self, r):
        if 0 <= r < len(self._rows): del self._rows[r]
    def takeRow(self, r): return self._rows.pop(r) if 0 <= r < len(self._rows) else []
    def parent(self): return self._parent if isinstance(self._parent, QStandardItem) and not getattr(self._parent, '_root', False) else None
    def row(self):
        p = self._parent
        if p is None: return -1
        return next((i for i, r in enumerate(p._rows) if self in r), -1)
    def column(self):
        p = self._parent
        if p is None: return -1
        return next((r.index(self) for r in p._rows if self in r), -1)
    def index(self): return QModelIndex(self._model, self.row(), self.column(), self._parent)
    def model(self): return self._model
    def clone(self):
        c = QStandardItem(self.text()); c._data = dict(self._data); c._check = self._check; return c
    def _changed(self):
        m = self._model
        if m is not None and not getattr(m, '_blocked', False): m.itemChanged.emit(self)
    def __getattr__(self, n): return _fallback(n)


class QStandardItemModel(QObject):
    itemChanged = Signal(object); dataChanged = Signal(object, object); rowsInserted = Signal(object, int, int)
    rowsRemoved = Signal(object, int, int); modelReset = Signal(); layoutChanged = Signal()
    def __init__(self, *a, **k):
        super().__init__(next((x for x in a if isinstance(x, QObject)), None))
        self._root = QStandardItem(); self._root._root = True; self._root._model = self; self._hh = []
        nums = [x for x in a if isinstance(x, int)]
        self._ncols = nums[1] if len(nums) > 1 else 0
    def invisibleRootItem(self): return self._root
    def rowCount(self, parent=None):
        it = self.itemFromIndex(parent) if isinstance(parent, QModelIndex) and parent.isValid() else self._root
        return it.rowCount() if it else 0
    def columnCount(self, parent=None): return max(self._root.columnCount(), len(self._hh), self._ncols)
    def item(self, r, c=0): return self._root.child(r, c)
    def setItem(self, r, c, it=None):
        if it is None: c, it = 0, c
        while len(self._root._rows) <= r: self._root._rows.append([])
        row = self._root._rows[r]
        while len(row) <= c: row.append(QStandardItem())
        it._parent = self._root; it._model = self; row[c] = it
    def appendRow(self, items):
        self._root.appendRow(items); self._adopt(self._root._rows[-1])
        self.rowsInserted.emit(QModelIndex(), self.rowCount() - 1, self.rowCount() - 1)
    def insertRow(self, r, items=None):
        self._root.insertRow(r, items if items is not None else [QStandardItem()]); self._adopt(self._root._rows[r]); return True
    def _adopt(self, row):
        for it in row:
            it._model = self
            for sub in it._rows: self._adopt(sub)
    def removeRow(self, r, parent=None):
        it = self.itemFromIndex(parent) if isinstance(parent, QModelIndex) and parent.isValid() else self._root
        it.removeRow(r); self.rowsRemoved.emit(QModelIndex(), r, r); return True
    def removeRows(self, r, n, parent=None):
        for _ in range(n): self.removeRow(r, parent)
        return True
    def takeRow(self, r): return self._root.takeRow(r)
    def clear(self): self._root._rows = []; self._hh = []; self.modelReset.emit()
    def setRowCount(self, n):
        while len(self._root._rows) > n: self._root._rows.pop()
        while len(self._root._rows) < n: self._root._rows.append([])
    def setColumnCount(self, n): self._ncols = n
    def setHorizontalHeaderLabels(self, labels): self._hh = [str(x) for x in labels]
    def setHorizontalHeaderItem(self, c, it):
        while len(self._hh) <= c: self._hh.append('')
        self._hh[c] = it.text() if hasattr(it, 'text') else str(it)
    def horizontalHeaderItem(self, c): return QStandardItem(self._hh[c]) if c < len(self._hh) else None
    def setVerticalHeaderLabels(self, *a): pass
    def headerData(self, section, orientation=1, role=0): return self._hh[section] if section < len(self._hh) else None
    def index(self, r, c=0, parent=None):
        p = self.itemFromIndex(parent) if isinstance(parent, QModelIndex) and parent.isValid() else self._root
        return QModelIndex(self, r, c, p)
    def itemFromIndex(self, idx):
        if not isinstance(idx, QModelIndex) or not idx.isValid(): return None
        p = idx._p or self._root
        return p.child(idx._r, idx._c)
    def indexFromItem(self, it): return it.index()
    def data(self, idx, role=0):
        it = self.itemFromIndex(idx); return it.data(role) if it else None
    def setData(self, idx, v, role=2):
        it = self.itemFromIndex(idx)
        if it is None: return False
        it.setData(v, role); return True
    def findItems(self, text, *a, column=0):
        return [r[column] for r in self._root._rows if len(r) > column and r[column].text() == text]
    def sort(self, *a): pass
    def setSortRole(self, *a): pass
    def flags(self, idx):
        it = self.itemFromIndex(idx); return it.flags() if it else _E(0)
    def hasChildren(self, idx=None):
        it = self.itemFromIndex(idx) if idx is not None else self._root; return bool(it and it._rows)


class _SelModel(QObject):
    selectionChanged = Signal(object, object); currentChanged = Signal(object, object); currentRowChanged = Signal(object, object)
    def __init__(self, view): super().__init__(None); self._v = view
    def selectedRows(self, col=0): return [self._v._model.index(r, col) for r in sorted(self._v._sel)] if self._v._model else []
    def selectedIndexes(self):
        m = self._v._model
        return [m.index(r, c) for r in sorted(self._v._sel) for c in range(max(1, m.columnCount()))] if m else []
    def hasSelection(self): return bool(self._v._sel)
    def isSelected(self, idx): return idx.row() in self._v._sel
    def currentIndex(self): return self._v.currentIndex()
    def clearSelection(self): self._v._sel = set()
    def clear(self): self._v._sel = set()
    def select(self, idx, *a):
        if isinstance(idx, QModelIndex) and idx.isValid(): self._v._sel.add(idx.row())
    def setCurrentIndex(self, idx, *a): self._v.setCurrentIndex(idx)


class QAbstractItemView(QWidget):
    clicked = Signal(object); doubleClicked = Signal(object); activated = Signal(object); pressed = Signal(object)
    customContextMenuRequested = Signal(object); entered = Signal(object)
    def __init__(self, *a, **k):
        super().__init__(next((x for x in a if isinstance(x, QObject)), None)); self._model = None; self._sel = set(); self._cur = -1
        self._selmodel = _SelModel(self)
    def setModel(self, m): self._model = m
    def model(self): return self._model
    def selectionModel(self): return self._selmodel
    def currentIndex(self): return self._model.index(self._cur, 0) if self._model is not None and self._cur >= 0 else QModelIndex()
    def setCurrentIndex(self, idx):
        if isinstance(idx, QModelIndex): self._cur = idx.row()
    def selectedIndexes(self): return self._selmodel.selectedIndexes()
    def clearSelection(self): self._sel = set()
    def selectRow(self, r): self._sel = {r}; self._cur = r
    def selectAll(self):
        if self._model is not None: self._sel = set(range(self._model.rowCount()))
    def expandAll(self): pass
    def collapseAll(self): pass
    def expand(self, *a): pass
    def setExpanded(self, *a): pass
    def header(self): return _Header()
    def horizontalHeader(self): return _Header()
    def verticalHeader(self): return _Header()
    def setItemDelegate(self, *a): pass
    def setItemDelegateForColumn(self, *a): pass
    def setColumnWidth(self, *a): pass
    def setColumnHidden(self, *a): pass
    def resizeColumnToContents(self, *a): pass
    def scrollTo(self, *a): pass
    def edit(self, *a): pass
    def setRootIndex(self, *a): pass
    def viewport(self): return self
    # app events: tap a row, toggle a check box, edit a cell
    def _user(self, v):
        r = int(v); self._sel = {r}; self._cur = r
        idx = self._model.index(r, 0) if self._model is not None else QModelIndex()
        self._selmodel.selectionChanged.emit(None, None); self._selmodel.currentChanged.emit(idx, QModelIndex())
        self.clicked.emit(idx)
    def _check(self, r, on, c=0):
        it = self._model.item(int(r), int(c)) if self._model is not None else None
        if it is not None: it.setCheckState(2 if on else 0)
    def _user_cell(self, r, c, text):
        it = self._model.item(int(r), int(c)) if self._model is not None else None
        if it is None and self._model is not None: it = QStandardItem(); self._model.setItem(int(r), int(c), it)
        if it is not None:
            v = it.data(0)
            if isinstance(v, (int, float)) and not isinstance(v, bool):
                try: text = type(v)(float(text))
                except ValueError: pass
            it.setData(text, 0) if not isinstance(text, str) else it.setText(text)


class _Header:
    def __getattr__(self, n): return lambda *a, **k: None


class QTreeView(QAbstractItemView): pass
class QTableView(QAbstractItemView): pass
class QListView(QAbstractItemView): pass
class QColumnView(QAbstractItemView): pass
class QHeaderView(QWidget):
    class ResizeMode:
        Interactive = 0; Stretch = 1; Fixed = 2; ResizeToContents = 3
    Interactive = 0; Stretch = 1; Fixed = 2; ResizeToContents = 3
class QItemSelectionModel(_SelModel): pass
class QStyledItemDelegate(QObject):
    def __init__(self, *a, **k): super().__init__(None)
class QItemDelegate(QStyledItemDelegate): pass
class QAbstractItemModel(QStandardItemModel): pass
class QAbstractTableModel(QStandardItemModel): pass
class QAbstractListModel(QStandardItemModel): pass
class QSortFilterProxyModel(QStandardItemModel):
    def setSourceModel(self, m): self._src = m
    def sourceModel(self): return getattr(self, '_src', None)
    def mapToSource(self, i): return i
    def mapFromSource(self, i): return i
    def setFilterFixedString(self, *a): pass
    def setFilterRegularExpression(self, *a): pass
    def setFilterKeyColumn(self, *a): pass
    def setFilterCaseSensitivity(self, *a): pass
    def setRecursiveFilteringEnabled(self, *a): pass


class QByteArray(bytes):
    def data(self): return bytes(self)
    def toStdString(self): return self.decode('utf-8', 'replace')
    def __str__(self): return self.decode('utf-8', 'replace')


class QProcessEnvironment:
    def __init__(self, env=None): self._e = dict(env or {})
    @staticmethod
    def systemEnvironment():
        import os
        return QProcessEnvironment(os.environ)
    def insert(self, k, v): self._e[str(k)] = str(v)
    def remove(self, k): self._e.pop(str(k), None)
    def value(self, k, default=''): return self._e.get(str(k), default)
    def contains(self, k): return str(k) in self._e
    def keys(self): return list(self._e)


class QProcess(QObject):
    """QProcess over subprocess: FEM's meshers and solvers (Gmsh, CalculiX, Elmer, Z88 ...) start external programs
    with it.  There is no event loop, so start() runs the program to completion (commands run on a worker thread) and
    then emits started / readyRead* / finished exactly as Qt would."""
    class ProcessState:
        NotRunning = _E(0); Starting = _E(1); Running = _E(2)
    class ExitStatus:
        NormalExit = _E(0); CrashExit = _E(1)
    class ProcessError:
        FailedToStart = _E(0); Crashed = _E(1); Timedout = _E(2); WriteError = _E(4); ReadError = _E(3); UnknownError = _E(5)
    class ProcessChannelMode:
        SeparateChannels = _E(0); MergedChannels = _E(1); ForwardedChannels = _E(2)
    NotRunning = ProcessState.NotRunning; Running = ProcessState.Running; NormalExit = ExitStatus.NormalExit
    CrashExit = ExitStatus.CrashExit; MergedChannels = ProcessChannelMode.MergedChannels; SeparateChannels = ProcessChannelMode.SeparateChannels
    FailedToStart = ProcessError.FailedToStart
    started = Signal(); finished = Signal(int, object); errorOccurred = Signal(object); stateChanged = Signal(object)
    readyReadStandardOutput = Signal(); readyReadStandardError = Signal(); readyRead = Signal()
    def __init__(self, *a, **k):
        super().__init__(next((x for x in a if isinstance(x, QObject)), None))
        self._prog = ''; self._args = []; self._cwd = None; self._env = None; self._merged = False
        self._out = b''; self._err = b''; self._code = 0; self._status = QProcess.ExitStatus.NormalExit; self._error = None
    def setProgram(self, p): self._prog = str(p)
    def program(self): return self._prog
    def setArguments(self, a): self._args = [str(x) for x in a]
    def arguments(self): return list(self._args)
    def setWorkingDirectory(self, d): self._cwd = str(d)
    def workingDirectory(self): return self._cwd or ''
    def setProcessEnvironment(self, e): self._env = e
    def processEnvironment(self): return self._env or QProcessEnvironment.systemEnvironment()
    def setEnvironment(self, lst): self._env = QProcessEnvironment(dict(x.split('=', 1) for x in lst if '=' in x))
    def setProcessChannelMode(self, m): self._merged = int(m) == 1
    def setStandardOutputFile(self, *a): pass
    def setStandardErrorFile(self, *a): pass
    def start(self, program=None, args=None, *a):
        import subprocess, shlex
        if program is not None:
            if args is None and isinstance(program, str) and ' ' in program and not self._args:
                parts = shlex.split(program); program, args = parts[0], parts[1:]
            self._prog = str(program)
        if args is not None: self._args = [str(x) for x in args]
        self._out = b''; self._err = b''
        try:
            p = subprocess.Popen([self._prog] + self._args, cwd=self._cwd or None, env=self._env._e if self._env else None,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT if self._merged else subprocess.PIPE)
        except OSError as e:
            self._error = QProcess.ProcessError.FailedToStart; self._code = -1
            self.errorOccurred.emit(QProcess.ProcessError.FailedToStart); return
        self.started.emit(); self.stateChanged.emit(QProcess.ProcessState.Running)
        out, err = p.communicate()
        self._out, self._err = out or b'', err or b''
        self._code = p.returncode if p.returncode >= 0 else -1
        self._status = QProcess.ExitStatus.NormalExit if p.returncode >= 0 else QProcess.ExitStatus.CrashExit
        if self._out: self.readyReadStandardOutput.emit(); self.readyRead.emit()
        if self._err: self.readyReadStandardError.emit()
        self.stateChanged.emit(QProcess.ProcessState.NotRunning)
        if p.returncode < 0:
            self._error = QProcess.ProcessError.Crashed; self.errorOccurred.emit(QProcess.ProcessError.Crashed)
        self.finished.emit(self._code, self._status)
    def startDetached(self, *a): self.start(*a); return True
    def waitForStarted(self, *a): return self._error is None
    def waitForFinished(self, *a): return self._error is None
    def waitForReadyRead(self, *a): return bool(self._out)
    def state(self): return QProcess.ProcessState.NotRunning
    def exitCode(self): return self._code
    def exitStatus(self): return self._status
    def error(self): return self._error if self._error is not None else QProcess.ProcessError.UnknownError
    def readAllStandardOutput(self): o = self._out; self._out = b''; return QByteArray(o)
    def readAllStandardError(self): e = self._err; self._err = b''; return QByteArray(e)
    def readAll(self): return self.readAllStandardOutput()
    def kill(self): pass
    def terminate(self): pass
    def close(self): pass
    def write(self, *a): return 0
    def closeWriteChannel(self): pass
    def processId(self): return 0
    @staticmethod
    def execute(program, args=None):
        import subprocess
        try: return subprocess.call([str(program)] + [str(x) for x in (args or [])])
        except OSError: return -2


class QThread(QObject):
    """QThread for panels that prepare / run work in a thread (FEM log panels): start() runs run() at once on the
    command's worker thread, emitting started / finished around it."""
    started = Signal(); finished = Signal()
    def __init__(self, *a, **k): super().__init__(next((x for x in a if isinstance(x, QObject)), None)); self._running = False
    def start(self, *a):
        self._running = True; self.started.emit()
        try: self.run()
        finally:
            self._running = False; self.finished.emit()
    def run(self): pass
    def isRunning(self): return self._running
    def isFinished(self): return not self._running
    def quit(self): pass
    def exit(self, *a): pass
    def wait(self, *a): return True
    def terminate(self): pass
    def requestInterruption(self): pass
    def isInterruptionRequested(self): return False
    @staticmethod
    def currentThread(): return QThread()
    @staticmethod
    def idealThreadCount():
        import os; return os.cpu_count() or 1
    @staticmethod
    def msleep(ms):
        import time; time.sleep(ms / 1000.0)
    @staticmethod
    def sleep(s):
        import time; time.sleep(s)


class QElapsedTimer:
    def __init__(self): self._t = None
    def start(self):
        import time; self._t = time.monotonic()
    restart = start
    def elapsed(self):
        import time; return int((time.monotonic() - (self._t or time.monotonic())) * 1000)
    def isValid(self): return self._t is not None
    def invalidate(self): self._t = None


class QMutex:
    def lock(self): pass
    def unlock(self): pass
    def tryLock(self, *a): return True


class QMutexLocker:
    def __init__(self, *a): pass
    def __enter__(self): return self
    def __exit__(self, *a): return False
    def unlock(self): pass


class QRunnable(metaclass=_QtMeta):
    def __init__(self, *a, **k): pass
    def run(self): pass
    def setAutoDelete(self, *a): pass
    def autoDelete(self): return True


class QThreadPool(QObject):
    """Runs a QRunnable at once (there is no event loop; commands already run on a worker thread)."""
    _inst = None
    @staticmethod
    def globalInstance():
        if QThreadPool._inst is None: QThreadPool._inst = QThreadPool()
        return QThreadPool._inst
    def start(self, r, *a): r.run()
    def waitForDone(self, *a): return True
    def maxThreadCount(self): return 1
    def setMaxThreadCount(self, *a): pass
    def activeThreadCount(self): return 0


class QFileInfo:
    """QFileInfo over os.path."""
    def __init__(self, *a):
        import os
        p = a[-1] if a else ''
        if len(a) == 2: p = os.path.join(str(a[0].path() if hasattr(a[0], 'path') else a[0]), str(a[1]))
        self._p = str(getattr(p, '_p', p))
    def _os(self):
        import os; return os
    def filePath(self): return self._p
    def absoluteFilePath(self): return self._os().path.abspath(self._p)
    def canonicalFilePath(self): return self._os().path.realpath(self._p)
    def fileName(self): return self._os().path.basename(self._p)
    def baseName(self): return self.fileName().split('.')[0]
    def completeBaseName(self): return self._os().path.splitext(self.fileName())[0]
    def suffix(self): return self.fileName().rsplit('.', 1)[-1] if '.' in self.fileName() else ''
    def completeSuffix(self): return self.fileName().split('.', 1)[1] if '.' in self.fileName() else ''
    def path(self): return self._os().path.dirname(self._p) or '.'
    def absolutePath(self): return self._os().path.dirname(self.absoluteFilePath())
    def dir(self): return QDir(self.path())
    def exists(self): return self._os().path.exists(self._p)
    def isFile(self): return self._os().path.isfile(self._p)
    def isDir(self): return self._os().path.isdir(self._p)
    def isReadable(self): return self._os().access(self._p, self._os().R_OK)
    def isWritable(self): return self._os().access(self._p, self._os().W_OK)
    def isExecutable(self): return self._os().access(self._p, self._os().X_OK)
    def size(self): return self._os().path.getsize(self._p) if self.exists() else 0
    def lastModified(self): return self._os().path.getmtime(self._p) if self.exists() else 0


class QDir:
    """QDir over os / os.path (FEM solvers change into their working directory)."""
    def __init__(self, p=''): self._p = str(getattr(p, '_p', p) or '.')
    @staticmethod
    def currentPath():
        import os; return os.getcwd()
    @staticmethod
    def setCurrent(p):
        import os
        try: os.chdir(str(p)); return True
        except OSError: return False
    @staticmethod
    def homePath():
        import os; return os.path.expanduser('~')
    @staticmethod
    def tempPath():
        import tempfile; return tempfile.gettempdir()
    @staticmethod
    def toNativeSeparators(p): return str(p)
    @staticmethod
    def fromNativeSeparators(p): return str(p)
    @staticmethod
    def cleanPath(p):
        import os; return os.path.normpath(str(p))
    @staticmethod
    def separator(): return '/'
    def path(self): return self._p
    def absolutePath(self):
        import os; return os.path.abspath(self._p)
    def filePath(self, n):
        import os; return os.path.join(self._p, str(n))
    def absoluteFilePath(self, n):
        import os; return os.path.abspath(os.path.join(self._p, str(n)))
    def exists(self, n=None):
        import os; return os.path.exists(self.filePath(n) if n else self._p)
    def mkpath(self, p):
        import os; os.makedirs(os.path.join(self._p, str(p)), exist_ok=True); return True
    def mkdir(self, p):
        import os
        try: os.mkdir(os.path.join(self._p, str(p))); return True
        except OSError: return False
    def remove(self, n):
        import os
        try: os.remove(self.filePath(n)); return True
        except OSError: return False
    def entryList(self, *a):
        import os, fnmatch
        pats = next((x for x in a if isinstance(x, (list, tuple))), None)
        names = sorted(os.listdir(self._p)) if os.path.isdir(self._p) else []
        return [n for n in names if not pats or any(fnmatch.fnmatch(n, p) for p in pats)]
    def dirName(self):
        import os; return os.path.basename(self._p.rstrip('/'))
    def cd(self, p):
        import os
        q = os.path.join(self._p, str(p))
        if os.path.isdir(q): self._p = q; return True
        return False

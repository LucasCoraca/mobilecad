"""Placeholders for GUI-only bindings (PySide/Qt widgets, pivy/Coin3D) in headless FreeCAD on Android.

FreeCAD modules such as Assembly's JointObject.py import these at module level but only *use* them in view providers and
task panels, which never run without FreeCAD's Gui. Any attribute resolves to a subclassable, callable placeholder."""


def _num(other, default=0):
    return other if isinstance(other, (int, float)) else default


class _StubMeta(type):
    """Class-level attribute access (QtGui.QSyntaxHighlighter on a placeholder *class*) also yields placeholders, and
    placeholder enum values take part in arithmetic / flag expressions (Qt.UserRole + 1, AlignLeft | AlignTop)."""
    def __getattr__(cls, name):
        if name.startswith('__'):
            raise AttributeError(name)
        return _StubMeta(name, (Stub,), {})
    __add__ = __radd__ = __sub__ = __rsub__ = __mul__ = __rmul__ = lambda c, o: _num(o)
    __or__ = __ror__ = __and__ = __rand__ = __xor__ = __rxor__ = lambda c, o: c
    __int__ = __index__ = lambda c: 0
    __float__ = lambda c: 0.0


class Stub(metaclass=_StubMeta):
    def __init__(self, *args, **kwargs):
        pass

    def __call__(self, *args, **kwargs):
        return Stub()

    def __getattr__(self, name):
        if name.startswith('__'):
            raise AttributeError(name)
        return Stub()

    def __bool__(self):
        return False

    __add__ = __radd__ = __sub__ = __rsub__ = __mul__ = __rmul__ = lambda s, o: _num(o)
    __lshift__ = __rlshift__ = __rshift__ = __rrshift__ = __truediv__ = __rtruediv__ = __floordiv__ = lambda s, o: _num(o)
    __neg__ = __pos__ = lambda s: 0
    __lt__ = __gt__ = __le__ = __ge__ = lambda s, o: False
    __or__ = __ror__ = __and__ = __rand__ = lambda s, o: s
    __int__ = __index__ = lambda s: 0
    __float__ = lambda s: 0.0

    def __iter__(self):
        return iter(())


def module_getattr(name):
    if name.startswith('__'):
        raise AttributeError(name)
    return _StubMeta(name, (Stub,), {})

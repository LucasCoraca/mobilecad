"""Headless stand-in for FreeCAD's PySide redirect (no Qt widgets on Android). See _stub.py."""
from . import QtCore, QtGui, QtWidgets, QtSvg, QtNetwork  # noqa: F401  (real submodules before the fallback __getattr__)
from _stub import module_getattr as __getattr__  # noqa: E402,F401

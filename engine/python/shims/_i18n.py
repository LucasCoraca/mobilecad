"""Interface language of the headless FreeCAD on Android: the lookup desktop FreeCAD gets from its QTranslators.

On the desktop, Gui installs FreeCAD's .qm catalogs into QCoreApplication; translate(context, text) (Python's
QtCore.QCoreApplication.translate, FreeCAD.Qt.translate, QObject.tr, a .ui form's retranslateUi with the form's class
as context) then returns the translation of that (context, source) pair, or the source itself.  Here the app hands
over the same catalogs (app asset i18n/<language>.json, "contexts", generated from FreeCAD's *.ts by
research/gen_i18n.py) through mobilecad.bridge.set_language; with none loaded every lookup returns the source text.
"""
import json

_contexts = {}
_app = {}
_language = ['en']


def load(path, language):
    """Use the catalog at `path` (None: English, the sources as written)."""
    global _contexts, _app
    if not path:
        _contexts, _app = {}, {}
    else:
        with open(path, encoding='utf-8') as f: j = json.load(f)
        _contexts, _app = j.get('contexts', {}), j.get('app', {})
    _language[0] = language or 'en'


def language():
    return _language[0]


def translate(context, text, disambiguation=None, n=-1, *a, **k):
    """QCoreApplication::translate: the translation of `text` in `context`, else `text` unchanged."""
    if not isinstance(text, str) or not text: return text
    c = _contexts.get(str(context) if context is not None else '') if _contexts else None
    out = c.get(text, text) if c else text
    return out.replace('%n', str(n)) if isinstance(n, int) and n >= 0 else out


def tr(text):
    """MobileCAD's own engine messages (errors the app shows): English text -> the app catalog's translation.
    Templates keep their %s / %d placeholders and are formatted after translating: tr('%s has no solid') % name."""
    return _app.get(text, text) if _app else text

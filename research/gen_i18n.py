"""Build the app's interface translation asset app/app/src/main/assets/i18n/<lang>.json.

    python3 -I research/gen_i18n.py <FreeCAD checkout> zh-Hans

FreeCAD's own wording comes from its Qt catalogs (src/**/translations/*_<qt code>.ts, LGPL-2.1-or-later, by the
FreeCAD translators). It is looked up the way the desktop looks it up:
  * commands: QCoreApplication::translate(context, sMenuText / sToolTipText), where the context is the C++ command
    class (Command::className(), DEF_STD_CMD(CmdPartDesignPad)) or, for Python commands, the command name
    (PythonCommand::languageChange -> applyCommandData(getName(), ...));
  * everything else keeps its (context, source) pair: App::Property for property names and tooltips, the .ui form
    class for task panels, "Workbench" for toolbar names, the context Python code passes to translate().
MobileCAD's own interface strings are written by hand in app/i18n/<lang>.json (English text -> translation) and
always win over FreeCAD's wording.
"""
import glob, json, os, re, sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
QT_CODE = {'zh-Hans': 'zh-CN'}          # app language code -> FreeCAD's catalog suffix
NAMES = {'zh-Hans': '简体中文'}


def ts_entries(root, qt):
    """{context: {source: translation}} from every finished translation in FreeCAD's catalogs."""
    out = {}
    for ts in sorted(glob.glob(os.path.join(root, 'src', '**', '*_%s.ts' % qt), recursive=True)):
        for c in ET.parse(ts).getroot().iter('context'):
            name = c.findtext('name') or ''
            for m in c.iter('message'):
                t = m.find('translation'); s = m.findtext('source')
                if t is None or not s or t.get('type') in ('unfinished', 'vanished', 'obsolete'): continue
                if m.get('numerus') == 'yes':
                    forms = [f.text for f in t.iter('numerusform') if f.text]
                    text = forms[0] if forms else ''
                else:
                    text = t.text or ''
                if text.strip(): out.setdefault(name, {}).setdefault(s, text)
    return out


def command_contexts(root):
    """Command name -> C++ class (the translation context of its texts)."""
    out = {}
    for f in glob.glob(os.path.join(root, 'src', '**', '*.cpp'), recursive=True):
        t = open(f, errors='ignore').read()
        # Cls::Cls(const char* name = "Sketcher_X") declarations hand the default name to the base class
        for cls, name in re.findall(r'\b(\w+)\(\s*const char\s*\*\s*\w+\s*=\s*"(\w+)"\s*\)', t):
            out.setdefault(name, cls)
        for cls, name in re.findall(r'\b(\w+)::\1\(\)\s*:\s*[\w:]+\(\s*"(\w+)"', t):
            out.setdefault(name, cls)
    return out


def strip_accel(s):
    """Menu accelerators: '打开(&O)…' -> '打开…', '&Open' -> 'Open'."""
    return re.sub(r'\(&\w\)', '', s).replace('&&', '\0').replace('&', '').replace('\0', '&').strip()


def main():
    root, lang = sys.argv[1], sys.argv[2]
    ctx = ts_entries(root, QT_CODE[lang])
    classes = command_contexts(root)
    catalog = json.load(open(os.path.join(REPO, 'app/app/src/main/assets/freecad/catalog.json')))
    fc = json.load(open(os.path.join(HERE, 'freecad_commands.json')))['commands']
    rr = os.path.join(HERE, 'freecad_command_resources_runtime.json')
    runtime = json.load(open(rr)) if os.path.exists(rr) else {}

    anyctx = {}
    for c in ctx.values():
        for k, v in c.items(): anyctx.setdefault(k, {}).setdefault(v, 0); anyctx[k][v] += 1
    def find(contexts, texts):
        for c in contexts:
            for s in texts:
                if c and s and s in ctx.get(c, {}): return ctx[c][s]
        for s in texts:                                  # no context of its own: FreeCAD's most common wording
            if s and s in anyctx: return max(anyctx[s].items(), key=lambda kv: kv[1])[0]
        return None
    def variants(*ts):
        out = []
        for t in ts:
            if not t: continue
            t = t.strip('"')
            m = re.match(r"\s*(?:QtCore\.)?QT_TRANSLATE_NOOP\(\s*['\"](\w+)['\"]\s*,\s*['\"](.*?)['\"]", t)
            if m: out += [m.group(2)]; continue
            out += [t, t.replace('...', '…'), t.replace('…', '...')]
        return out
    parent = {m: g for g, c in catalog['commands'].items() for m in c.get('group', [])}

    cmds = {}
    for cid, c in catalog['commands'].items():
        src = fc.get(cid, {})
        contexts = [classes.get(cid), cid, classes.get(parent.get(cid)), parent.get(cid)]
        m = find(contexts, variants(src.get('sMenuText'), runtime.get(cid, {}).get('MenuText'), c.get('label')))
        t = find(contexts, variants(src.get('sToolTipText'), runtime.get(cid, {}).get('ToolTip'), c.get('tip')))
        if m or t: cmds[cid] = [strip_accel(m) if m else '', t or '']

    own = json.load(open(os.path.join(REPO, 'app/i18n/%s.json' % lang), encoding='utf-8'))
    asset = {'language': lang, 'name': NAMES[lang],
             'note': 'Generated by research/gen_i18n.py. FreeCAD wording from FreeCAD\'s %s catalogs (LGPL-2.1-or-later)' % QT_CODE[lang],
             'app': own, 'commands': cmds, 'contexts': ctx,
             # English texts FreeCAD translates differently in different contexts: the most common wording, for
             # strings looked up without a context (the app falls back to FreeCAD's wording for its own labels)
             'common': {s: max(v.items(), key=lambda kv: kv[1])[0] for s, v in anyctx.items() if len(v) > 1}}
    dst = os.path.join(REPO, 'app/app/src/main/assets/i18n/%s.json' % lang)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, 'w', encoding='utf-8') as f: json.dump(asset, f, ensure_ascii=False, separators=(',', ':'), sort_keys=True)
    print('%s: %d app strings, %d of %d commands, %d contexts / %d FreeCAD strings, %d KB' % (
        lang, len(own), len(cmds), len(catalog['commands']), len(ctx), sum(map(len, ctx.values())), os.path.getsize(dst) // 1024))


if __name__ == '__main__':
    main()

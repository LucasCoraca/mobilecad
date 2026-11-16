"""List the interface strings MobileCAD translates and check a language file against them.

    python3 -I research/i18n_keys.py zh-Hans            # missing / unused keys of app/i18n/zh-Hans.json
    python3 -I research/i18n_keys.py zh-Hans --missing  # missing keys only, one JSON string per line

Keys are the English texts given to tr("…") in the Kotlin app (constant concatenations "a" + "b" joined) and to
_tr('…') in the engine bridge, plus the strings those functions receive through a variable (EXTRA below).
"""
import ast, glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

# Python message helpers fed from tables / parameters rather than a literal: the strings that reach them
_PY_INDIRECT = [r"_need\([^,]+,\s*\d+,\s*('(?:[^'\\]|\\.)*'|\"(?:[^\"\\]|\\.)*\")"]

KT_STR = r'"(?:[^"\\$]|\\.|\$(?!\{|[A-Za-z_]))*"'


def kt_unescape(s):
    return json.loads(s.replace('\\$', '$'))


def kotlin_keys():
    keys = set()
    xr = os.path.join(os.path.dirname(REPO), 'mobilecad-xr-main', 'android', 'app', 'src', 'main', 'java')   # MobileCAD XR's own screens
    for f in glob.glob(os.path.join(REPO, 'app/app/src/main/java/**/*.kt'), recursive=True) + glob.glob(os.path.join(xr, '**/*.kt'), recursive=True):
        t = open(f, encoding='utf-8').read()
        for m in re.finditer(r'\btr\(\s*(' + KT_STR + r'(?:\s*\+\s*' + KT_STR + r')*)', t):
            parts = re.findall(KT_STR, m.group(1))
            keys.add(''.join(kt_unescape(p) for p in parts))
    return keys


def python_keys():
    keys = set()
    for f in glob.glob(os.path.join(REPO, 'engine/python/mobilecad/*.py')):
        src = open(f, encoding='utf-8').read()
        for node in ast.walk(ast.parse(src)):
            if isinstance(node, ast.Call) and getattr(node.func, 'id', None) == '_tr' and node.args:
                a = node.args[0]
                if isinstance(a, ast.Constant) and isinstance(a.value, str): keys.add(a.value)
                else:   # _tr(table.get(k, 'default')) / _tr(variable): every string constant in the expression
                    keys.update(n.value for n in ast.walk(a) if isinstance(n, ast.Constant) and isinstance(n.value, str) and re.search('[A-Za-z]{2}', n.value))
        for rx in _PY_INDIRECT:
            for m in re.finditer(rx, src): keys.add(ast.literal_eval(m.group(1)))
    return keys


def extra_keys():
    p = os.path.join(REPO, 'app/i18n/extra-keys.txt')
    return {l.rstrip('\n') for l in open(p, encoding='utf-8') if l.strip() and not l.startswith('#')} if os.path.exists(p) else set()


def all_keys():
    return {k for k in kotlin_keys() | python_keys() | extra_keys() if re.search('[A-Za-z]', k) and '::' not in k}


if __name__ == '__main__':
    lang = sys.argv[1]
    have = json.load(open(os.path.join(REPO, 'app/i18n/%s.json' % lang), encoding='utf-8'))
    keys = all_keys()
    missing = sorted(k for k in keys if k not in have)
    # a translation keeps every placeholder: Qt-style %1..%9 (any order) and Python %s / %d (same sequence)
    bad = [k for k, v in have.items() if sorted(re.findall(r'%[1-9]', k)) != sorted(re.findall(r'%[1-9]', v))
           or re.findall(r'%[sd]', k) != re.findall(r'%[sd]', v)]
    for k in bad: print('  PLACEHOLDERS DIFFER:', json.dumps(k, ensure_ascii=False), '->', json.dumps(have[k], ensure_ascii=False))
    if '--missing' in sys.argv:
        for k in missing: print(json.dumps(k, ensure_ascii=False))
    else:
        if bad: sys.exit(1)
        print('%d keys, %d translated, %d missing, %d unused' % (len(keys), len(keys) - len(missing), len(missing), len(set(have) - keys)))
        for k in missing[:50]: print('  missing:', json.dumps(k, ensure_ascii=False))
        for k in sorted(set(have) - keys)[:50]: print('  unused:', json.dumps(k, ensure_ascii=False))

"""Spreadsheet workbench (src/Mod/Spreadsheet/Gui/Command.cpp + SheetTableView): the sheet grid for the Variables tab and
the toolbar commands (alignment, styles, merge / split, alias, import / export)."""
import re
import FreeCAD as App
from .bridge import op, _doc

def _col(n):
    s = ''
    while True:
        s = chr(ord('A') + n % 26) + s; n = n // 26 - 1
        if n < 0: return s

def _split(cell):
    m = re.fullmatch(r'([A-Z]+)(\d+)', cell)
    c = 0
    for ch in m.group(1): c = c * 26 + (ord(ch) - 64)
    return c - 1, int(m.group(2)) - 1

def _cells(rng):
    """'A1' or 'A1:C3' -> list of cell names."""
    if ':' not in rng: return [rng]
    a, b = rng.split(':'); (c0, r0), (c1, r1) = _split(a), _split(b)
    return ['%s%d' % (_col(c), r + 1) for r in range(min(r0, r1), max(r0, r1) + 1) for c in range(min(c0, c1), max(c0, c1) + 1)]

@op
def sheet_list():
    return [[o.Name, o.Label] for o in _doc().Objects if o.TypeId == 'Spreadsheet::Sheet']

@op
def sheet_data(name, rows=40, cols=12):
    """What SheetTableView shows: contents, displayed value, alias, style, alignment and colours per used cell, plus
    column widths and row heights."""
    s = _doc().getObject(name)
    used = s.getNonEmptyCells() if hasattr(s, 'getNonEmptyCells') else []
    maxr, maxc = rows, cols
    cells = {}
    for c in used:
        ci, ri = _split(c); maxr = max(maxr, ri + 2); maxc = max(maxc, ci + 2)
        try: disp = s.get(c)
        except Exception as e: disp = '#ERR'
        if hasattr(disp, 'UserString'): disp = disp.UserString
        elif isinstance(disp, float): disp = ('%.6g' % disp)
        cells[c] = {'content': s.getContents(c), 'value': str(disp), 'alias': s.getAlias(c) or '',
                    'style': sorted(s.getStyle(c) or []), 'align': sorted(s.getAlignment(c) or []),
                    'bg': list(s.getBackground(c) or []), 'fg': list(s.getForeground(c) or [])}
    widths = {_col(i): s.getColumnWidth(_col(i)) for i in range(maxc)}
    heights = {str(i + 1): s.getRowHeight(str(i + 1)) for i in range(maxr)}
    return {'rows': maxr, 'cols': maxc, 'cells': cells, 'widths': widths, 'heights': heights}

@op
def sheet_set(name, cell, text):
    """Typing into a cell (SheetTableView edit): text, number with unit, or '=expression'."""
    s = _doc().getObject(name); s.set(cell, text); _doc().recompute(); return s.getContents(cell)

@op
def sheet_cmd(name, rng, kind, value=None):
    """Spreadsheet_Align* / Style* / MergeCells / SplitCell / SetAlias + clear, insert / remove rows & columns."""
    s = _doc().getObject(name); cells = _cells(rng)
    if kind == 'align':                        # 'left' 'center' 'right' 'top' 'vcenter' 'bottom'
        horiz = value in ('left', 'center', 'right')
        for c in cells:
            group = ('left', 'center', 'right') if horiz else ('top', 'vcenter', 'bottom')
            cur = {a for a in (s.getAlignment(c) or []) if a not in group}   # keep the other axis
            cur.add(value); s.setAlignment(c, '|'.join(sorted(cur)))
    elif kind == 'style':                      # toggle 'bold' 'italic' 'underline' like the toolbar buttons
        on = all(value in (s.getStyle(c) or []) for c in cells)
        for c in cells: s.setStyle(c, value, 'remove' if on else 'add')
    elif kind == 'merge':
        s.mergeCells(rng)
    elif kind == 'split':
        s.splitCell(cells[0])
    elif kind == 'alias':
        s.setAlias(cells[0], value or '')
    elif kind == 'clear':
        for c in cells: s.clear(c)
    elif kind == 'background':
        for c in cells: s.setBackground(c, tuple(value))
    elif kind == 'foreground':
        for c in cells: s.setForeground(c, tuple(value))
    elif kind == 'unit':
        for c in cells: s.setDisplayUnit(c, value or '')
    elif kind == 'insertRows':
        s.insertRows(str(_split(cells[0])[1] + 1), int(value or 1))
    elif kind == 'removeRows':
        s.removeRows(str(_split(cells[0])[1] + 1), int(value or 1))
    elif kind == 'insertColumns':
        s.insertColumns(_col(_split(cells[0])[0]), int(value or 1))
    elif kind == 'removeColumns':
        s.removeColumns(_col(_split(cells[0])[0]), int(value or 1))
    elif kind == 'width':
        s.setColumnWidth(_col(_split(cells[0])[0]), int(value))
    else:
        raise RuntimeError(kind)
    _doc().recompute(); return True

@op
def sheet_import(name, path):
    """Spreadsheet_Import: CSV/TSV into the sheet (delimiter from the extension, like the desktop dialog)."""
    s = _doc().getObject(name) if name else _doc().addObject('Spreadsheet::Sheet', 'Spreadsheet')
    delim = '\t' if path.lower().endswith(('.tsv', '.tab', '.txt')) else (';' if path.lower().endswith('.ssv') else ',')
    s.importFile(path, delim, '"', '\\'); _doc().recompute(); return s.Name

@op
def sheet_export(name, path):
    s = _doc().getObject(name)
    delim = '\t' if path.lower().endswith(('.tsv', '.tab', '.txt')) else ','
    s.exportFile(path, delim, '"', '\\'); return True

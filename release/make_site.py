"""Builds the download page from site_template.html: the gallery (serve/screens, made with release/shot.sh), the launch
video and the release notes already on the local page (publish.sh prepends a note per release).
    make_site.py            -> serve/index.html  (local server: APKs and source archives next to the page)
    make_site.py --github   -> docs/             (GitHub Pages: downloads from the latest GitHub Release, source = the repo)"""
import json, os, re, shutil, sys
R = os.path.dirname(os.path.abspath(__file__)); S = os.path.join(R, '..', 'serve')
TAB = [('tablet-partdesign.jpg', 'Part design', "The screwdriver bit from FreeCAD's PartDesignExample, with its full feature history."),
       ('tablet-feature-dialog.jpg', 'Edit any feature', 'Touch and hold a feature to reopen its dialog. The model updates as you change values.'),
       ('tablet-sketcher.jpg', 'Sketcher', "Constraints, dimensions and snapping, with the sketch's status always in view."),
       ('tablet-assembly.jpg', 'Assemblies', 'The excavator from AssemblyExample, with its components and joints.'),
       ('tablet-fem.jpg', 'FEM results', 'Stress results of the beam in FEMExample, coloured on the model.'),
       ('tablet-bim.jpg', 'BIM', 'The building from BIMExample, with walls, windows, structure and axes.'),
       ('tablet-engineblock.jpg', 'Part workbench', 'EngineBlock, built from Part primitives and construction geometry.'),
       ('tablet-dark.jpg', 'Dark mode', 'Follows the system theme, from the 3D view to the toolbars.')]
PH = [('phone-partdesign.jpg', 'Full screen for the model', 'The feature list folds away on phones.'),
      ('phone-tree.jpg', 'Feature list', 'Slides in from the tree button.'),
      ('phone-feature-dialog.jpg', 'Dialogs', 'Open as bottom sheets, so the model stays visible.'),
      ('phone-sketcher.jpg', 'Sketcher', 'The same tools, sized for one hand.'),
      ('phone-assembly.jpg', 'Assemblies', 'Every workbench works on a phone.'),
      ('phone-fem.jpg', 'FEM', 'Results stay readable on a small screen.')]
def cards(items, kind):
    return '\n'.join('      <figure class="card-shot" data-i="%d">\n        <button class="frame %s" aria-label="View %s full size">'
                     '<img src="screens/%s" alt="%s" loading="lazy"></button>\n        <figcaption><span class="t">%s</span>'
                     '<span class="d">%s</span></figcaption>\n      </figure>' % (k, kind, t, f, t, t, d) for k, (f, t, d) in enumerate(items))
page = os.path.join(S, 'index.html'); old = open(page).read() if os.path.exists(page) else ''
notes = '\n'.join(re.findall(r'<p style="font-size:14px"><b>Latest.*?</p>', old, re.S))
for f, _, _ in TAB + PH: assert os.path.exists(os.path.join(S, 'screens', f)), f
html = open(os.path.join(R, 'site_template.html')).read()
html = html.replace('__TAB__', cards(TAB, 'tablet')).replace('__PHONE__', cards(PH, 'phone')).replace('__NOTES__', notes)
html = html.replace('__SHOTS__', json.dumps({'tab': TAB, 'phone': PH}))
REPO = 'https://github.com/LucasCoraca/mobilecad'
if '--github' in sys.argv:
    rel = REPO + '/releases/latest'
    links = {'__APK_ARM__': rel + '/download/MobileCAD-arm64.apk', '__APK_X86__': rel + '/download/MobileCAD-x86_64.apk',
             '__SRC__': REPO, '__SRC_DIR__': rel, '__SUMS__': rel + '/download/SHA256SUMS'}
    out = os.path.join(R, '..', 'docs'); os.makedirs(os.path.join(out, 'screens'), exist_ok=True)
    for f, _, _ in TAB + PH: shutil.copy(os.path.join(S, 'screens', f), os.path.join(out, 'screens', f))
    for f in ('logo.png', 'favicon.png', 'mobilecad-launch.mp4', 'mobilecad-launch-poster.jpg'): shutil.copy(os.path.join(S, f), os.path.join(out, f))
    open(os.path.join(out, '.nojekyll'), 'w').close()
    for k, v in links.items(): html = html.replace(k, v)
    html = html.replace('<a href="#notes">Release notes</a>', '<a href="%s/releases">Releases</a>' % REPO)
    page = os.path.join(out, 'index.html')
else:
    for k, v in {'__APK_ARM__': 'MobileCAD-arm64.apk', '__APK_X86__': 'MobileCAD-x86_64.apk', '__SRC__': 'source/mobilecad-src.tar.xz',
                 '__SRC_DIR__': 'source/', '__SUMS__': 'source/SHA256SUMS'}.items(): html = html.replace(k, v)
open(page, 'w').write(html); print('%s: %d tablet, %d phone shots, %d notes' % (os.path.relpath(page, os.path.join(R, '..')), len(TAB), len(PH), notes.count('<p ')))

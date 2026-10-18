"""MobileCAD launch video: the screen recordings in clips/ (made with rec.sh on the emulator) framed in a tablet or phone
on a 1920x1080 canvas with a caption, between a title card and an end card, joined with crossfades.
    python3 make_video.py            -> ../../serve/mobilecad-launch.mp4 (+ poster jpg)"""
import os, subprocess, json
from PIL import Image, ImageDraw, ImageFont, ImageFilter
H = os.path.dirname(os.path.abspath(__file__)); CL = os.path.join(H, 'clips'); B = os.path.join(H, 'build'); os.makedirs(B, exist_ok=True)
OUT = os.path.join(H, '..', '..', 'serve', 'mobilecad-launch.mp4'); LOGO = os.path.join(H, '..', '..', 'branding', 'mobilecad-mark-512.png')
W, HT, FPS, FADE = 1920, 1080, 30, 0.5
F = '/usr/share/fonts/truetype/noto/NotoSans-%s.ttf'
def font(w, s): return ImageFont.truetype(F % w, s)
INK, MUTED, ACCENT, BEZEL = (22, 32, 43), (95, 107, 119), (42, 111, 201), (15, 19, 24)

# scene: clip, kind, seconds used, caption title, caption text, crop (top, bottom) as fractions of the recording
SCENES = [
 ('s1_partdesign', 'tablet', 7.0, 'Real CAD, made for touch', "FreeCAD's modelling engine runs on the device. Orbit with one finger.", (0.0225, 0.0763)),
 ('s2_edit', 'tablet', 9.4, 'Every feature stays editable', 'Reopen any feature and type new values on the built-in number pad.', (0.0225, 0.0763)),
 ('s3_sketch', 'tablet', 6.5, 'A full constraint sketcher', 'Dimensions, constraints and snapping, sized for fingers.', (0.0225, 0.0763)),
 ('s4_assembly', 'tablet', 6.0, 'Assemblies', 'Components and joints, from the excavator example.', (0.0225, 0.0763)),
 ('s5_fem', 'tablet', 6.0, 'FEM analysis', 'Stress results, coloured on the model.', (0.0225, 0.0763)),
 ('s6_bim', 'tablet', 6.0, 'BIM', 'Architectural models with walls, windows and structure.', (0.0225, 0.0763)),
 ('s7_dark', 'tablet', 6.0, 'Dark mode', 'Follows your system theme.', (0.0225, 0.0763)),
 ('s8_phone', 'phone', 9.9, 'Made for phones, too', 'The feature list slides in, and dialogs become bottom sheets. Phones, tablets and foldables.', (0.030, 0.025)),
]

def rounded(size, r, fill):
    m = Image.new('L', size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    im = Image.new('RGBA', size, fill); im.putalpha(m); return im

def background():
    bg = Image.new('RGB', (W, HT), (255, 255, 255)); d = ImageDraw.Draw(bg)
    for y in range(HT):                                                  # soft top-to-bottom wash
        t = y / HT; c = tuple(int(a + (b - a) * t) for a, b in zip((239, 243, 248), (252, 253, 255))); d.line((0, y, W, y), fill=c)
    return bg.convert('RGBA')

def wrap(d, text, f, width):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= width: cur = t
        else: lines.append(cur); cur = w
    return lines + [cur]

def overlay(kind, title, text, idx):
    """Opaque canvas with a transparent screen hole (the recording shows through), bezel, shadow and caption."""
    im = background(); d = ImageDraw.Draw(im)
    if kind == 'tablet':
        sw, sh = 1456, 814; sx, sy = (W - sw) // 2, 204; bez, r = 16, 26      # bezel ends above the step dots
        d.text((sx, 64), title, font=font('Bold', 52), fill=INK)
        d.text((sx, 136), text, font=font('Regular', 28), fill=MUTED)
    else:
        sh = 930; sw = int(sh * 720 / (1600 * (1 - 0.030 - 0.025)));   # the cropped recording's aspect
        sx, sy = 1180, (HT - sh) // 2; bez, r = 12, 46
        tx = 200; d.text((tx, 380), title, font=font('Bold', 64), fill=INK)
        y = 470
        for line in wrap(d, text, font('Regular', 32), 820): d.text((tx, y), line, font=font('Regular', 32), fill=MUTED); y += 48
    # shadow, bezel, hole
    sh_layer = Image.new('RGBA', (W, HT), (0, 0, 0, 0)); ImageDraw.Draw(sh_layer).rounded_rectangle(
        (sx - bez, sy - bez + 18, sx + sw + bez, sy + sh + bez + 18), r + bez, fill=(16, 32, 52, 70))
    im = Image.alpha_composite(im, sh_layer.filter(ImageFilter.GaussianBlur(28)))
    frame = rounded((sw + 2 * bez, sh + 2 * bez), r + bez, BEZEL + (255,)); im.alpha_composite(frame, (sx - bez, sy - bez))
    hole = Image.new('L', (W, HT), 255); ImageDraw.Draw(hole).rounded_rectangle((sx, sy, sx + sw - 1, sy + sh - 1), r, fill=0)
    im.putalpha(Image.composite(im.getchannel('A'), Image.new('L', (W, HT), 0), hole))
    # step dots
    for k in range(len(SCENES)):
        cx = W // 2 - (len(SCENES) - 1) * 11 + k * 22; ImageDraw.Draw(im).ellipse((cx - 4, HT - 26, cx + 4, HT - 18), fill=ACCENT if k == idx else (200, 208, 217))
    p = os.path.join(B, 'ov_%d.png' % idx); im.save(p); return p, (sx, sy, sw, sh)

def card(name, lines):
    im = background().convert('RGB'); d = ImageDraw.Draw(im)
    logo = Image.open(LOGO).convert('RGBA').resize((220, 220), Image.LANCZOS); im.paste(logo, ((W - 220) // 2, 250), logo)
    y = 500
    for txt, f, col, gap in lines:
        tw = d.textlength(txt, font=f); d.text(((W - tw) / 2, y), txt, font=f, fill=col); y += gap
    p = os.path.join(B, name + '.png'); im.save(p); return p

def run(cmd): subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

segs = []
intro = card('intro', [('MobileCAD', font('Bold', 96), INK, 130), ('Real CAD, made for touch.', font('Regular', 40), MUTED, 70),
                       ('Parametric CAD for Android, built on the FreeCAD engine', font('Regular', 28), ACCENT, 60)])
outro = card('outro', [('MobileCAD', font('Bold', 96), INK, 130), ('Free and open source · Android 8 or newer', font('Regular', 40), MUTED, 70),
                       ('Built on FreeCAD, Open CASCADE and Qt · GPL-3.0', font('Regular', 28), MUTED, 90),
                       ('Not affiliated with the FreeCAD project. Models: FreeCAD example projects.', font('Regular', 22), (138, 150, 163), 40)])
for name, png, dur in (('intro', intro, 3.5), ('outro', outro, 4.5)):
    o = os.path.join(B, name + '.mp4')
    run(['ffmpeg', '-y', '-loop', '1', '-t', str(dur), '-i', png, '-vf', 'fps=%d,format=yuv420p' % FPS, '-c:v', 'libx264', '-crf', '18', '-preset', 'medium', o])
    segs.append((o, dur))
outro_seg = segs.pop()
for i, (clip, kind, dur, title, text, (ct, cb)) in enumerate(SCENES):
    ov, (sx, sy, sw, sh) = overlay(kind, title, text, i); o = os.path.join(B, 'seg_%d.mp4' % i)
    vf = ('[0:v]fps=%d,crop=iw:ih*%.4f:0:ih*%.4f,scale=%d:%d:flags=lanczos,tpad=stop_mode=clone:stop_duration=%s,trim=duration=%s,setpts=PTS-STARTPTS[v];'
          'color=c=white:s=%dx%d:r=%d:d=%s[bg];[bg][v]overlay=%d:%d[b];[b][1:v]overlay=0:0,format=yuv420p[o]'
          % (FPS, 1 - ct - cb, ct, sw, sh, dur, dur, W, HT, FPS, dur, sx, sy))
    run(['ffmpeg', '-y', '-i', os.path.join(CL, clip + '.mp4'), '-loop', '1', '-i', ov, '-filter_complex', vf, '-map', '[o]', '-t', str(dur),
         '-c:v', 'libx264', '-crf', '18', '-preset', 'medium', o])
    segs.append((o, dur))
segs.append(outro_seg)
# crossfade chain
inputs, fc, off, last = [], [], 0.0, '0:v'
for k, (o, dur) in enumerate(segs): inputs += ['-i', o]
off = segs[0][1] - FADE
for k in range(1, len(segs)):
    lab = 'x%d' % k; fc.append('[%s][%d:v]xfade=transition=fade:duration=%s:offset=%.3f[%s]' % (last, k, FADE, off, lab)); last = lab; off += segs[k][1] - FADE
fc.append('[%s]fade=t=in:st=0:d=0.4,format=yuv420p[out]' % last)
run(['ffmpeg', '-y'] + inputs + ['-filter_complex', ';'.join(fc), '-map', '[out]', '-c:v', 'libx264', '-crf', '20', '-preset', 'slow',
     '-movflags', '+faststart', '-pix_fmt', 'yuv420p', OUT])
run(['ffmpeg', '-y', '-ss', '5', '-i', OUT, '-frames:v', '1', '-q:v', '3', OUT.replace('.mp4', '-poster.jpg')])
d = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', OUT]))
print('%s: %.1f s, %.1f MB' % (OUT, d, os.path.getsize(OUT) / 1e6))

"""Generate MenetrendGO's own transit pictograms.

Outputs (all original artwork, no provider assets are read):
  app/src/main/res/font/transit_pictograms.ttf   provider fontCode points -> our glyphs
  app/src/main/res/drawable/ic_mode_*.xml        mode filter pictograms
  app/src/main/assets/vehicle-icons.js           Leaflet live-map vehicle markers

The provider identifies route marks and service pictograms by numeric code point
(fontCharSet "MNR2007"). This font keeps those code points and each glyph's
bounding box (scripts/pictograms/metrics.json) so layouts stay put, but every
outline is drawn here: lettering comes from Barlow Semi Condensed (SIL OFL 1.1,
scripts/pictograms/fonts) and pictograms are built from plain geometry below.
The resulting font is a Modified Version of Barlow and is distributed under the OFL.

Requires: pip install fonttools skia-pathops
Run:      python scripts/generate_pictograms.py
"""
import json, math, os, sys
import pathops
from fontTools.ttLib import TTFont
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.reverseContourPen import ReverseContourPen
from fontTools.pens.recordingPen import DecomposingRecordingPen

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HERE = os.path.join(ROOT, "scripts", "pictograms")
APP = os.path.join(ROOT, "app", "src", "main")
METRICS = {int(k): v for k, v in json.load(open(os.path.join(HERE, "metrics.json"))).items()}

# ---------------------------------------------------------------- geometry
K = 0.5522847498

def P(): return pathops.Path()

def union(*ps):
    out = P()
    for p in ps:
        if p is None: continue
        out = pathops.op(out, p, pathops.PathOp.UNION, fix_winding=True)
    return out

def diff(a, *bs):
    for b in bs: a = pathops.op(a, b, pathops.PathOp.DIFFERENCE, fix_winding=True)
    return a

def inter(a, b): return pathops.op(a, b, pathops.PathOp.INTERSECTION, fix_winding=True)

def rect(x, y, w, h, r=0):
    p = P(); r = min(r, w / 2, h / 2)
    if r <= 0:
        p.moveTo(x, y); p.lineTo(x + w, y); p.lineTo(x + w, y + h); p.lineTo(x, y + h); p.close(); return p
    k = r * K
    p.moveTo(x + r, y); p.lineTo(x + w - r, y)
    p.cubicTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r); p.lineTo(x + w, y + h - r)
    p.cubicTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h); p.lineTo(x + r, y + h)
    p.cubicTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r); p.lineTo(x, y + r)
    p.cubicTo(x, y + r - k, x + r - k, y, x + r, y); p.close(); return p

def ellipse(cx, cy, rx, ry):
    p = P(); kx, ky = rx * K, ry * K
    p.moveTo(cx + rx, cy); p.cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
    p.cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
    p.cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
    p.cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy); p.close(); return p

def circle(cx, cy, r): return ellipse(cx, cy, r, r)
def ring(cx, cy, r, w): return diff(circle(cx, cy, r), circle(cx, cy, r - w))
def frame(x, y, w, h, t, r=0): return diff(rect(x, y, w, h, r), rect(x + t, y + t, w - 2 * t, h - 2 * t, max(r - t, 0)))

def poly(*pts):
    p = P(); p.moveTo(*pts[0])
    for q in pts[1:]: p.lineTo(*q)
    p.close(); return p

def line(pts, w, cap="round", join="round"):
    p = P(); p.moveTo(*pts[0])
    for q in pts[1:]: p.lineTo(*q)
    caps = {"round": pathops.LineCap.ROUND_CAP, "butt": pathops.LineCap.BUTT_CAP, "square": pathops.LineCap.SQUARE_CAP}
    joins = {"round": pathops.LineJoin.ROUND_JOIN, "miter": pathops.LineJoin.MITER_JOIN}
    p.stroke(w, caps[cap], joins[join], 4)
    p.convertConicsToQuads()
    return union(p)

def arc(cx, cy, r, a0, a1, w, cap="round", steps=24):
    pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
            cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
    return line(pts, w, cap)

def xform(p, a=1, b=0, c=0, d=1, e=0, f=0):
    out = P(); p.draw(TransformPen(out.getPen(), (a, b, c, d, e, f))); return out

def move(p, dx, dy): return xform(p, 1, 0, 0, 1, dx, dy)
def scale(p, s, cx=0, cy=0): return xform(p, s, 0, 0, s, cx - s * cx, cy - s * cy)
def rotate(p, deg, cx, cy):
    a = math.radians(deg); co, si = math.cos(a), math.sin(a)
    return xform(p, co, si, -si, co, cx - co * cx + si * cy, cy - si * cx - co * cy)
def mirror(p, cx): return xform(p, -1, 0, 0, 1, 2 * cx, 0)

def bounds(p):
    b = p.bounds
    return None if b == (0, 0, 0, 0) or b[2] <= b[0] else b

# ---------------------------------------------------------------- lettering
FONTS = {k: TTFont(os.path.join(HERE, "fonts", f)) for k, f in {
    "bold": "BarlowSemiCondensed-Bold.ttf", "italic": "BarlowSemiCondensed-BoldItalic.ttf",
    "heavy": "BarlowSemiCondensed-ExtraBoldItalic.ttf"}.items()}

def text(s, style="bold", cap=70.0, track=0.0):
    if style == "heavy":  # extra oblique and width for the speed of long-distance marks
        t = _text(s, "heavy", cap, track); return xform(t, 1.22, 0, -0.16, 1, 0, 0)
    return _text(s, style, cap, track)

def _text(s, style="bold", cap=70.0, track=0.0):
    """Lettering in a y-down design space; baseline at y=0, cap height = `cap` units."""
    f = FONTS[style]; gs = f.getGlyphSet(); cmap = f.getBestCmap(); k = cap / f["OS/2"].sCapHeight
    out = P(); x = 0.0
    for ch in s:
        name = cmap.get(ord(ch))
        if name is None: raise SystemExit(f"Barlow lacks {ch!r}")
        rec = DecomposingRecordingPen(gs); gs[name].draw(rec)
        rec.replay(TransformPen(out.getPen(), (k, 0, 0, -k, x, 0)))
        x += gs[name].width * k + track * cap
    return union(out)

def centred(p, cx, cy):
    b = bounds(p); return move(p, cx - (b[0] + b[2]) / 2, cy - (b[1] + b[3]) / 2)

def fit_text(s, style, cap, max_w, cx, baseline):
    t = text(s, style, cap); b = bounds(t); w = b[2] - b[0]
    if w > max_w: t = xform(t, max_w / w, 0, 0, 1, 0, 0); b = bounds(t); w = b[2] - b[0]
    return move(t, cx - (b[0] + b[2]) / 2, baseline)

# ---------------------------------------------------------------- glyph builders
# Everything below is drawn in a y-down design space and later fitted to the
# provider glyph's original bounding box.

def badge(label, style="fill", italic=False, w=200.0, h=99.0):
    face = "heavy" if italic else "bold"; cap = h * 0.58; base = h / 2 + cap / 2
    r = h * 0.17
    if style == "fill":
        return diff(rect(0, 0, w, h, r), fit_text(label, face, cap, w - h * 0.36, w / 2, base))
    t = h * 0.07
    return union(frame(0, 0, w, h, t, r), fit_text(label, face, cap * 0.96, w - h * 0.42, w / 2, base))

def square_num(label):
    return diff(rect(0, 0, 100, 100, 12), fit_text(label, "bold", 62, 84, 50, 81))

def circled(label, filled=False, cap=50):
    if filled: return diff(circle(50, 50, 50), fit_text(label, "bold", cap, 70, 50, 50 + cap / 2))
    return union(ring(50, 50, 50, 8), fit_text(label, "bold", cap, 62, 50, 50 + cap / 2))

def boxed(label, filled=False, cap=58, w=100):
    if filled: return diff(rect(0, 0, w, 100, 10), fit_text(label, "bold", cap, w - 24, w / 2, 50 + cap / 2))
    return union(frame(0, 0, w, 100, 7, 10), fit_text(label, "bold", cap, w - 26, w / 2, 50 + cap / 2))

def boxed_pic(pic, pad=16, filled=False, w=100):
    inner = fit_into(pic, pad, pad, w - 2 * pad, 100 - 2 * pad)
    if filled: return diff(rect(0, 0, w, 100, 10), inner)
    return union(frame(0, 0, w, 100, 7, 10), inner)

def circled_pic(pic, pad=24):
    return union(ring(50, 50, 50, 6), fit_into(pic, pad, pad, 100 - 2 * pad, 100 - 2 * pad))

def fit_into(p, x, y, w, h):
    b = bounds(p); s = min(w / (b[2] - b[0]), h / (b[3] - b[1]))
    p = xform(p, s, 0, 0, s, 0, 0); b = bounds(p)
    return move(p, x + (w - (b[2] - b[0])) / 2 - b[0], y + (h - (b[3] - b[1])) / 2 - b[1])

def crossed(p, size=100, w=8, h=100):
    """Prohibition: slash with a clear gap cut out of the artwork."""
    s = line([(6, 6), (size - 6, h - 6)], w)
    gap = line([(6, 6), (size - 6, h - 6)], w * 2.6)
    return union(diff(p, gap), s)

# ---------------------------------------------------------------- pictograms
def bicycle():
    wheel = lambda cx: ring(cx, 64, 19, 5.5)
    frame_ = line([(22, 64), (39, 37), (66, 37), (78, 64)], 5.5)
    frame2 = line([(22, 64), (50, 64), (66, 37)], 5.5)
    seat = line([(39, 37), (34, 25)], 5.5); seat2 = line([(28, 24), (41, 24)], 6)
    bars = line([(66, 37), (61, 23), (71, 23)], 5.5)
    crank = circle(50, 64, 5)
    return union(wheel(24), wheel(76), frame_, frame2, seat, seat2, bars, crank)

def bus_side(door=True):
    body = rect(0, 10, 100, 44, 7)
    wins = [rect(8 + i * 18, 17, 14, 14, 2) for i in range(5)]
    b = diff(body, *wins, rect(4, 44, 92, 2.5))
    wheels = union(circle(22, 56, 10), circle(78, 56, 10))
    hubs = union(circle(22, 56, 4), circle(78, 56, 4))
    b = diff(b, circle(22, 56, 13), circle(78, 56, 13))
    return diff(union(b, wheels), hubs)

def bus_front():
    body = rect(10, 4, 80, 76, 12)
    b = diff(body, rect(18, 14, 64, 34, 5), circle(25, 64, 5), circle(75, 64, 5), rect(36, 60, 28, 6, 3))
    mirrors = union(line([(10, 18), (3, 22), (3, 34)], 5), line([(90, 18), (97, 22), (97, 34)], 5))
    wheels = union(rect(16, 76, 16, 14, 4), rect(68, 76, 16, 14, 4))
    return union(b, mirrors, wheels)

def train_front():
    body = P(); body.moveTo(28, 6); body.lineTo(72, 6); body.cubicTo(84, 6, 88, 14, 89, 26)
    body.lineTo(92, 74); body.cubicTo(92, 82, 87, 86, 80, 86); body.lineTo(20, 86)
    body.cubicTo(13, 86, 8, 82, 8, 74); body.lineTo(11, 26); body.cubicTo(12, 14, 16, 6, 28, 6); body.close()
    glass = P(); glass.moveTo(25, 16); glass.lineTo(75, 16); glass.lineTo(80, 46); glass.lineTo(20, 46); glass.close()
    b = diff(body, rect(14, 14, 72, 34, 8) if False else glass, circle(24, 66, 6), circle(76, 66, 6), rect(40, 64, 20, 5, 2.5))
    rails = union(line([(14, 96), (86, 96)], 5, "butt"), line([(22, 86), (16, 96)], 5), line([(78, 86), (84, 96)], 5))
    return union(b, rails)

def tram_front():
    body = rect(14, 22, 72, 66, 16)
    b = diff(body, rect(22, 30, 56, 28, 9), circle(28, 74, 5), circle(72, 74, 5))
    panto = union(line([(36, 22), (56, 6)], 5), line([(56, 6), (44, 4), (70, 4)], 5))
    return union(b, panto, line([(18, 96), (82, 96)], 5, "butt"))

def ship():
    hull = P(); hull.moveTo(2, 56); hull.lineTo(98, 56); hull.lineTo(84, 78); hull.lineTo(14, 78); hull.close()
    deck = rect(18, 38, 60, 16, 3); bridge = rect(32, 22, 34, 14, 3); funnel = rect(56, 8, 10, 14, 2)
    s = diff(union(hull, deck, bridge, funnel), *[rect(24 + i * 12, 42, 7, 6, 1) for i in range(4)])
    waves = line([(2, 90), (14, 84), (26, 90), (38, 84), (50, 90), (62, 84), (74, 90), (86, 84), (98, 90)], 5)
    return union(s, waves)

def plane():
    p = poly((48, 4), (54, 4), (58, 38), (98, 58), (98, 66), (58, 56), (56, 82), (68, 92), (68, 98), (51, 92),
             (34, 98), (34, 92), (46, 82), (44, 56), (4, 66), (4, 58), (44, 38))
    return union(p, rect(45, 2, 12, 40, 6))

def wheelchair():
    head = circle(40, 10, 9)
    body = line([(38, 24), (40, 56), (68, 56), (78, 82)], 9, "round")
    arm = line([(39, 38), (62, 38)], 8)
    wheel = arc(42, 66, 26, 140, 400, 7)
    return union(head, body, arm, diff(wheel, circle(42, 66, 14)))

def bed():
    base = union(rect(0, 50, 100, 12, 2), rect(0, 20, 9, 60, 2), rect(91, 38, 9, 42, 2))
    pillow = rect(14, 34, 22, 14, 6); mattress = rect(40, 36, 51, 12, 4)
    return union(base, pillow, mattress)

def couchette():
    return union(rect(0, 60, 100, 10, 2), rect(0, 20, 8, 60, 2), rect(92, 52, 8, 28, 2),
                 rect(12, 48, 20, 10, 5), rect(36, 48, 56, 10, 3),
                 xform(rect(10, 22, 82, 8, 3), 1, 0, -0.12, 1, 4, 0))

def seat():
    back = line([(28, 8), (36, 60)], 18); head = rect(20, 0, 20, 14, 6)
    cushion = rect(30, 54, 56, 18, 8); arm = line([(46, 46), (76, 46)], 7)
    leg = union(rect(52, 70, 12, 20), rect(34, 88, 48, 8, 4))
    return union(back, head, cushion, arm, leg)

def cutlery():
    fork = union(rect(14, 4, 5, 32, 2), rect(24, 4, 5, 32, 2), rect(34, 4, 5, 32, 2),
                 rect(14, 30, 25, 12, 6), rect(22, 34, 9, 62, 4.5))
    knife = union(P(), rect(62, 50, 10, 46, 5))
    blade = P(); blade.moveTo(62, 4); blade.cubicTo(80, 10, 82, 40, 74, 56); blade.lineTo(62, 56); blade.close()
    return union(fork, knife, blade)

def glass():
    bowl = P(); bowl.moveTo(20, 4); bowl.lineTo(80, 4); bowl.cubicTo(82, 36, 66, 52, 50, 52)
    bowl.cubicTo(34, 52, 18, 36, 20, 4); bowl.close()
    return union(bowl, rect(46, 50, 8, 36), rect(26, 86, 48, 8, 4))

def cup():
    body = P(); body.moveTo(8, 30); body.lineTo(70, 30); body.lineTo(66, 66)
    body.cubicTo(64, 76, 58, 80, 50, 80); body.lineTo(28, 80); body.cubicTo(20, 80, 14, 76, 12, 66); body.close()
    handle = arc(72, 48, 12, -80, 110, 7)
    steam = union(arc(28, 16, 6, 90, 270, 4), arc(48, 16, 6, 90, 270, 4))
    return union(body, handle, rect(2, 86, 82, 7, 3.5))

def suitcase():
    return union(diff(rect(4, 26, 92, 64, 9), rect(4, 52, 92, 4)), frame(32, 10, 36, 22, 7, 5))

def phone():
    h = P(); h.moveTo(28, 8); h.lineTo(42, 8); h.lineTo(46, 30); h.lineTo(38, 38); h.cubicTo(42, 52, 50, 60, 62, 66)
    h.lineTo(70, 58); h.lineTo(92, 62); h.lineTo(92, 78); h.cubicTo(92, 86, 86, 92, 78, 92)
    h.cubicTo(44, 90, 12, 58, 10, 24); h.cubicTo(10, 14, 18, 8, 28, 8); h.close(); return h

def cigarette():
    c = union(rect(6, 54, 66, 14, 2), rect(76, 54, 8, 14, 1), rect(88, 54, 6, 14, 1))
    smoke = line([(84, 46), (78, 36), (86, 26), (80, 16)], 5)
    return union(c, smoke)

def no_smoking():
    return union(diff(fit_into(cigarette(), 18, 18, 64, 64), line([(18, 18), (82, 82)], 16)),
                 ring(50, 50, 50, 8), line([(17, 17), (83, 83)], 8, "butt"))

def envelope():
    return union(frame(0, 14, 100, 72, 7, 6), line([(4, 18), (50, 54), (96, 18)], 7, "round", "miter"))

def hammers():
    def ham():
        return union(rect(46, 10, 8, 80, 3), rect(30, 6, 40, 14, 3))
    return union(rotate(ham(), 45, 50, 50), rotate(ham(), -45, 50, 50))

def asterisk(heavy=False):
    w = 14 if heavy else 9
    return union(*[rotate(line([(50, 10), (50, 90)], w), a, 50, 50) for a in (0, 60, 120)])

def snowflake():
    arm = union(line([(50, 6), (50, 94)], 7), line([(38, 14), (50, 24), (62, 14)], 6),
                line([(38, 86), (50, 76), (62, 86)], 6))
    return union(*[rotate(arm, a, 50, 50) for a in (0, 60, 120)])

def sun():
    rays = [rotate(line([(50, 4), (50, 16)], 6), a, 50, 50) for a in range(0, 360, 45)]
    return union(circle(50, 50, 22), *rays)

def building():
    b = union(rect(8, 30, 84, 66), poly((2, 32), (50, 6), (98, 32)))
    wins = [rect(18 + c * 24, 42 + r * 22, 16, 12, 1) for c in range(3) for r in range(2)]
    return diff(b, *wins[:4], wins[5], rect(42, 66, 16, 30, 2), wins[4])

def star():
    pts = []
    for i in range(10):
        r = 50 if i % 2 == 0 else 21
        a = math.radians(-90 + i * 36); pts.append((50 + r * math.cos(a), 52 + r * math.sin(a)))
    return poly(*pts)

def warning(filled=True):
    tri = P(); tri.moveTo(50, 4); tri.lineTo(98, 92); tri.lineTo(2, 92); tri.close()
    mark = union(rect(45, 34, 10, 32, 5), circle(50, 78, 6))
    if filled: return diff(tri, mark)
    inner = P(); inner.moveTo(50, 22); inner.lineTo(84, 84); inner.lineTo(16, 84); inner.close()
    return union(diff(tri, inner), mark)

def ticket_machine(kind):
    m = diff(frame(0, 0, 70, 100, 6, 6), P())
    if kind == "keypad":
        keys = [rect(20 + c * 11, 30 + r * 11, 7, 7, 1) for c in range(3) for r in range(4)]
        return union(m, rect(16, 12, 38, 10, 2), *keys)
    if kind == "screen":
        return union(m, rect(14, 14, 42, 28, 3), rect(14, 52, 18, 6, 2), rect(38, 52, 18, 6, 2), rect(20, 76, 30, 6, 3))
    if kind == "tickets":
        return union(m, rect(14, 14, 42, 18, 2), rect(22, 44, 26, 34, 2), rect(28, 50, 26, 34, 2))
    return union(m, rect(14, 14, 42, 18, 2), diff(rect(18, 44, 34, 40, 4), rect(26, 52, 18, 24, 2)))

def location_pin():
    p = P(); p.moveTo(50, 98); p.cubicTo(30, 70, 14, 54, 14, 36); p.cubicTo(14, 16, 30, 2, 50, 2)
    p.cubicTo(70, 2, 86, 16, 86, 36); p.cubicTo(86, 54, 70, 70, 50, 98); p.close()
    return diff(p, circle(50, 36, 14))

def stroller():
    adult = union(circle(30, 10, 9), line([(30, 24), (34, 54)], 12), line([(34, 54), (24, 94)], 9),
                  line([(34, 54), (46, 94)], 9), line([(31, 30), (54, 46)], 7))
    pram = P(); pram.moveTo(54, 46); pram.lineTo(96, 46); pram.cubicTo(96, 64, 86, 72, 74, 72)
    pram.lineTo(64, 72); pram.cubicTo(58, 72, 54, 66, 54, 60); pram.close()
    hood = arc(80, 46, 16, 180, 270, 7, "butt")
    return union(adult, pram, hood, ring(62, 86, 8, 4.5), ring(88, 86, 8, 4.5))

def bear():
    head = union(circle(50, 56, 38), circle(20, 22, 15), circle(80, 22, 15))
    face = union(circle(36, 50, 7), circle(64, 50, 7), ellipse(50, 72, 15, 11))
    return union(diff(head, face, circle(20, 22, 6), circle(80, 22, 6)), ellipse(50, 68, 6, 4))

def hungary():
    # Same public-domain national outline as the pass artwork (pass_hungary.xml), filled.
    import re
    import xml.etree.ElementTree as ET
    root = ET.parse(os.path.join(APP, "res", "drawable", "pass_hungary.xml")).getroot()
    A = "{http://schemas.android.com/apk/res/android}"
    from fontTools.svgLib.path import parse_path
    out = P()
    for e in root.iter("path"): parse_path(e.get(A + "pathData"), out.getPen())
    return union(out)

def country_pass():
    hu = fit_into(hungary(), 0, 0, 100, 60)
    ticket = diff(rect(56, 40, 40, 56, 5), rect(64, 52, 24, 5, 2), rect(64, 64, 24, 5, 2), rect(64, 76, 14, 5, 2))
    return union(diff(hu, rect(50, 34, 52, 68, 8)), ticket)

def funicular():
    track = line([(4, 92), (96, 52)], 6)
    car = lambda x, y: diff(xform(rect(0, 0, 26, 24, 4), 1, -0.43, 0, 1, x, y), xform(rect(5, 4, 16, 9, 2), 1, -0.43, 0, 1, x, y))
    return union(track, car(12, 56), car(42, 43), car(72, 30))

def cogs(): return funicular()

def train_small(): return train_front()

def arrow(dir="right", w=100, h=60, t=20):
    p = poly((0, h / 2 - t / 2), (w - h / 2, h / 2 - t / 2), (w - h / 2, 0), (w, h / 2), (w - h / 2, h),
             (w - h / 2, h / 2 + t / 2), (0, h / 2 + t / 2))
    return {"right": p, "left": mirror(p, w / 2)}[dir]

def thin_arrow(dir="down", l=100):
    shaft = line([(10, 0), (10, l - 18)], 5, "butt"); head = poly((0, l - 22), (20, l - 22), (10, l))
    p = union(shaft, head)
    return p if dir == "down" else xform(p, 1, 0, 0, -1, 0, l)

def elbow(path_pts, head_at_end=True):
    shaft = line(path_pts, 10, "butt", "miter")
    (x0, y0), (x1, y1) = path_pts[-2], path_pts[-1]
    a = math.atan2(y1 - y0, x1 - x0); ux, uy = math.cos(a), math.sin(a)
    tip = (x1 + ux * 16, y1 + uy * 16)
    head = poly(tip, (x1 - uy * 14, y1 + ux * 14), (x1 + uy * 14, y1 - ux * 14))
    return union(shaft, head)

def combo(top, bottom):
    return union(fit_into(top, 10, 0, 80, 44), rect(0, 48, 100, 4), fit_into(bottom, 10, 56, 80, 44))

def wagon(label=None, car=False):
    body = frame(6, 12, 88, 48, 6, 5)
    if label: body = union(body, fit_text(label, "bold", 30, 70, 50, 51))
    if car:
        c = union(rect(20, 30, 60, 16, 6), poly((32, 30), (40, 18), (62, 18), (70, 30)))
        c = diff(c, poly((38, 28), (43, 21), (50, 21), (50, 28)), poly((54, 28), (54, 21), (60, 21), (65, 28)), circle(32, 46, 7), circle(68, 46, 7))
        body = union(rect(2, 52, 96, 8, 2), c, circle(32, 46, 5), circle(68, 46, 5), rect(2, 24, 6, 34), rect(92, 24, 6, 34))
    wheels = union(circle(18, 70, 7), circle(32, 70, 7), circle(68, 70, 7), circle(82, 70, 7), rect(0, 60, 100, 4))
    return union(body, wheels)

def vr_bar(w): return rect(50 - w / 2, 0, w, 100)

def h_shape(double=False):
    p = union(rect(0, 0, 26, 100), rect(74, 0, 26, 100), rect(0, 40, 100, 20))
    return union(p, rect(0, 66, 100, 14)) if double else p

def hourglass():
    return union(poly((10, 4), (90, 4), (50, 50)), poly((10, 96), (90, 96), (50, 50)))

def wifi_pill():
    t = fit_text("WiFi", "bold", 52, 80, 50, 76)
    return diff(rect(0, 10, 100, 80, 40), t)

def bus_on_demand(sign):
    b = bus_side()
    mark = rect(6, 2, 20, 36, 3) if sign == "phone" else circle(16, 20, 13)
    holes = rect(10, 8, 12, 22, 2) if sign == "phone" else move(phone(), 0, 0)
    sym = diff(mark, holes) if sign == "phone" else diff(mark, fit_into(phone(), 8, 12, 16, 16))
    return union(diff(b, rect(2, 0, 28, 42)), sym)

def dashed_band(inner):
    dashes = union(*[rect(x, 0, 10, 5) for x in range(0, 100, 16)], *[rect(x, 67, 10, 5) for x in range(0, 100, 16)])
    return union(fit_into(inner, 0, 10, 100, 52), dashes)

def replacement_dash(): return dashed_band(bus_side())

def train_to_bus():
    return union(fit_into(train_front(), 0, 20, 30, 60), fit_into(arrow("right"), 38, 40, 24, 20),
                 fit_into(bus_front(), 68, 18, 32, 64))

def train_bus_stack():
    return union(fit_into(train_front(), 0, 30, 30, 50), fit_into(bus_front(), 26, 0, 74, 100))

def no_entry(): return diff(circle(50, 50, 50), rect(16, 40, 68, 20, 3))

def car_carrier(): return wagon(car=True)

def steam_loco():
    boiler = rect(30, 30, 50, 28, 6); cab = rect(78, 10, 22, 48, 3); chimney = rect(36, 4, 12, 28, 2)
    dome = circle(60, 30, 7); front = rect(20, 42, 14, 16, 2); frame_ = rect(18, 56, 82, 8)
    wheels = union(circle(34, 74, 11), circle(60, 74, 11), circle(86, 76, 9))
    cow = poly((18, 56), (4, 72), (18, 72))
    smoke = union(circle(16, 10, 8), circle(4, 6, 5))
    return diff(union(boiler, cab, chimney, dome, front, frame_, wheels, cow, smoke), rect(84, 16, 10, 14, 2))

def euro_amp():  # ¢-like and odd glyphs fall back to lettering
    return text("¢", "bold", 80)

def h_slash():
    return union(xform(h_shape(), 0.7, 0, 0, 1, 15, 0), line([(4, 50), (24, 20)], 8), line([(4, 50), (24, 80)], 8),
                 line([(96, 0), (70, 100)], 8))

def return_arrow():
    return union(line([(30, 30), (80, 30)], 8, "butt"), arc(80, 56, 26, -90, 90, 8, "butt"),
                 line([(80, 82), (20, 82)], 8, "butt"), poly((34, 12), (34, 48), (12, 30)))

def caret(): return line([(6, 86), (50, 30), (94, 86)], 16, "butt", "miter")

def info_box():
    return union(frame(0, 0, 100, 100, 7, 8), circle(50, 24, 8), rect(44, 40, 12, 40), rect(34, 74, 32, 8), rect(34, 40, 16, 8))

def r_diamond():
    d = poly((50, 2), (98, 50), (50, 98), (2, 50))
    inner = poly((50, 10), (90, 50), (50, 90), (10, 50))
    return union(diff(d, inner), fit_text("R", "bold", 40, 40, 50, 70))

# ---------------------------------------------------------------- code table
BLUE_LINES = []
def spec_table():
    T = {}
    nums = [str(n) for n in range(1, 100)] + ["0"]
    for i, c in enumerate(list(range(33, 127)) + list(range(161, 167))): T[c] = lambda s=nums[i]: square_num(s)
    pics = {
        167: lambda: rect(0, 0, 100, 100, 6),
        168: lambda: diff(rect(0, 0, 100, 100, 12), circle(50, 50, 24)),
        169: lambda: diff(circle(50, 50, 50), line([(32, 32), (68, 68)], 12), line([(68, 32), (32, 68)], 12)),
        170: hammers, 171: lambda: circled_pic(hammers(), 20), 172: lambda: boxed_pic(hammers(), 18),
        174: lambda: frame(0, 0, 100, 100, 7, 6),
        175: lambda: union(rect(44, 20, 12, 80, 4), rect(20, 4, 60, 18, 5)),
        176: lambda: union(ring(50, 50, 50, 8), circle(50, 50, 12)),
        177: lambda: union(ring(50, 50, 50, 8), rect(46, 2, 8, 96), rect(2, 46, 96, 8)),
        178: lambda: union(frame(0, 0, 100, 100, 7, 6), rect(46, 2, 8, 96), rect(2, 46, 96, 8)),
        179: lambda: union(rect(42, 0, 16, 100), rect(0, 42, 100, 16)),
        190: lambda: text("1+", "bold", 100),
        191: cutlery, 192: glass, 193: cup,
        194: lambda: union(fit_into(cutlery(), 0, 0, 42, 100), fit_into(cup(), 48, 30, 52, 60)),
        195: lambda: text("R", "bold", 100),
        196: lambda: circled("R"), 197: lambda: boxed("R"),
        198: no_entry, 199: lambda: union(ring(50, 50, 50, 8), rect(45, 4, 10, 92)),
        200: lambda: union(ring(50, 50, 50, 8), poly((50, 22), (76, 70), (24, 70))),
        201: lambda: boxed("R", filled=True), 202: lambda: circled("R", filled=True),
        203: bed, 204: couchette, 205: seat,
        206: lambda: union(seat(), fit_into(text("+1", "bold", 60), 62, 0, 38, 36)),
        207: lambda: wagon("1 2"), 208: car_carrier, 209: lambda: wagon("1"), 210: lambda: wagon("2"), 211: wagon,
        212: lambda: boxed_pic(bicycle(), 12, w=150), 213: bicycle,
        214: lambda: crossed(boxed_pic(bicycle(), 12, w=150), 150),
        215: bus_side, 216: lambda: union(bus_side(), P()),
        217: steam_loco, 218: ship, 219: lambda: rotate(plane(), 90, 50, 50), 220: suitcase,
        221: lambda: text("1", "bold", 100), 222: lambda: text("2", "bold", 100),
        223: wheelchair, 224: lambda: boxed_pic(phone(), 18), 225: no_smoking,
        226: lambda: crossed(boxed_pic(cigarette(), 16)), 227: lambda: boxed("KN", cap=46),
        228: envelope, 239: lambda: boxed("P"),
        240: lambda: poly((50, 4), (98, 92), (2, 92)), 241: lambda: circle(50, 50, 50), 242: lambda: rect(0, 0, 100, 100, 4),
        243: lambda: ring(50, 50, 50, 16), 244: lambda: diff(circle(50, 50, 50), rect(50, 0, 50, 100)),
        245: lambda: diff(circle(50, 50, 50), rect(0, 0, 50, 100)),
        246: lambda: poly((50, 0), (100, 50), (50, 100), (0, 50)), 247: lambda: poly((2, 8), (98, 8), (50, 96)),
        248: lambda: poly((50, 4), (98, 92), (2, 92)), 249: lambda: diff(circle(50, 50, 50), ring(50, 50, 30, 10)),
        250: lambda: arrow("right"), 251: lambda: arrow("left"),
        252: lambda: elbow([(6, 0), (6, 60), (80, 60)]), 253: lambda: elbow([(0, 6), (70, 6), (70, 70)]),
        254: lambda: elbow([(90, 0), (90, 56), (16, 56)]), 255: lambda: elbow([(80, 80), (80, 20), (16, 20)]),
        256: lambda: elbow([(100, 6), (20, 6), (20, 70)]), 257: lambda: elbow([(0, 80), (70, 80), (70, 16)]),
        258: lambda: elbow([(100, 80), (20, 80), (20, 16)]), 259: lambda: elbow([(6, 80), (6, 20), (80, 20)]),
        260: lambda: thin_arrow("down"), 261: lambda: thin_arrow("up"), 262: lambda: thin_arrow("down"),
        263: lambda: thin_arrow("up"), 264: lambda: rotate(thin_arrow("down"), 45, 10, 50),
        265: lambda: line([(20, 0), (10, 20), (20, 45), (30, 70), (20, 100)], 4),
        266: lambda: vr_bar(6), 267: lambda: vr_bar(5), 268: lambda: vr_bar(6), 269: lambda: vr_bar(3),
        270: lambda: vr_bar(10), 271: lambda: vr_bar(5), 272: lambda: rect(0, 0, 100, 10),
        273: lambda: union(ring(50, 50, 50, 6), inter(hourglass(), circle(50, 50, 46))),
        274: lambda: union(frame(0, 0, 100, 100, 6, 4), scale(hourglass(), 0.9, 50, 50)),
        275: lambda: union(frame(0, 0, 100, 100, 6, 4), poly((4, 96), (96, 4), (96, 96))),
        276: asterisk, 277: lambda: boxed_pic(asterisk(), 14), 278: lambda: asterisk(True),
        279: lambda: union(arrow("right", 100, 40, 14), mirror(arrow("right", 50, 40, 14), 25)),
        280: lambda: text("F", "bold", 100), 281: lambda: boxed("1", cap=64), 282: lambda: boxed("2", cap=64),
        283: lambda: union(boxed("1", cap=64), rect(14, 46, 72, 7)), 284: lambda: union(boxed("2", cap=64), rect(14, 46, 72, 7)),
        285: lambda: poly((0, 100), (100, 0), (100, 100)),
        286: lambda: diff(poly((2, 4), (98, 4), (50, 96)), poly((18, 14), (82, 14), (50, 76))),
        287: lambda: boxed_pic(union(arrow("right", 100, 40, 10), mirror(arrow("right", 50, 40, 10), 25)), 16),
        288: lambda: union(frame(0, 0, 100, 100, 7, 6), line([(20, 20), (80, 80)], 8), line([(80, 20), (20, 80)], 8)),
        289: h_shape, 290: lambda: h_shape(True), 291: return_arrow, 292: lambda: ring(50, 50, 50, 14), 293: caret,
        295: lambda: crossed(boxed_pic(union(rect(20, 30, 54, 26, 10), circle(80, 30, 12), rect(24, 50, 8, 30), rect(62, 50, 8, 30)), 14)),
        298: lambda: circled("S", filled=True, cap=64),
        316: lambda: bus_on_demand("phone"), 317: lambda: bus_on_demand("handset"),
        319: snowflake, 320: sun, 321: building, 324: lambda: circled_pic(snowflake(), 16),
        325: lambda: circled("D"), 327: lambda: circled("H", filled=True), 328: lambda: circled("M", filled=True),
        329: lambda: text("@", "bold", 90), 331: lambda: boxed_pic(wheelchair(), 16),
        332: lambda: union(frame(0, 0, 180, 100, 7, 10), fit_into(bicycle(), 14, 16, 100, 68), fit_text("R", "bold", 58, 40, 145, 79)),
        333: lambda: boxed("Wi-Fi", cap=50, w=180),
        334: lambda: union(diff(fit_into(bicycle(), 18, 18, 64, 64), line([(18, 18), (82, 82)], 16)), ring(50, 50, 50, 7), line([(16, 16), (84, 84)], 7, "butt")),
        336: lambda: union(frame(0, 0, 180, 100, 7, 10), fit_into(bicycle(), 14, 16, 100, 68), fit_into(no_entry(), 124, 28, 44, 44)),
        340: star, 341: lambda: text("¢", "bold", 90), 342: h_slash, 346: lambda: text("§", "bold", 90),
        347: lambda: combo(wagon(), ship()), 348: lambda: combo(wagon(), bus_side()), 349: lambda: combo(ship(), bus_side()),
        406: info_box, 430: warning, 431: bicycle,
        432: lambda: union(frame(0, 0, 180, 100, 7, 10), fit_into(bicycle(), 14, 16, 100, 68), fit_text("R", "bold", 58, 40, 145, 79)),
        433: lambda: boxed_pic(bicycle(), 14, filled=True, w=130),
        434: lambda: union(frame(0, 0, 200, 100, 7, 10), diff(rect(14, 14, 110, 72, 6), fit_into(bicycle(), 22, 20, 94, 60)), fit_text("R", "bold", 58, 40, 162, 79)),
        435: lambda: warning(False),
        436: lambda: ticket_machine("keypad"), 437: lambda: ticket_machine("tickets"),
        438: lambda: ticket_machine("screen"), 439: lambda: ticket_machine("card"),
        447: r_diamond, 536: replacement_dash,
        551: lambda: circled_pic(train_front()), 552: lambda: circled_pic(tram_front()), 553: lambda: circled_pic(bus_front()),
        554: lambda: circled_pic(ship()), 555: location_pin, 556: lambda: circle(50, 50, 50), 557: country_pass,
        558: wheelchair, 559: wifi_pill, 560: lambda: union(ring(50, 50, 50, 6), fit_text("100", "bold", 38, 72, 50, 69)),
        589: stroller, 590: lambda: circled_pic(stroller(), 20), 592: bear,
        598: lambda: union(poly((0, 50), (44, 10), (44, 90)), poly((100, 50), (56, 10), (56, 90))),
        599: lambda: poly((0, 50), (80, 0), (80, 100)), 600: lambda: poly((100, 50), (20, 0), (20, 100)),
        602: train_bus_stack, 603: lambda: dashed_band(train_to_bus()),
        606: funicular, 607: lambda: circled_pic(funicular(), 20),
        2163: lambda: arrow("left", 100, 80, 26), 2164: lambda: arrow("right", 100, 80, 26),
    }
    T.update(pics)
    for c, s in {180: "1", 181: "2", 182: "3", 183: "4", 184: "5", 185: "6", 186: "7", 187: "A", 188: "B", 189: "C"}.items():
        T[c] = lambda s=s: circled(s)
    # Plain lettering, operator names included (no third-party logo artwork).
    plain = {296: "NR", 318: "MÁV", 337: "E", 338: "H", 339: "O", 343: "V", 344: "B", 345: "G", 404: "1.R", 405: "2.R",
             407: "IRT", 409: "N/IRT", 410: "NRT", 299: "Westbahn", 561: "MÁV", 562: "MÁV", 564: "GYSEV", 565: "HÉV",
             566: "HÉV", 567: "VOLÁN", 568: "VOLÁN", 591: "AP", 593: "eurostar", 596: "leo express", 597: "MÁVBUSZ",
             601: "OUIGO"}
    for c, s in plain.items(): T[c] = lambda s=s: text(s, "bold", 100)
    italic_plain = {229: "EC", 230: "ICE", 231: "IC", 232: "SC", 233: "EN", 234: "D", 235: "Ex", 236: "ICR", 237: "IP",
                    238: "S", 297: "RJ", 322: "ER", 323: "rj", 326: "REx", 330: "IR", 415: "nj", 429: "rjx",
                    610: "BEX", 615: "ECD", 617: "ECE", 620: "FA", 622: "FB", 624: "FL", 626: "FR", 628: "FUN",
                    631: "GEX", 634: "HST", 637: "ICL", 642: "L", 648: "P", 650: "PE", 654: "RER", 656: "RT",
                    660: "TER", 662: "TGV", 664: "TRN", 668: "VAE"}
    for c, s in italic_plain.items(): T[c] = lambda s=s: text(s, "heavy", 100)
    italic_badge = {467: "EC", 468: "EIC", 469: "EIP", 470: "EN", 471: "ER", 472: "Ex", 473: "Gy", 474: "IC", 475: "ICE",
                    476: "ICN", 477: "IR", 478: "nj", 479: "RE", 480: "rj", 481: "rjx", 482: "S", 483: "SC", 484: "Sz",
                    569: "ICR", 570: "REx", 571: "cjx", 572: "R", 573: "D", 574: "RB", 575: "IRE", 576: "Sp", 577: "Os",
                    578: "RR", 579: "E", 580: "B", 581: "Pu", 582: "TLK", 611: "BEX", 616: "ECD", 618: "ECE", 621: "FA",
                    623: "FB", 625: "FL", 627: "FR", 629: "FUN", 632: "GEX", 635: "HST", 638: "ICL", 643: "L", 649: "P",
                    651: "PE", 655: "RER", 657: "RT", 661: "TER", 663: "TGV", 665: "TRN", 669: "VAE"}
    for c, s in italic_badge.items(): T[c] = lambda s=s: badge(s, "fill", True)
    outline = {380: "X10", 381: "Z20", 382: "Z25", 383: "Z30", 384: "Z50", 385: "Z60", 386: "Z70", 397: "Z57",
               403: "1.R", 408: "RG", 417: "Z72", 418: "Z440", 448: "Z80", 674: "X71", 675: "X506", 676: "Z506"}
    outline.update({c: s for c, s in zip(range(517, 536), ["Z10", "Z12", "Z21", "Z29", "Z34", "Z35", "Z36", "Z40",
                    "Z42", "Z43", "Z44", "Z51", "Z71", "Z73", "Z76", "Z77", "Z87", "Z300", "Z329"])})
    outline.update({547: "Z74", 548: "Z85", 549: "Z140", 550: "Z770"})
    for c, s in outline.items(): T[c] = lambda s=s: badge(s, "outline")
    filled = {294: "IR25", 335: "S10", 395: "", 449: "1", 594: "G100", 595: "G506", 604: "S15", 605: "S447"}
    seq = lambda start, labels: dict(zip(range(start, start + len(labels)), labels))
    filled.update(seq(350, "S12 S20 S21 S25 S30 S34 S35 S36 S40 S42 S50 S51 S60 S70 S71 S72 S80 S104 S150 S210 S220 S250 S431 S432 S440 S450 S750 S780 S790 S820".split()))
    filled.update(seq(387, "G10 G25 G42 G43 G60 G70 G71 G820".split()))
    filled.update({396: "S57", 398: "G72", 399: "G40", 400: "G30", 401: "G44", 402: "S41", 411: "G50", 412: "G80",
                   413: "S87", 414: "S506"})
    filled.update(seq(419, "S430 S300 S316 S326 S329 S336 S350 G35 G316 G326".split()))
    filled.update(seq(440, "H5 H6 H7 H8 H9 G20 S76".split()))
    filled.update(seq(450, "H5A H5E H6A H6E H7A H7E H8A H8E H8Y H8SZ H9A H9E 1A 1B 1C 1E 1H".split()))
    filled.update(seq(485, "1S 1T 11 40 G12 G21 G29 G34 G36 G51 G57 G73 G76 G77 G87 G300 G329 G336 G440 S29 S43 S44 S73 S77 S100 S101 S105 S110 S225 S400 S433 S785".split()))
    filled.update(seq(537, "G74 G85 G140 G770 IR85 IR87 S74 S85 S140 S770".split()))
    filled.update(seq(583, "IR14 IR16 IR30 IR32 IR46 IR50".split()))
    filled.update({608: "A", 609: "B", 612: "C", 613: "D", 614: "E", 619: "F", 630: "G", 633: "H", 636: "I", 639: "J",
                   640: "K", 641: "L", 644: "M", 645: "N", 646: "O", 647: "P", 652: "Q", 653: "R", 658: "S", 659: "T",
                   666: "U", 667: "V", 670: "W", 671: "X", 672: "Y", 673: "Z"})
    filled.update(seq(677, "S889 S890 H5B H5Y H6B H8B H8C H67 IR10 IR29 IR67 IR100 IR176 S223 S438 S503 S505 S514 S620 S650 S980".split()))
    for c, s in filled.items(): T[c] = lambda s=s: badge(s, "fill") if s else rect(0, 0, 200, 99, 17)
    return T

# ---------------------------------------------------------------- font build
def to_font_units(p, box):
    """Fit a y-down design path into a font-unit bbox (y-up), preserving aspect, centred."""
    x0, y0, x1, y1 = box; b = bounds(p)
    bw, bh = b[2] - b[0], b[3] - b[1]; s = min((x1 - x0) / bw, (y1 - y0) / bh)
    ox = x0 + ((x1 - x0) - bw * s) / 2; oy = y0 + ((y1 - y0) - bh * s) / 2
    return xform(p, s, 0, 0, -s, ox - b[0] * s, oy + b[3] * s)

def text_to_font_units(p, box):
    """Text keeps its natural width: height fixed to the original box, advance grows if needed."""
    x0, y0, x1, y1 = box; b = bounds(p); s = (y1 - y0) / (b[3] - b[1])
    return xform(p, s, 0, 0, -s, x0 - b[0] * s, y0 + b[3] * s)

def build_font(out_path):
    T = spec_table()
    missing = [c for c, m in METRICS.items() if len(m) == 5 and c not in T]
    if missing: raise SystemExit(f"no artwork for codes: {missing}")
    order = [".notdef", "space"]; cmap = {32: "space"}; glyphs = {}; metrics = {}
    empty = TTGlyphPen(None).glyph()
    glyphs[".notdef"] = empty; metrics[".notdef"] = (500, 0)
    glyphs["space"] = empty; metrics["space"] = (300, 0)
    for code, m in sorted(METRICS.items()):
        name = f"u{code:04X}"; order.append(name); cmap[code] = name
        if len(m) == 1:
            glyphs[name] = empty; metrics[name] = (m[0], 0); continue
        x0, y0, x1, y1, adv = m; rsb = adv - x1
        art = T[code]()
        b = bounds(art)
        natural = (b[2] - b[0]) / (b[3] - b[1]) > 1.6 and (x1 - x0) / (y1 - y0) > 1.6 and code not in (272, 279)
        fp = text_to_font_units(art, (x0, y0, x1, y1)) if natural else to_font_units(art, (x0, y0, x1, y1))
        fp = union(fp)
        pen = TTGlyphPen(None)
        fp.draw(Cu2QuPen(ReverseContourPen(pen), 1.0, reverse_direction=False))
        g = pen.glyph(); g.recalcBounds(None)
        fb = bounds(fp); glyphs[name] = g
        metrics[name] = (max(adv, round(fb[2] + rsb)), round(fb[0]))
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(order); fb.setupCharacterMap(cmap); fb.setupGlyf(glyphs); fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=930, descent=-251)
    fb.setupNameTable({"familyName": "MenetrendGO Pictograms", "styleName": "Regular",
                       "copyright": "Pictograms (c) MenetrendGO contributors. Lettering derived from Barlow, "
                                    "Copyright 2017 The Barlow Project Authors (https://github.com/jpt/barlow)",
                       "licenseDescription": "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
                       "licenseInfoURL": "https://openfontlicense.org"})
    fb.setupOS2(sTypoAscender=930, sTypoDescender=-251, usWinAscent=930, usWinDescent=251, sCapHeight=700, sxHeight=514)
    fb.setupPost(); fb.save(out_path)
    return len(order)

# ---------------------------------------------------------------- mode pictograms
MODES = {  # file, map key, background, [(glyph builder, colour)]
    "rail": ("RAIL", "#003A7E"), "coach": ("COACH", "#FDC300"), "local_bus": ("BUS", "#009EE3"),
    "hev": ("SUBURBAN_RAILWAY", "#005E3B"), "tramtrain": ("TRAMTRAIN", "#F29101"),
    "replacement_bus": ("RAIL_REPLACEMENT_BUS", "#C4146E"),
}

def mode_train():
    """Mainline train cab: tapered nose, wide windscreen, lamps, rails."""
    body = P(); body.moveTo(11.5, 6.2); body.lineTo(20.5, 6.2); body.cubicTo(23.2, 6.2, 24.6, 7.9, 24.9, 10.6)
    body.lineTo(25.6, 22.6); body.cubicTo(25.7, 24.4, 24.6, 25.4, 22.9, 25.4); body.lineTo(9.1, 25.4)
    body.cubicTo(7.4, 25.4, 6.3, 24.4, 6.4, 22.6); body.lineTo(7.1, 10.6); body.cubicTo(7.4, 7.9, 8.8, 6.2, 11.5, 6.2); body.close()
    screen = P(); screen.moveTo(10.2, 9.0); screen.lineTo(21.8, 9.0); screen.lineTo(22.9, 15.6); screen.lineTo(9.1, 15.6); screen.close()
    b = diff(body, screen, circle(10.4, 20.6, 1.5), circle(21.6, 20.6, 1.5), rect(13.6, 19.8, 4.8, 1.6, .8))
    rails = union(line([(8.6, 25.2), (6.8, 28.0)], 1.5), line([(23.4, 25.2), (25.2, 28.0)], 1.5),
                  line([(5.2, 28.2), (26.8, 28.2)], 1.5, "butt"))
    return union(b, rails, rect(13.5, 4.4, 5, 1.2, .6))

def mode_hev():
    """Suburban unit: squarer cab, split screen, diamond pantograph."""
    body = rect(7.4, 9.6, 17.2, 16.2, 3.4)
    b = diff(body, rect(9.6, 12.0, 6.1, 6.0, 1.2), rect(16.3, 12.0, 6.1, 6.0, 1.2),
             circle(11.0, 22.2, 1.4), circle(21.0, 22.2, 1.4), rect(14.4, 21.4, 3.2, 1.6, .8))
    panto = union(line([(12, 9.4), (16, 6.4), (20, 9.4)], 1.2), line([(12, 6.4), (16, 9.4), (20, 6.4)], 1.0),
                  line([(10.6, 4.6), (21.4, 4.6)], 1.3))
    rails = union(line([(9.6, 25.6), (8.0, 28.0)], 1.5), line([(22.4, 25.6), (24.0, 28.0)], 1.5),
                  line([(5.6, 28.2), (26.4, 28.2)], 1.5, "butt"))
    return union(b, panto, rails)

def mode_tramtrain():
    """Low-floor tram-train: rounded nose, one deep screen, single-arm pantograph."""
    body = P(); body.moveTo(10.6, 9.4); body.lineTo(21.4, 9.4); body.cubicTo(23.6, 9.4, 24.8, 10.8, 25.0, 13.2)
    body.lineTo(25.4, 23.0); body.cubicTo(25.4, 24.8, 24.2, 25.8, 22.6, 25.8); body.lineTo(9.4, 25.8)
    body.cubicTo(7.8, 25.8, 6.6, 24.8, 6.6, 23.0); body.lineTo(7.0, 13.2); body.cubicTo(7.2, 10.8, 8.4, 9.4, 10.6, 9.4); body.close()
    screen = P(); screen.moveTo(10.0, 11.8); screen.lineTo(22.0, 11.8); screen.cubicTo(22.8, 11.8, 23.0, 12.4, 23.0, 13.2)
    screen.lineTo(23.0, 18.4); screen.lineTo(9.0, 18.4); screen.lineTo(9.0, 13.2); screen.cubicTo(9.0, 12.4, 9.2, 11.8, 10.0, 11.8); screen.close()
    b = diff(body, screen, circle(10.6, 22.4, 1.3), circle(21.4, 22.4, 1.3))
    panto = union(line([(14, 9.2), (18.6, 6.4), (15.2, 4.4)], 1.1), line([(12.6, 4.4), (19.4, 4.4)], 1.3))
    rails = union(line([(5.6, 28.2), (26.4, 28.2)], 1.5, "butt"), line([(9.8, 25.6), (8.6, 28.0)], 1.4),
                  line([(22.2, 25.6), (23.4, 28.0)], 1.4))
    return union(b, panto, rails)

def mode_bus(coach=False):
    """Bus or coach front: screen, mirrors, lamps, wheels."""
    top = 5.6 if coach else 6.4
    body = rect(8.0, top, 16.0, 19.6 - (top - 6.4), 3.6 if coach else 2.6)
    screen = rect(9.8, top + 2.4, 12.4, 9.6 if coach else 8.6, 1.4 if coach else 1.0)
    b = diff(body, screen, circle(11.2, 21.0, 1.3), circle(20.8, 21.0, 1.3), rect(13.8, 20.2, 4.4, 1.6, .8))
    if not coach: b = diff(b, rect(9.8, top + 0.6, 12.4, 1.0, .5))
    mirrors = union(line([(8.2, top + 2.6), (6.0, top + 3.6), (6.0, top + 6.4)], 1.3),
                    line([(23.8, top + 2.6), (26.0, top + 3.6), (26.0, top + 6.4)], 1.3))
    wheels = union(rect(9.2, 25.4, 3.6, 3.0, .9), rect(19.2, 25.4, 3.6, 3.0, .9))
    return union(b, mirrors, wheels)

def mode_replacement():
    """Rail replacement: small train, arrow, then the bus that takes over."""
    train = fit_into(mode_train(), 2.2, 10.6, 10.4, 15.0)
    arrow_ = fit_into(arrow("right", 100, 60, 22), 13.2, 16.4, 4.4, 3.0)
    bus = fit_into(mode_bus(), 18.0, 6.4, 12.6, 19.2)
    return [(union(train, arrow_), "#FFFFFF"), (bus, "#FECC00")]

def mode_layers(key):
    if key == "rail": return [(mode_train(), "#FFFFFF")]
    if key == "hev": return [(mode_hev(), "#FFFFFF")]
    if key == "tramtrain": return [(mode_tramtrain(), "#FFFFFF")]
    if key == "local_bus": return [(mode_bus(), "#FFFFFF")]
    if key == "coach": return [(mode_bus(True), "#3A3A39")]
    return mode_replacement()

def svg_d(p):
    pen = SVGPathPen(None, lambda v: f"{v:.2f}".rstrip("0").rstrip(".")); p.draw(pen); return pen.getCommands()

def write_modes():
    icons = {}
    for key, (map_key, bg) in MODES.items():
        layers = mode_layers(key)
        xml = ['<?xml version="1.0" encoding="utf-8"?>',
               "<!-- MenetrendGO mode pictogram; generated by scripts/generate_pictograms.py -->",
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
               '    android:width="40dp" android:height="40dp"',
               '    android:viewportWidth="32" android:viewportHeight="32">',
               f'    <path android:fillColor="{bg}" android:pathData="{svg_d(rect(0, 0, 32, 32, 6))}" />']
        for p, colour in layers:
            xml.append(f'    <path android:fillColor="{colour}" android:fillType="evenOdd"\n        android:pathData="{svg_d(p)}" />')
        xml.append("</vector>")
        open(os.path.join(APP, "res", "drawable", f"ic_mode_{key}.xml"), "w", encoding="utf-8", newline="\n").write("\n".join(xml) + "\n")
        body = "".join(f'<path fill="{c}" fill-rule="evenodd" d="{svg_d(p)}"/>' for p, c in layers)
        icons[map_key] = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><circle cx="16" cy="16" r="16" fill="{bg}"/>'
                          f'<g transform="translate(3.2 3.2) scale(.8)">{body}</g></svg>')
    js = ("// Generated by scripts/generate_pictograms.py (MenetrendGO's own mode pictograms).\n"
          "const vehicleIcons=" + json.dumps(icons, separators=(",", ":")) + ";\n")
    open(os.path.join(APP, "assets", "vehicle-icons.js"), "w", encoding="utf-8", newline="\n").write(js)

if __name__ == "__main__":
    write_modes()
    n = build_font(os.path.join(APP, "res", "font", "transit_pictograms.ttf"))
    print(f"wrote 6 mode pictograms, vehicle-icons.js and {n} glyphs")

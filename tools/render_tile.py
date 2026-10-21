#!/usr/bin/env python3
"""
render_tile.py — render the real Sleep tile from the real Android VectorDrawables.

Why this exists: hand-written HTML mockups drift from the shipping drawables and lie
about what the tile will look like. This script reads wear/src/main/res/drawable/*.xml
and the geometry constants in SleepTileService.kt, converts the vectors to SVG, and
renders a filmstrip of paused animation frames with headless Chrome.

So every preview is generated from the code that compiles.

Usage:
    python tools/render_tile.py                 # filmstrip of the entrance
    python tools/render_tile.py --times 0,900   # custom frame times (ms)
    python tools/render_tile.py --out-dir DIR

Outputs (default: design/_render/):
    index.html      the composed page
    filmstrip.png   one row of frames, animations frozen per frame
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE = os.path.join(REPO, "wear", "src", "main", "res", "drawable")
TILE_KT = os.path.join(REPO, "wear", "src", "main", "java", "io", "github",
                       "hardbas", "wearsync", "app", "tile", "SleepTileService.kt")

ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
]


# ---------------------------------------------------------------- colour helpers

def argb_to_rgba(value: str):
    h = value.lstrip("#")
    if len(h) == 8:
        a, r, g, b = h[0:2], h[2:4], h[4:6], h[6:8]
    elif len(h) == 6:
        a, r, g, b = "ff", h[0:2], h[2:4], h[4:6]
    else:
        raise ValueError("unsupported colour %r" % value)
    return int(r, 16), int(g, 16), int(b, 16), round(int(a, 16) / 255.0, 4)


def rgba_css(value: str) -> str:
    r, g, b, a = argb_to_rgba(value)
    return "rgba(%d,%d,%d,%s)" % (r, g, b, a)


# ------------------------------------------------------- VectorDrawable -> SVG

def gradient_defs(node, gid: str):
    """Serialise a <gradient> child of aapt:attr into an SVG <defs> snippet + url()."""
    kind = node.get(ANDROID + "type", "linear")
    stops = []
    for item in node.findall("item"):
        off = float(item.get(ANDROID + "offset", "0"))
        col = item.get(ANDROID + "color", "#FF000000")
        stops.append('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>'
                     % (off, "#%s" % col.lstrip("#")[-6:], round(argb_to_rgba(col)[3], 4)))
    body = "".join(stops)
    if kind == "radial":
        cx = node.get(ANDROID + "centerX", "0")
        cy = node.get(ANDROID + "centerY", "0")
        r = node.get(ANDROID + "gradientRadius", "1")
        d = ('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">'
             % (gid, cx, cy, r))
    elif kind == "sweep":
        cx = node.get(ANDROID + "centerX", "0")
        cy = node.get(ANDROID + "centerY", "0")
        d = ('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="100">'
             % (gid, cx, cy))
    else:
        x1 = node.get(ANDROID + "startX", "0")
        y1 = node.get(ANDROID + "startY", "0")
        x2 = node.get(ANDROID + "endX", "100")
        y2 = node.get(ANDROID + "endY", "100")
        d = ('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">'
             % (gid, x1, y1, x2, y2))
    return d + body + ("</radialGradient>" if kind in ("radial", "sweep") else "</linearGradient>")


def paint_attrs(path_el, prefix, counter, defs):
    """Resolve fillColor/strokeColor, including gradient variants, into SVG attributes."""
    out = {}
    for slot, attr in (("fill", "fillColor"), ("stroke", "strokeColor")):
        grad = path_el.find("%s%s" % (AAPT, "attr"))
        chosen = None
        for candidate in path_el.findall("%s%s" % (AAPT, "attr")):
            if candidate.get("name") == "android:" + attr:
                chosen = candidate
        if chosen is not None:
            g = chosen.find("gradient")
            if g is not None:
                gid = "%s_g%d" % (prefix, counter[0])
                counter[0] += 1
                defs.append(gradient_defs(g, gid))
                out[slot] = "url(#%s)" % gid
                continue
        raw = path_el.get(ANDROID + attr)
        if raw:
            out[slot] = rgba_css(raw)
        elif slot == "fill":
            out[slot] = "none"
    if path_el.get(ANDROID + "fillAlpha"):
        out["fill_opacity"] = path_el.get(ANDROID + "fillAlpha")
    if path_el.get(ANDROID + "strokeWidth"):
        out["stroke_width"] = path_el.get(ANDROID + "strokeWidth")
    if path_el.get(ANDROID + "fillType") == "evenOdd":
        out["fill_rule"] = "evenodd"
    return out


def collect(node, prefix, counter, defs, out):
    for child in node:
        tag = child.tag
        if tag == "path":
            attrs = paint_attrs(child, prefix, counter, defs)
            pieces = ['d="%s"' % child.get(ANDROID + "pathData")]
            pieces.append('fill="%s"' % attrs.get("fill", "none"))
            if attrs.get("fill_opacity"):
                pieces.append('fill-opacity="%s"' % attrs["fill_opacity"])
            if attrs.get("stroke"):
                pieces.append('stroke="%s"' % attrs["stroke"])
            if attrs.get("stroke_width"):
                pieces.append('stroke-width="%s"' % attrs["stroke_width"])
            if attrs.get("fill_rule"):
                pieces.append('fill-rule="%s"' % attrs["fill_rule"])
            out.append("<path %s/>" % " ".join(pieces))
        elif tag == "group":
            inner = []
            collect(child, prefix, counter, defs, inner)
            name = child.get(ANDROID + "name", "g")
            def num(attr, default=0.0):
                return float(child.get(ANDROID + attr, default))
            px, py = num("pivotX"), num("pivotY")
            sx, sy = num("scaleX", 1.0), num("scaleY", 1.0)
            tx, ty = num("translateX"), num("translateY")
            rot = num("rotation")
            # Android applies scale/rotate about the pivot, then translation.
            parts = ["translate(%s,%s)" % (tx, ty),
                     "translate(%s,%s)" % (px, py),
                     "rotate(%s)" % rot,
                     "scale(%s,%s)" % (sx, sy),
                     "translate(%s,%s)" % (-px, -py)]
            out.append('<g data-name="%s" transform="%s">%s</g>'
                       % (name, " ".join(parts), "".join(inner)))
        elif tag == "clip-path":
            # Unsupported on purpose: a clip that silently disappears would make the
            # preview lie. Refuse loudly instead.
            raise SystemExit("render_tile: <clip-path> in %s is not supported" % prefix)


def vector_to_svg(name: str, prefix: str) -> str:
    path = os.path.join(DRAWABLE, name + ".xml")
    if not os.path.exists(path):
        raise SystemExit("render_tile: missing drawable %s" % path)
    root = ET.parse(path).getroot()
    vw = root.get(ANDROID + "viewportWidth", "100")
    vh = root.get(ANDROID + "viewportHeight", "100")
    counter = [0]
    defs, body = [], []
    collect(root, prefix, counter, defs, body)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" '
            'preserveAspectRatio="xMidYMid meet"><defs>%s</defs>%s</svg>'
            % (vw, vh, "".join(defs), "".join(body)))


# --------------------------------------------------- constants from the Kotlin

def read_geometry():
    src = open(TILE_KT, encoding="utf-8").read()
    def num(pattern, default, cast=float):
        m = re.search(pattern, src)
        return cast(m.group(1)) if m else default
    return {
        "sun_dx": num(r"SUN_OFFSET_X_DP\s*=\s*(-?[\d.]+)f", -40.0),
        "sun_y0": num(r"SUN_START_Y_DP\s*=\s*(-?[\d.]+)f", 44.0),
        "sun_y1": num(r"SUN_END_Y_DP\s*=\s*(-?[\d.]+)f", 112.0),
        "sun_ms": num(r"setDurationMillis\((\d+)L\)\s*\n\s*\.setEasing[^\n]*cubicBezier\(0\.42", 1700, int),
        "arc_ms": num(r"setDurationMillis\((\d+)L\)\s*\n\s*\.setEasing[^\n]*cubicBezier\(0\.16", 1300, int),
        "arc_start": num(r"arcStartAngle\s*=\s*(-?[\d.]+)f", 140.0),
        "arc_span": num(r"totalArcSpanDegrees\s*=\s*(-?[\d.]+)f", 260.0),
    }


# ------------------------------------------------------------------- page build

FRAME_W = 192
TILE_CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{background:#08080A;color:#E8E8EC;font-family:-apple-system,"Segoe UI",Roboto,Arial,sans-serif;padding:26px}
.h{font-size:15px;font-weight:600;margin-bottom:4px}
.s{font-size:11.5px;color:#8A8A93;margin-bottom:18px;line-height:1.6}
.row{display:flex;gap:18px;flex-wrap:wrap;align-items:flex-start}
.frame{text-align:center}
.frame .t{font-size:10px;color:#6E6E78;margin-top:8px;font-variant-numeric:tabular-nums}
.tile{position:relative;width:192px;height:192px;border-radius:50%;overflow:hidden;isolation:isolate;
box-shadow:0 0 0 1px rgba(255,255,255,.08),0 14px 34px rgba(0,0,0,.8)}
.sf{position:absolute;inset:0;background:radial-gradient(circle at 50% 42%,#151B3D 0%,#0D1126 46%,#070914 80%,#030408 100%)}
.star{position:absolute;background:#fff;border-radius:50%}
.moon{position:absolute;left:0;top:0;width:192px;height:192px}
.sun{position:absolute;left:50%;top:50%;width:70px;height:70px}
.horizon{position:absolute;inset:0;width:100%;height:100%}
.arcsvg{position:absolute;inset:0;width:100%;height:100%}
.track{stroke:rgba(255,255,255,.13);stroke-width:6.5;fill:none;stroke-linecap:round}
.active{stroke:#47CDFF;stroke-width:6.5;fill:none;stroke-linecap:round}
.content{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
.title{font-size:11px;font-weight:700;color:#FFD166;letter-spacing:.55px;text-transform:uppercase}
.score{font-size:32px;font-weight:700;color:#fff;line-height:1.05;font-variant-numeric:tabular-nums;margin-top:-1px}
.q{font-size:11px;font-weight:500;color:rgba(255,255,255,.82)}
.dur{font-size:10px;font-weight:500;color:rgba(255,255,255,.6);margin-top:2px}
.pill{display:flex;align-items:center;gap:4px;background:rgba(255,255,255,.14);border-radius:999px;padding:3px 9px;margin-top:8px}
.pill span{font-size:10px;font-weight:700;font-variant-numeric:tabular-nums}
.dot{width:5px;height:5px;border-radius:50%;background:#00E676}
"""


def frame_css(t_ms, g, seq=0, live=False):
    """Freeze the entrance choreography at t_ms.

    Every frame gets its OWN keyframe names and selectors, scoped by seq. Sharing them
    across frames makes the last one win for every tile, which silently freezes the
    whole strip at the last frame's state.
    """
    cycle = 5000
    off = -int(t_ms)
    sink = "sink%d" % seq
    sweep = "sweep%d" % seq
    pause = "" if live else (
        ".f%(seq)d .sun,.f%(seq)d .active{animation-play-state:paused!important;"
        "animation-delay:%(off)dms!important}\n" % {"seq": seq, "off": off}
    )
    return """
@keyframes %(sink)s{
  0%%{transform:translate(-50%%,-50%%) translate(%(dx)fpx,%(y0)fpx);
      animation-timing-function:cubic-bezier(.42,0,.26,1)}
  %(sink_pct).2f%%{transform:translate(-50%%,-50%%) translate(%(dx)fpx,%(y1)fpx)}
  92%%{transform:translate(-50%%,-50%%) translate(%(dx)fpx,%(y1)fpx)}
  100%%{transform:translate(-50%%,-50%%) translate(%(dx)fpx,%(y0)fpx)}
}
@keyframes %(sweep)s{
  0%%{stroke-dasharray:0 553;animation-timing-function:cubic-bezier(.16,1,.3,1)}
  %(sweep_pct).2f%%{stroke-dasharray:%(arc_len).1f 553}
  100%%{stroke-dasharray:%(arc_len).1f 553}
}
%(pause)s.f%(seq)d .sun{animation-name:%(sink)s;animation-duration:%(cycle)dms;animation-iteration-count:infinite}
.f%(seq)d .active{animation-name:%(sweep)s;animation-duration:%(cycle)dms;animation-iteration-count:infinite}
""" % {
        "dx": g["sun_dx"], "y0": g["sun_y0"], "y1": g["sun_y1"],
        "sink_pct": 100.0 * g["sun_ms"] / cycle,
        "sweep_pct": 100.0 * g["arc_ms"] / cycle,
        "arc_len": 2 * 3.14159265 * 88 * (0.88 * g["arc_span"]) / 360.0,
        "cycle": cycle, "off": off, "seq": seq,
        "sink": sink, "sweep": sweep, "pause": pause,
    }


def tile_markup(g, css_extra="", seq=0):
    stars = "".join(
        '<div class="star" style="width:%spx;height:%spx;left:%spx;top:%spx;opacity:%s"></div>'
        % (w, w, x, y, o)
        for w, x, y, o in [(1.6, 52, 38, .9), (1.2, 96, 29, .7), (1.1, 150, 56, .55), (1.4, 31, 112, .5)]
    )
    return """
<div class="tile f%(seq)d">
  <div class="sf"></div>%(stars)s
  <div class="moon">%(moon)s</div>
  <div class="sun">%(sun)s</div>
  <div class="horizon">%(horizon)s</div>
  <svg class="arcsvg" viewBox="0 0 192 192">
    <circle class="track" cx="96" cy="96" r="88" stroke-dasharray="%(track).1f 553" transform="rotate(%(start)f 96 96)"/>
    <circle class="active" cx="96" cy="96" r="88" transform="rotate(%(start)f 96 96)"/>
  </svg>
  <div class="content">
    <div class="title">Sue\u00f1o</div><div class="score">88</div>
    <div class="q">Excelente</div><div class="dur">7h 12m</div>
    <div class="pill"><div class="dot"></div><span>\u00b10.0 \u00b0C</span></div>
  </div>
  <style>%(css)s</style>
</div>""" % {
        "stars": stars,
        "moon": vector_to_svg("ic_tile_moon", "m%d" % seq),
        "sun": vector_to_svg("sun_disc", "s%d" % seq),
        "horizon": vector_to_svg("horizon_limb", "h%d" % seq),
        "track": 2 * 3.14159265 * 88 * g["arc_span"] / 360.0,
        "start": g["arc_start"],
        "css": css_extra,
        "seq": seq,
    }


def build_page(g, times):
    frames = []
    for i, t in enumerate(times):
        frames.append('<div class="frame">%s<div class="t">%d ms</div></div>'
                      % (tile_markup(g, frame_css(t, g, i), i), t))
    header = (
        'Constantes le\u00eddas de SleepTileService.kt: sol x=%s y=%s\u2192%s %sms \u00b7 '
        'arco %sms \u00b7 ancla %s\u00b0 longitud %s\u00b0'
        % (g["sun_dx"], g["sun_y0"], g["sun_y1"], g["sun_ms"],
           g["arc_ms"], g["arc_start"], g["arc_span"])
    )
    return ("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
            "<title>Sleep tile render</title><style>" + TILE_CSS + "</style></head><body>"
            '<div class="h">Sleep tile \u00b7 generado desde los VectorDrawable reales</div>'
            '<div class="s">' + header + '</div>'
            '<div class="row">' + "".join(frames) + "</div></body></html>")


# ---------------------------------------------------------------------- render

def find_chrome():
    for c in CHROME_CANDIDATES:
        if os.path.exists(c):
            return c
    return shutil.which("google-chrome") or shutil.which("chromium")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--times", default="0,350,700,1050,1400,1700",
                    help="frame times in ms, comma separated")
    ap.add_argument("--out-dir", default=os.path.join(REPO, "design", "_render"))
    ap.add_argument("--scale", type=float, default=2.0)
    ap.add_argument("--live", action="store_true",
                    help="also write live.html: one tile with the animation actually running")
    args = ap.parse_args()

    g = read_geometry()
    times = [int(x) for x in args.times.split(",") if x.strip()]
    os.makedirs(args.out_dir, exist_ok=True)

    html_path = os.path.join(args.out_dir, "index.html")
    with open(html_path, "w", encoding="utf-8", newline="") as fh:
        fh.write(build_page(g, times))

    live_path = os.path.join(args.out_dir, "live.html")
    if args.live:
        body = tile_markup(g, frame_css(0, g, 0, live=True), 0)
        header = ('<div class="h">Sleep tile \u00b7 en movimiento</div>'
                  '<div class="s">Generado desde los VectorDrawable reales y las constantes de '
                  'SleepTileService.kt. La entrada real es la primera pasada; despu\u00e9s repite.</div>')
        with open(live_path, "w", encoding="utf-8", newline="") as fh:
            fh.write("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                     "<title>Sleep tile live</title><style>" + TILE_CSS
                     + "</style></head><body>" + header
                     + '<div class="row">' + body + "</div></body></html>")
        print("live: %s" % live_path)

    chrome = find_chrome()
    if not chrome:
        print("wrote %s (no Chrome found; open it manually)" % html_path)
        return 0

    width = int(FRAME_W * args.scale * min(len(times), 3) + 80)
    height = int(FRAME_W * args.scale + 120)
    png = os.path.join(args.out_dir, "filmstrip.png")
    cmd = [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
           "--force-device-scale-factor=%s" % args.scale,
           "--window-size=%d,%d" % (width, height),
           "--screenshot=%s" % png,
           "file:///%s" % html_path.replace("\\", "/").lstrip("/")]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if not os.path.exists(png):
        print(res.stdout[-800:], res.stderr[-800:], file=sys.stderr)
        return 1
    print("html: %s" % html_path)
    print("png : %s  (%d frames)" % (png, len(times)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

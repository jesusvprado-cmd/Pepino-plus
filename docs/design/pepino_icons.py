"""Iconos de perfil de Pepino+: un pepino como mascota en 7 poses (mismos nombres que los antiguos del gatito)."""
import os
import subprocess
import sys

OUT = sys.argv[1]
TMP = '/tmp/pepino_svg'
os.makedirs(TMP, exist_ok=True)

INK = '#173D10'


def defs():
    return f'''<defs>
  <linearGradient id="skin" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#9BE36A"/><stop offset="0.45" stop-color="#6CCB45"/><stop offset="1" stop-color="#3B9A2A"/>
  </linearGradient>
  <radialGradient id="glow" cx="0.5" cy="0.42" r="0.7">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.28"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
  </radialGradient>
</defs>'''


def eyes(style, look=(0, 0)):
    lx, ly = look
    out = []
    for ex in (-24, 24):
        if style == 'closed':
            out.append(f'<path d="M{ex-14},-2 Q{ex},10 {ex+14},-2" fill="none" stroke="{INK}" stroke-width="6" stroke-linecap="round"/>')
        elif style == 'happy':
            out.append(f'<path d="M{ex-14},4 Q{ex},-12 {ex+14},4" fill="none" stroke="{INK}" stroke-width="6" stroke-linecap="round"/>')
        else:
            out.append(f'<ellipse cx="{ex}" cy="0" rx="17" ry="19" fill="#FFFFFF" stroke="{INK}" stroke-width="5"/>')
            out.append(f'<circle cx="{ex+lx}" cy="{2+ly}" r="10" fill="#111111"/>')
            out.append(f'<circle cx="{ex+lx-4}" cy="{ly-3}" r="3.6" fill="#FFFFFF"/>')
    return ''.join(out)


def mouth(style):
    if style == 'grin':
        return (f'<path d="M-22,22 Q0,52 22,22 Z" fill="#7A1F1F" stroke="{INK}" stroke-width="5" stroke-linejoin="round"/>'
                f'<path d="M-12,36 Q0,46 12,36" fill="#FF8C8C"/>')
    if style == 'o':
        return f'<ellipse cx="0" cy="30" rx="8" ry="10" fill="#7A1F1F" stroke="{INK}" stroke-width="4"/>'
    if style == 'small':
        return f'<path d="M-8,26 Q0,32 8,26" fill="none" stroke="{INK}" stroke-width="5" stroke-linecap="round"/>'
    if style == 'sleep':
        return f'<path d="M-6,28 Q0,25 6,28" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    return f'<path d="M-16,22 Q0,38 16,22" fill="none" stroke="{INK}" stroke-width="6" stroke-linecap="round"/>'


def body(w=118, h=212):
    """Pepino centrado en (0,0), de pie. Devuelve el cuerpo sin cara."""
    x, y = -w / 2, -h / 2
    bumps = [(-30, -60), (22, -78), (-12, -20), (30, -6), (-34, 30), (14, 44), (-6, 78), (34, 70), (-26, 92), (8, -48)]
    b = ''.join(f'<ellipse cx="{bx}" cy="{by}" rx="5" ry="3.6" fill="#2D7A1F" opacity="0.55"/>' for bx, by in bumps)
    return (
        f'<path d="M-6,{y+6} q4,-22 18,-26" fill="none" stroke="#3E7A22" stroke-width="9" stroke-linecap="round"/>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{w/2}" fill="url(#skin)" stroke="{INK}" stroke-width="6"/>'
        f'<rect x="{x+16}" y="{y+26}" width="18" height="{h-70}" rx="9" fill="#FFFFFF" opacity="0.28"/>'
        + b
    )


def face(eye='open', mth='smile', look=(0, 0), blush=True):
    s = f'<g transform="translate(0,-38)">{eyes(eye, look)}{mouth(mth)}'
    if blush:
        s += '<ellipse cx="-44" cy="22" rx="11" ry="6" fill="#FF7FA0" opacity="0.7"/><ellipse cx="44" cy="22" rx="11" ry="6" fill="#FF7FA0" opacity="0.7"/>'
    return s + '</g>'


def arm(x1, y1, x2, y2):
    return (f'<path d="M{x1},{y1} L{x2},{y2}" stroke="{INK}" stroke-width="17" stroke-linecap="round"/>'
            f'<path d="M{x1},{y1} L{x2},{y2}" stroke="#5DBE3B" stroke-width="7" stroke-linecap="round"/>')


def legs():
    return arm(-24, 96, -30, 118) + arm(24, 96, 30, 118)


def scene(bg, content):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320" width="320" height="320">{defs()}
<rect width="320" height="320" fill="{bg}"/><rect width="320" height="320" fill="url(#glow)"/>{content}</svg>'''


def sparkles(color='#FFFFFF'):
    return ''.join(
        f'<path d="M{x},{y-9} L{x+3},{y-3} L{x+9},{y} L{x+3},{y+3} L{x},{y+9} L{x-3},{y+3} L{x-9},{y} L{x-3},{y-3} Z" fill="{color}" opacity="0.85"/>'
        for x, y in ((52, 62), (270, 92), (258, 250)))


icons = {}

# 01 Sentado: de pie, mirando al frente, brazos abajo.
icons['01_sentado'] = scene('#E8322E', '<g transform="translate(160,170)">'
                            + arm(-56, 10, -78, 48) + arm(56, 10, 78, 48) + legs() + body() + face() + '</g>' + sparkles())

# 02 Asomándose: solo la parte de arriba asoma por abajo, con las manos en el borde.
icons['02_asomandose'] = scene('#FF8A1F', '<g transform="translate(160,300)">' + body() + face('open', 'small', (0, -4)) + '</g>'
                               + '<rect x="0" y="282" width="320" height="38" fill="#C9600F"/>'
                               + f'<rect x="0" y="282" width="320" height="6" fill="{INK}" opacity="0.35"/>'
                               + '<g transform="translate(160,300)">' + arm(-52, -16, -70, -26) + arm(52, -16, 70, -26) + '</g>')

# 03 Sonriendo: ojos felices, gran sonrisa, brazos arriba.
icons['03_sonriendo'] = scene('#FF4F9E', '<g transform="translate(160,172)">'
                              + arm(-56, -6, -86, -52) + arm(56, -6, 86, -52) + legs() + body() + face('happy', 'grin') + '</g>' + sparkles())

# 04 Agazapado: tumbado, mirando hacia arriba.
icons['04_agazapado'] = scene('#FFC81F', '<g transform="translate(160,212) rotate(-90)">'
                              + arm(-40, -62, -62, -78) + arm(40, -62, 62, -78) + body() + '</g>'
                              + '<g transform="translate(118,212)">' + eyes('open', (6, -7)) + mouth('small') + '</g>')

# 05 Palomitas: sujetando un cubo de palomitas.
corn = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFF6D6" stroke="{INK}" stroke-width="3"/>'
               for x, y, r in ((-26, 44, 13), (-6, 36, 14), (16, 40, 13), (30, 50, 11), (-14, 52, 12), (6, 50, 12)))
bucket = (corn + f'<path d="M-40,54 L40,54 L30,120 L-30,120 Z" fill="#FFFFFF" stroke="{INK}" stroke-width="5" stroke-linejoin="round"/>'
          + ''.join(f'<path d="M{x},54 L{x*0.75:.1f},120 L{(x+14)*0.75:.1f},120 L{x+14},54 Z" fill="#E8322E"/>' for x in (-33, -5, 23))
          + f'<path d="M-40,54 L40,54 L30,120 L-30,120 Z" fill="none" stroke="{INK}" stroke-width="5" stroke-linejoin="round"/>')
flying = ''.join(f'<circle cx="{x}" cy="{y}" r="9" fill="#FFF6D6" stroke="{INK}" stroke-width="3"/>' for x, y in ((62, 70), (262, 58), (240, 120), (78, 132)))
icons['05_palomitas'] = scene('#9B4DE8', flying + '<g transform="translate(160,160)">' + legs() + body() + face('open', 'grin', (3, 0))
                              + bucket + arm(-56, 20, -30, 70) + arm(56, 20, 30, 70) + '</g>')

# 06 Jugando: inclinado, con una pelota y líneas de movimiento.
ball = (f'<circle cx="248" cy="236" r="30" fill="#FFD21F" stroke="{INK}" stroke-width="5"/>'
        f'<path d="M222,222 Q248,240 274,222 M228,256 Q248,236 268,256" fill="none" stroke="{INK}" stroke-width="4"/>')
motion = ''.join(f'<path d="M{x},{y} l-26,0" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" opacity="0.7"/>' for x, y in ((70, 120), (60, 150), (74, 180)))
icons['06_jugando'] = scene('#1E9BFF', motion + ball + '<g transform="translate(150,168) rotate(-14)">'
                            + arm(-56, 0, -86, -30) + arm(56, 10, 92, 44) + legs() + body() + face('open', 'grin', (6, 4)) + '</g>')

# 07 Dormido: tumbado, ojos cerrados y «Zz», de noche.
stars = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFFFFF" opacity="0.8"/>' for x, y, r in ((40, 50, 3), (96, 30, 2), (280, 40, 3), (250, 90, 2), (60, 110, 2)))
zz = (f'<text x="214" y="132" font-family="Manrope, sans-serif" font-weight="800" font-size="40" fill="#FFFFFF">Z</text>'
      f'<text x="246" y="102" font-family="Manrope, sans-serif" font-weight="800" font-size="28" fill="#FFFFFF" opacity="0.85">z</text>')
icons['07_dormido'] = scene('#24306E', stars + '<rect x="0" y="246" width="320" height="74" fill="#1A2350"/>'
                            + '<g transform="translate(160,212) rotate(-90)">' + body() + '</g>'
                            + '<g transform="translate(110,214)">' + eyes('closed') + mouth('sleep')
                            + '<ellipse cx="-30" cy="22" rx="10" ry="5" fill="#FF7FA0" opacity="0.6"/></g>' + zz)

for name, svg in icons.items():
    svg_path = os.path.join(TMP, name + '.svg')
    png_path = os.path.join(TMP, name + '.png')
    with open(svg_path, 'w') as f:
        f.write(svg)
    subprocess.run(['rsvg-convert', '-w', '320', '-h', '320', svg_path, '-o', png_path], check=True)
    from PIL import Image
    Image.open(png_path).convert('RGB').save(os.path.join(OUT, name + '.webp'), 'WEBP', quality=90)
print('ok', len(icons))

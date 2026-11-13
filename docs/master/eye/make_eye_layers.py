# Makes the two eye layers from the approved iris render:
#   python3 make_eye_layers.py app/src/main/res/drawable-nodpi <preview dir> docs/master/eye/seema_eye_source.webp
# seema_eye_fibres.webp: only the blue fibres (the centre filled with them), still, filling the almond.
# seema_eye_core.webp: the pupil and hazel collar with a soft edge, the part that moves.
import numpy as np, sys
from PIL import Image
res = sys.argv[1]
src = np.asarray(Image.open(sys.argv[3] if len(sys.argv) > 3 else f"{res}/seema_eye.webp").convert("RGBA")).astype(np.float32)
n = src.shape[0]; R = n / 2.0; c = (n - 1) / 2.0
yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
dx, dy = xx - c, yy - c
r = np.hypot(dx, dy) / R
ang = np.arctan2(dy, dx)

def sample(img, x, y):
    x = np.clip(x, 0, n - 1); y = np.clip(y, 0, n - 1)
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    x1 = np.clip(x0 + 1, 0, n - 1); y1 = np.clip(y0 + 1, 0, n - 1)
    fx = (x - x0)[..., None]; fy = (y - y0)[..., None]
    a = img[y0, x0] * (1 - fx) + img[y0, x1] * fx
    b = img[y1, x0] * (1 - fx) + img[y1, x1] * fx
    return a * (1 - fy) + b * fy

# 1. Fibres only: inside 0.64 R the blue fibres of 0.64..0.82 R are drawn inward along the same angle.
HOLE, FROM, TO = 0.64, 0.64, 0.82
sr = FROM + (TO - FROM) * np.clip(r / HOLE, 0, 1) ** 0.85
fill = sample(src, c + np.cos(ang) * sr * R, c + np.sin(ang) * sr * R)
w = np.clip((HOLE + 0.04 - r) / 0.08, 0, 1)[..., None]   # blend 0.60..0.68
fib = src * (1 - w) + fill * w
fib[..., 3] = src[..., 3]
Image.fromarray(np.clip(fib, 0, 255).astype(np.uint8), "RGBA").save(f"{res}/seema_eye_fibres.webp", quality=90, method=6)

# 2. The moving centre: pupil + hazel collar, solid to 0.50 R, fading out by 0.62 R.
CORE = 0.62
a = np.clip((CORE - r) / (CORE - 0.50), 0, 1)
a = a * a * (3 - 2 * a)
core = src.copy(); core[..., 3] = src[..., 3] * a
k = int(round(CORE * R)); lo, hi = int(c - k), int(c + k) + 1
Image.fromarray(np.clip(core[lo:hi, lo:hi], 0, 255).astype(np.uint8), "RGBA").save(f"{res}/seema_eye_core.webp", quality=92, method=6)
print("core px", hi - lo, "of", n)

# preview: core shifted right on the fibres
prev = Image.fromarray(np.clip(fib, 0, 255).astype(np.uint8), "RGBA")
cimg = Image.open(f"{res}/seema_eye_core.webp")
for i, sh in enumerate([0, 110, -110]):
    p = prev.copy(); p.alpha_composite(cimg, (lo + sh, lo)); p.save(f"{sys.argv[2]}/preview{i}.png")

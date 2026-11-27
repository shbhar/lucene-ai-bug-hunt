# Independent exact check of one tessellation of a polygon, in fractions of the given doubles. A helper, not a program
# that runs by itself: it takes four arguments and reads two files that are not part of this folder, a polygon and the
# console output of a program that tessellated it. Started without arguments it stops with an IndexError.
# usage: mycheck.py POLYGON.json CONSOLE.txt LABEL CHECKFLAG(false|true)
#   POLYGON.json  {"coordinates": [outer ring, hole, ...]}, each ring a closed list of [x, y] points
#   CONSOLE.txt   one line a triangle: TRI <LABEL> <CHECKFLAG> x1 y1 x2 y2 x3 y3, the six doubles in hexadecimal notation
#   It prints one line: CORRECT, WRONG or NO-TRIANGLES, with the two areas and the counts that decide it.
import json, sys
from fractions import Fraction as F
poly = json.load(open(sys.argv[1]))
label, flag = sys.argv[3], sys.argv[4]
rings = [[(F(x), F(y)) for x, y in r] for r in poly['coordinates']]
def sarea(r):  # signed area of a closed ring
    s = F(0)
    for i in range(len(r) - 1):
        s += r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1]
    return s / 2
def orient(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
def onseg(a, b, p):
    return orient(a, b, p) == 0 and min(a[0], b[0]) <= p[0] <= max(a[0], b[0]) and min(a[1], b[1]) <= p[1] <= max(a[1], b[1])
def pip(r, p):  # 1 inside, 0 on boundary, -1 outside (crossing number, exact)
    inside = False
    for i in range(len(r) - 1):
        a, b = r[i], r[i + 1]
        if onseg(a, b, p): return 0
        if (a[1] > p[1]) != (b[1] > p[1]):
            xi = a[0] + (p[1] - a[1]) * (b[0] - a[0]) / (b[1] - a[1])
            if xi > p[0]: inside = not inside
    return 1 if inside else -1
tris = []
for line in open(sys.argv[2]):
    w = line.split()
    if len(w) >= 9 and w[0] == 'TRI' and w[1] == label and w[2] == flag:
        v = [F(float.fromhex(x)) for x in w[3:9]]
        tris.append([(v[0], v[1]), (v[2], v[3]), (v[4], v[5])])
want = abs(sarea(rings[0])) - sum(abs(sarea(h)) for h in rings[1:])
got = sum(abs(orient(*t)) / 2 for t in tris)
verts = set(p for r in rings for p in r)
notvert = sum(1 for t in tris for p in t if p not in verts)
zero = sum(1 for t in tris if orient(*t) == 0)
inhole = outside = onb = 0
for t in tris:
    c = ((t[0][0] + t[1][0] + t[2][0]) / 3, (t[0][1] + t[1][1] + t[2][1]) / 3)
    o = pip(rings[0], c)
    if o < 0: outside += 1
    if o == 0: onb += 1
    for h in rings[1:]:
        q = pip(h, c)
        if q > 0: inhole += 1
        if q == 0: onb += 1
def clip(subject, a, b, s):  # keep the part of the convex polygon on the left (s=1) or right (s=-1) of a->b
    out = []
    n = len(subject)
    for i in range(n):
        p, q = subject[i], subject[(i + 1) % n]
        dp, dq = s * orient(a, b, p), s * orient(a, b, q)
        if dp >= 0: out.append(p)
        if (dp > 0 and dq < 0) or (dp < 0 and dq > 0):
            t = dp / (dp - dq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out
def inter_area(t1, t2):
    s = 1 if orient(*t2) > 0 else -1
    poly = list(t1)
    for i in range(3):
        poly = clip(poly, t2[i], t2[(i + 1) % 3], s)
        if len(poly) < 3: return F(0)
    ar = F(0)
    for i in range(len(poly)):
        ar += poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
    return abs(ar) / 2
overl = 0; overl_area = F(0)
nz = [t for t in tris if orient(*t) != 0]
for i in range(len(nz)):
    for j in range(i + 1, len(nz)):
        a = inter_area(nz[i], nz[j])
        if a > 0: overl += 1; overl_area += a
ok = (len(tris) > 0 and got == want and inhole == 0 and outside == 0 and overl == 0 and notvert == 0)
print('%s [%s check=%s] triangles %d | area want %.17g got %.17g exact-equal %s diff %.3g | zero-area %d | min area %.3g | vertices not of the polygon %d | centroids in a hole %d, outside the ring %d, on a boundary %d | overlapping pairs %d (area %.3g)' % (
    'CORRECT' if ok else ('NO-TRIANGLES' if not tris else 'WRONG'), label, flag, len(tris), float(want), float(got), got == want, float(got - want), zero,
    float(min([abs(orient(*t)) / 2 for t in tris] or [0])), notvert, inhole, outside, onb, overl, float(overl_area)))

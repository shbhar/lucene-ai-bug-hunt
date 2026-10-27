"""
Z3 model of the two point-to-child rules used by PackedQuadPrefixTree at one level of the quad tree.

  index side  PackedQuadPrefixTree.PrefixTreeIterator.hasNext (PackedQuadPrefixTree.java:468-503):
              children are visited in Z order 0=TL,1=TR,2=BL,3=BR (nextCell, :248-270) and the first child
              whose rectangle relate()s to the point (not DISJOINT) is taken; for a geo context the
              rectangle-contains-point test wraps longitude (+180 == -180 for a rectangle with minX = -180).
  query side  PackedQuadPrefixTree.getCell (:90-128) -> QuadPrefixTree.battenberg (:223-235):
              top iff yp >= ymid, left iff xp <= xmid.

Exact rational arithmetic (geo world bounds are dyadic, so cell edges are exact binary fractions).
Q1: inside an arbitrary cell [x0,x0+w]x[y0,y0+h], without wrap: is there a point where the rules differ?
Q2: the level-1 children of the geo world [-180,180]x[-90,90] with the geo wrap rule: same question.
"""
from z3 import Real, Solver, And, Or, If, Not, sat

def contains(px, py, minx, maxx, miny, maxy, wrap):
    inx = And(px >= minx, px <= maxx)
    if wrap:
        # spatial4j RectangleImpl.relate(Point) in geo: a point at x=+180 is shifted by -360 when it lies east of
        # the rectangle, so a rectangle starting at -180 contains it (and x=-180 is shifted +360 for maxX=180)
        inx = Or(inx, And(px == 180, minx == -180), And(px == -180, maxx == 180))
    return And(inx, py >= miny, py <= maxy)

def zfirst(px, py, x0, w, y0, h, wrap):
    xm, ym = x0 + w / 2, y0 + h / 2
    kids = [(x0, xm, ym, y0 + h), (xm, x0 + w, ym, y0 + h), (x0, xm, y0, ym), (xm, x0 + w, y0, ym)]
    e = 4
    for i in reversed(range(4)):
        e = If(contains(px, py, *kids[i], wrap), i, e)
    return e

def batt(px, py, x0, w, y0, h):
    xm, ym = x0 + w / 2, y0 + h / 2
    return If(ym <= py, If(xm >= px, 0, 1), If(xm >= px, 2, 3))

def run(name, x0, w, y0, h, wrap, extra=()):
    px, py = Real('px'), Real('py')
    s = Solver()
    s.add(px >= x0, px <= x0 + w, py >= y0, py <= y0 + h)
    for c in extra:
        s.add(c)
    s.add(zfirst(px, py, x0, w, y0, h, wrap) != batt(px, py, x0, w, y0, h))
    r = s.check()
    print(name, r, s.model() if r == sat else '')
    return r

X0, W, Y0, H = Real('x0'), Real('w'), Real('y0'), Real('h')
# Q1: symbolic cell, no wrap (any level, any context without longitude wrap)
px, py = Real('px'), Real('py')
s = Solver()
s.add(W > 0, H > 0, px >= X0, px <= X0 + W, py >= Y0, py <= Y0 + H)
s.add(zfirst(px, py, X0, W, Y0, H, False) != batt(px, py, X0, W, Y0, H))
print('Q1 symbolic cell, no wrap:', s.check())
# Q2: geo world, level-1 split, with wrap
run('Q2 geo world level 1, wrap:', -180, 360, -90, 180, True)
# Q3: geo world, wrap, excluding x=+180
run('Q3 geo world level 1, wrap, px != 180:', -180, 360, -90, 180, True, [Real('px') != 180])

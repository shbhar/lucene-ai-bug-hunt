"""Z3 FP64 witness search for BBoxOverlapRatioValueSource.score (counterexample engine only).

Model <-> Java mapping (lucene/spatial-extras/.../bbox/BBoxOverlapRatioValueSource.java):
  L189 qryWestLeft  = max(a.minX, b.minX)       -> wl
  L190 qryWestRight = b.maxX                    -> wr
  L191 if (wl < wr) width += wr - wl            -> p1
  L193 qryEastLeft  = b.minX                    -> el
  L194 qryEastRight = min(a.maxX, b.maxX)       -> er
  L195 if (el < er) width += er - el            -> p2
  L219 targetArea = calcArea(target.getWidth(), target.getHeight()); spatial4j getWidth() = maxX - minX
  L223 targetRatio = intersectionArea / targetArea; L231-232 assert ratio in [0,1]
Case Q1: a = target crosses the dateline (minX > maxX), b = query does not; the query ratio uses
  b's width:   ratio_w = width / (b.maxX - b.minX)  (heights equal, minSideLength 0)
Case Q2: a = query crosses, b = target does not: targetRatio's width factor is width / bW too.
Both reduce to: find width > bW in FP64 with the code's operation order (round-to-nearest-even).
Inputs: -180 <= x <= 180, a.minX > a.maxX, b.minX <= b.maxX (validated rectangles).
"""
import sys, time
from z3 import *

RNE_ = RNE()
D = Float64()
def fp(name): return FP(name, D)
amin, amax, bmin, bmax = fp('amin'), fp('amax'), fp('bmin'), fp('bmax')
c180 = FPVal(180.0, D); m180 = FPVal(-180.0, D); zero = FPVal(0.0, D)
def fmax(x, y): return If(fpGT(x, y), x, y)   # Math.max on non-NaN, non-signed-zero-sensitive here
def fmin(x, y): return If(fpLT(x, y), x, y)
wl = fmax(amin, bmin); wr = bmax
el = bmin; er = fmin(amax, bmax)
p1 = If(fpLT(wl, wr), fpSub(RNE_, wr, wl), zero)
w1 = fpAdd(RNE_, zero, p1)
p2 = If(fpLT(el, er), fpSub(RNE_, er, el), zero)
width = fpAdd(RNE_, w1, p2)
bW = fpSub(RNE_, bmax, bmin)
s = Solver()
for v in (amin, amax, bmin, bmax):
    s.add(Not(fpIsNaN(v)), fpGEQ(v, m180), fpLEQ(v, c180))
s.add(fpGT(amin, amax), fpLEQ(bmin, bmax))
s.add(fpGT(width, bW))
for v in (amin, amax, bmin, bmax):
    s.add(Or(fpEQ(v, zero), fpGEQ(fpAbs(v), FPVal(1.0, D))))
s.set("timeout", 600000)
t0 = time.time(); r = s.check(); dt = time.time() - t0
print("query: exists validated (a crosses, b not) with width > b.width ?", r, f"({dt:.1f}s)")
if r == sat:
    m = s.model()
    vals = {str(v): float(m.eval(v).as_decimal(30).rstrip('?')) if False else m.eval(v) for v in (amin, amax, bmin, bmax)}
    for k, v in vals.items():
        print(k, v, "=", sys.float_info and v)

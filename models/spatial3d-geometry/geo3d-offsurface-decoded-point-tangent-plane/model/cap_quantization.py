"""
Z3 model: membership of a QUANTIZED (decoded) point in a small spherical cap, as Geo3DPoint queries evaluate it.

Model element -> Java (Lucene main @ ea57360912ed):
  cap test  n.q - cos(r) >= -1e-12        GeoStandardCircle.isWithin -> SidedPlane.isWithin (SidedPlane.java:301-309:
                                           |evaluate| < MINIMUM_RESOLUTION => within, else sign test); plane built in
                                           GeoStandardCircle.java:95-104 through the two rim points, so n.rim = cos(r)
  q = s + e, |e_i| <= DECODE/2            PlanetModel.encodeValue (floor(x/DECODE)) + decodeValue ((k+0.5)*DECODE),
                                           PlanetModel.java:274-315; used by PointInShapeIntersectVisitor.visit:77-92
  s on the unit sphere                    Geo3DPoint(lat, lon) indexes a surface point (SPHERE model; the WGS84 case is
                                           the same up to the 0.3% flattening)
  DECODE                                  PlanetModel.SPHERE.DECODE = 4.656612873077393E-10 (read from the jar)

Questions (n = cap centre direction):
  FP(theta0): is there a surface point s at angular distance >= theta0 from the centre whose decoded point passes
              the cap test?  sat => false positive at distance theta0.
  FN(theta1): is there a surface point s at angular distance <= theta1 (< r) whose decoded point FAILS the cap
              test?  sat => false negative.
Two centre orientations: n along an axis (n.e <= DECODE/2, best case) and n along a box diagonal
(n.e <= sqrt(3)*DECODE/2, worst case).  Analytic bound for r -> 0: theta_max = sqrt(2*(max n.e + 1e-12)).
Everything is exact rational arithmetic (nlsat); cosines are replaced by exact rational bounds (Taylor with the
correct-sign remainder) so that a sat answer at theta0 is sound and an unsat answer is sound for every theta >= theta0.
"""
import sys
from fractions import Fraction as Fr

from z3 import Real, Solver, sat, unsat, RealVal, Q

DECODE = Fr(4656612873077393, 10**25)  # 4.656612873077393E-10
TOL = Fr(1, 10**12)                    # Vector.MINIMUM_RESOLUTION
EARTH_M = 6371008.771415               # PlanetModel.WGS84 mean radius, only for printing metres


def cos_upper(t):
    # cos t <= 1 - t^2/2 + t^4/24
    t = Fr(t)
    return 1 - t * t / 2 + t ** 4 / 24


def cos_lower(t):
    # cos t >= 1 - t^2/2
    t = Fr(t)
    return 1 - t * t / 2


def rv(fr):
    return Q(fr.numerator, fr.denominator)


def solve(n, r, kind, theta):
    """n: tuple of Fractions with |n| = 1 (exactly representable choice); r, theta: Fractions (radians)."""
    sx, sy, sz, ex, ey, ez = (Real(v) for v in ("sx", "sy", "sz", "ex", "ey", "ez"))
    s = Solver()
    s.set("timeout", 120000)
    s.add(sx * sx + sy * sy + sz * sz == 1)
    h = rv(DECODE / 2)
    for e in (ex, ey, ez):
        s.add(e >= -h, e <= h)
    ns = rv(n[0]) * sx + rv(n[1]) * sy + rv(n[2]) * sz
    nq = rv(n[0]) * (sx + ex) + rv(n[1]) * (sy + ey) + rv(n[2]) * (sz + ez)
    # cos(r) is not rational; the plane offset is cos(r). For FP we need nq >= cos r - TOL: use the SMALLER cos
    # bound (makes the inside test harder to pass => a sat witness is sound). For FN use the LARGER bound.
    if kind == "FP":
        s.add(ns <= rv(cos_lower(theta)))           # angular distance >= theta (cos t <= cos_lower(theta)? no:)
        # cos is decreasing: angle >= theta  <=>  n.s <= cos(theta); cos(theta) >= cos_lower(theta), so requiring
        # n.s <= cos_lower(theta) is STRONGER than angle >= theta: sat witnesses are sound.
        s.add(nq >= rv(cos_upper(r)) - rv(TOL))
    else:
        s.add(ns >= rv(cos_upper(theta)))           # angle <= theta (stronger: cos_upper >= cos)
        s.add(nq < rv(cos_lower(r)) - rv(TOL))      # fails the test even against the smaller offset bound
    res = s.check()
    m = s.model() if res == sat else None
    return res, m


def frac_of(m, v):
    val = m[v]
    return Fr(val.numerator_as_long(), val.denominator_as_long())


def main():
    out = []
    from math import sqrt
    # exact unit vectors: axis (1,0,0); "diagonal-like" with rational coordinates (2/3, 2/3, 1/3) (|n|_1 = 5/3)
    centres = {"axis": (Fr(1), Fr(0), Fr(0)), "diag(2/3,2/3,1/3)": (Fr(2, 3), Fr(2, 3), Fr(1, 3))}
    for radius_m in (1.0, 10.0, 100.0):
        r = Fr(radius_m / EARTH_M).limit_denominator(10**15)
        for cname, n in centres.items():
            l1 = sum(abs(c) for c in n)
            tmax = sqrt(float(2 * (l1 * DECODE / 2 + TOL) + r * r))
            for factor in (0.95, 1.05):
                theta = Fr(tmax * factor).limit_denominator(10**15)
                res, m = solve(n, r, "FP", theta)
                line = (f"FP radius={radius_m:6.1f} m centre={cname:18s} theta0={float(theta):.3e} rad "
                        f"({float(theta) * EARTH_M:7.1f} m) = {factor:.2f} x analytic bound -> {res}")
                if m is not None:
                    sv = [frac_of(m, v) for v in m.decls() if v.name() in ("sx",)]
                    line += "  (witness found)"
                out.append(line)
            # false negative: a point at a quarter of the radius from the centre fails the cap test
            theta1 = r / 4
            res, m = solve(n, r, "FN", theta1)
            out.append(f"FN radius={radius_m:6.1f} m centre={cname:18s} point at <= r/4 ({float(theta1) * EARTH_M:.2f} m) "
                       f"excluded by quantization -> {res}")
    print("\n".join(out))


if __name__ == "__main__":
    main()

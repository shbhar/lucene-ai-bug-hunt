"""
SMT model (z3py) of ShapeField.encodeTriangle / decodeTriangle / resolveTriangleType.

Model element -> Java line (lucene/core/src/java/org/apache/lucene/document/ShapeField.java @ea57360912ed)
  rotate minX to a (two branches)       -> 157-182
  all-same-X degenerate rotation        -> 183-212
  CW -> CCW swap of b and c              -> 214-228   (STOCK: ab = bc ; FIXED: ab = ca)
  GeoUtils.orient (double products)      -> GeoUtils.java orient(): v1=(bx-ax)*(cy-ay), v2=(cx-ax)*(by-ay)
  bbox-corner code selection + throw     -> 230-274
  decode switch                          -> 291-364
  resolveTriangleType merge rules        -> 374-400

Queries
  Q1  flags, orientation = exact sign (orient=exact) or IEEE double (orient=ieee): is there an input whose
      decoded edges carry a flag different from the original edge with the same end points?
      STOCK: expected SAT (counterexample); FIXED + exact: expected UNSAT (proof for all 2^192 x 8 inputs).
      (A FREE orientation sign was tried first and is too weak: if orient lies, even the vertex set is not
      reconstructed, see Q4; logged as model fix 1.)
  Q1' flag invariant of the rotate/flip transformation with the orientation sign FREE (covers every rounding
      behaviour of orient): STOCK expected SAT, FIXED expected UNSAT. Together with vertex reconstruction
      (Q4, and the bounded-exhaustive Java run) this gives the flag property for decode(encode(t)).
  Q2  with orient modelled exactly as Java computes it (IEEE double products of int differences), can a
      non-degenerate triangle reach 'throw new IllegalArgumentException("Could not encode ...")'?
  Q3  with the IEEE orient: can the stored (decoded) triangle be strictly clockwise (exact orientation)?
  Q4  vertex reconstruction: decoded vertex multiset == input multiset unless it throws; orient=exact
      (expected UNSAT = lossless when orient is right) and orient=ieee (Java's double orient).
Run: python3 -I encode_triangle.py [q1|q2|q3|q4|all]
"""
import sys
import time
from z3 import (BitVec, BitVecVal, Bool, BoolVal, If, And, Or, Not, Solver, sat, unsat, Int, ZeroExt, SignExt,
                fpSignedToFP, FPSort, RNE, fpMul, fpGT, fpLT, fpEQ, BV2Int, set_param, Extract, simplify)

BV = 32


def smin(a, b):
    return If(a < b, a, b)


def smax(a, b):
    return If(a > b, a, b)


def orient_exact_sign(ax, ay, bx, by, cx, cy):
    """exact sign of (bx-ax)*(cy-ay) - (cx-ax)*(by-ay) using 68-bit arithmetic"""
    W = 68
    e = lambda v: SignExt(W - BV, v)
    v1 = (e(bx) - e(ax)) * (e(cy) - e(ay))
    v2 = (e(cx) - e(ax)) * (e(by) - e(ay))
    return If(v1 > v2, 1, If(v1 < v2, -1, 0)), v1 - v2


D = FPSort(11, 53)


def orient_double(ax, ay, bx, by, cx, cy):
    """GeoUtils.orient(double...) called with ints: int differences are exact in double (|d| < 2^33)"""
    W = 34
    e = lambda v: SignExt(W - BV, v)
    tofp = lambda bv: fpSignedToFP(RNE(), bv, D)
    v1 = fpMul(RNE(), tofp(e(bx) - e(ax)), tofp(e(cy) - e(ay)))
    v2 = fpMul(RNE(), tofp(e(cx) - e(ax)), tofp(e(by) - e(ay)))
    return If(fpGT(v1, v2), 1, If(fpLT(v1, v2), -1, 0))


def encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, orient_fn, fixed):
    """returns (minY, minX, maxY, maxX, y, x, code, ab, bc, ca, throws) as in encodeTriangle"""
    # rotate edges and place minX at the beginning
    rot = Or(bX < aX, cX < aX)
    br = bX < cX
    # branch 1 (bX < cX): a<-b, b<-c, c<-a ; ab<-bc, bc<-ca, ca<-ab
    # branch 2: a<-c, c<-b, b<-a ; ab<-ca, ca<-bc, bc<-ab
    n_aX = If(rot, If(br, bX, cX), aX)
    n_aY = If(rot, If(br, bY, cY), aY)
    n_bX = If(rot, If(br, cX, aX), bX)
    n_bY = If(rot, If(br, cY, aY), bY)
    n_cX = If(rot, If(br, aX, bX), cX)
    n_cY = If(rot, If(br, aY, bY), cY)
    n_ab = If(rot, If(br, bc, ca), ab)
    n_bc = If(rot, If(br, ca, ab), bc)
    n_ca = If(rot, If(br, ab, bc), ca)
    aX, aY, bX, bY, cX, cY, ab, bc, ca = n_aX, n_aY, n_bX, n_bY, n_cX, n_cY, n_ab, n_bc, n_ca
    # degenerate: all same X
    deg = And(Not(rot), aX == bX, aX == cX)
    rot2 = And(deg, Or(bY < aY, cY < aY))
    br2 = bY < cY
    n_aX = If(rot2, If(br2, bX, cX), aX)
    n_aY = If(rot2, If(br2, bY, cY), aY)
    n_bX = If(rot2, If(br2, cX, aX), bX)
    n_bY = If(rot2, If(br2, cY, aY), bY)
    n_cX = If(rot2, If(br2, aX, bX), cX)
    n_cY = If(rot2, If(br2, aY, bY), cY)
    n_ab = If(rot2, If(br2, bc, ca), ab)
    n_bc = If(rot2, If(br2, ca, ab), bc)
    n_ca = If(rot2, If(br2, ab, bc), ca)
    aX, aY, bX, bY, cX, cY, ab, bc, ca = n_aX, n_aY, n_bX, n_bY, n_cX, n_cY, n_ab, n_bc, n_ca
    # change orientation if CW
    o = orient_fn(aX, aY, bX, bY, cX, cY)
    flip = (o == -1)
    tb = ab
    n_ab = If(flip, ca if fixed else bc, ab)
    n_bX = If(flip, cX, bX)
    n_bY = If(flip, cY, bY)
    n_cX = If(flip, bX, cX)
    n_cY = If(flip, bY, cY)
    n_ca = If(flip, tb, ca)
    ab, bX, bY, cX, cY, ca = n_ab, n_bX, n_bY, n_cX, n_cY, n_ca
    global LAST_POST
    LAST_POST = ((aX, aY), (bX, bY), (cX, cY), (ab, bc, ca))
    minX = aX
    minY = smin(aY, smin(bY, cY))
    maxX = smax(aX, smax(bX, cX))
    maxY = smax(aY, smax(bY, cY))
    # code selection
    c_list = []
    # returns (y, x, code) via nested If; throws flag
    y = If(minY == aY,
           If(And(maxY == bY, maxX == bX), cY, If(And(maxY == cY, maxX == cX), bY, bY)),
           If(maxY == aY,
              If(And(minY == bY, maxX == bX), cY, If(And(minY == cY, maxX == cX), bY, cY)),
              If(And(maxX == bX, minY == bY), aY, aY)))
    x = If(minY == aY,
           If(And(maxY == bY, maxX == bX), cX, If(And(maxY == cY, maxX == cX), bX, cX)),
           If(maxY == aY,
              If(And(minY == bY, maxX == bX), cX, If(And(minY == cY, maxX == cX), bX, bX)),
              If(And(maxX == bX, minY == bY), cX, bX)))
    code = If(minY == aY,
              If(And(maxY == bY, maxX == bX), 0, If(And(maxY == cY, maxX == cX), 1, 7)),
              If(maxY == aY,
                 If(And(minY == bY, maxX == bX), 3, If(And(minY == cY, maxX == cX), 2, 6)),
                 If(And(maxX == bX, minY == bY), 5, 4)))
    throws = And(minY != aY, maxY != aY, Not(And(maxX == bX, minY == bY)), Not(And(maxX == cX, maxY == cY)))
    return (minY, minX, maxY, maxX, y, x, code, ab, bc, ca, throws)


def decode(enc):
    minY, minX, maxY, maxX, y, x, code, ab, bc, ca, _ = enc
    t = [minY, minX, maxY, maxX, y, x]  # slots 0..5 (Y,X,Y,X,Y,X)

    def sel(c, choices):
        # choices: dict code -> value
        r = choices[7]
        for k in range(6, -1, -1):
            r = If(code == k, choices[k], r)
        return r
    aY = sel(code, {0: t[0], 1: t[0], 2: t[2], 3: t[2], 4: t[4], 5: t[4], 6: t[2], 7: t[0]})
    aX = sel(code, {k: t[1] for k in range(8)})
    bY = sel(code, {0: t[2], 1: t[4], 2: t[4], 3: t[0], 4: t[0], 5: t[0], 6: t[0], 7: t[4]})
    bX = sel(code, {0: t[3], 1: t[5], 2: t[5], 3: t[3], 4: t[5], 5: t[3], 6: t[5], 7: t[3]})
    cY = sel(code, {0: t[4], 1: t[2], 2: t[0], 3: t[4], 4: t[2], 5: t[2], 6: t[4], 7: t[2]})
    cX = sel(code, {0: t[5], 1: t[3], 2: t[3], 3: t[5], 4: t[3], 5: t[5], 6: t[3], 7: t[5]})
    return aX, aY, ab, bX, bY, bc, cX, cY, ca


def eqp(p, q):
    return And(p[0] == q[0], p[1] == q[1])


def edge_flag_ok(orig, dec):
    """orig/dec: ((x,y),(x,y),(x,y)), flags (f01,f12,f20). For a decoded edge between two DISTINCT points,
    its flag must equal the OR of the flags of original edges joining the same two points (this is the
    resolveTriangleType merge rule; for distinct original vertices it is just the matching edge's flag)."""
    (P, F), (Q, G) = orig, dec
    conds = []
    for e in range(3):
        u, v = Q[e], Q[(e + 1) % 3]
        same_pts = []
        for oe in range(3):
            ou, ov = P[oe], P[(oe + 1) % 3]
            same_pts.append((Or(And(eqp(u, ou), eqp(v, ov)), And(eqp(u, ov), eqp(v, ou))), F[oe]))
        exp = Or([And(m, f) for m, f in same_pts])
        conds.append(If(eqp(u, v), BoolVal(True), exp == G[e]))
    return And(conds)


def mk_inputs():
    aX, aY, bX, bY, cX, cY = [BitVec(n, BV) for n in ("aX", "aY", "bX", "bY", "cX", "cY")]
    ab, bc, ca = Bool("ab"), Bool("bc"), Bool("ca")
    return aX, aY, ab, bX, bY, bc, cX, cY, ca


def orient_fn_for(mode):
    if mode == "exact":
        return lambda *a: orient_exact_sign(*a)[0]
    if mode == "ieee":
        return orient_double
    raise ValueError(mode)


def run_q1(fixed, mode="exact"):
    aX, aY, ab, bX, bY, bc, cX, cY, ca = mk_inputs()
    enc = encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, orient_fn_for(mode), fixed)
    dec = decode(enc)
    s = Solver()
    s.add(Not(enc[10]))
    distinct = And(Not(And(aX == bX, aY == bY)), Not(And(bX == cX, bY == cY)), Not(And(aX == cX, aY == cY)))
    s.add(distinct)
    orig = (((aX, aY), (bX, bY), (cX, cY)), (ab, bc, ca))
    d = (((dec[0], dec[1]), (dec[3], dec[4]), (dec[6], dec[7])), (dec[2], dec[5], dec[8]))
    s.add(Not(edge_flag_ok(orig, d)))
    t = time.time()
    r = s.check()
    out = f"Q1 flags fixed={fixed} orient={mode} (distinct vertices): {r} ({time.time() - t:.1f}s)"
    if r == sat:
        m = s.model()
        out += "\n   cex: " + str({str(k): m[k] for k in m.decls()})
    return out, r


def run_q1p(fixed):
    """Q1': flag invariant of encode's rotate/flip transformation, orientation sign FREE (any rounding):
    after the transformation, flag slot ab/bc/ca belongs to the edge (a,b)/(b,c)/(c,a) of the transformed
    triple. Comparisons only (no multiplication)."""
    aX, aY, ab, bX, bY, bc, cX, cY, ca = mk_inputs()
    o = Int("o_free")
    enc = encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, lambda *a: o, fixed)
    A, B, C, (fab, fbc, fca) = LAST_POST
    s = Solver()
    s.add(Or(o == -1, o == 0, o == 1))
    distinct = And(Not(And(aX == bX, aY == bY)), Not(And(bX == cX, bY == cY)), Not(And(aX == cX, aY == cY)))
    s.add(distinct)
    orig = (((aX, aY), (bX, bY), (cX, cY)), (ab, bc, ca))
    post = ((A, B, C), (fab, fbc, fca))
    s.add(Not(edge_flag_ok(orig, post)))
    t = time.time()
    r = s.check()
    out = f"Q1' flag invariant through rotate+flip fixed={fixed} (orient FREE, distinct vertices): {r} ({time.time() - t:.1f}s)"
    if r == sat:
        m = s.model()
        vals = {str(k): m[k] for k in m.decls()}
        out += "\n   cex: " + str({k: (v.as_signed_long() if hasattr(v, 'as_signed_long') else v) for k, v in vals.items()})
    return out, r


def run_q4(mode="exact"):
    aX, aY, ab, bX, bY, bc, cX, cY, ca = mk_inputs()
    enc = encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, orient_fn_for(mode), False)
    dec = decode(enc)
    s = Solver()
    s.add(Not(enc[10]))
    P = [(aX, aY), (bX, bY), (cX, cY)]
    Q = [(dec[0], dec[1]), (dec[3], dec[4]), (dec[6], dec[7])]
    # multiset equality: Q is a permutation of P
    import itertools
    perms = [And([eqp(Q[i], P[p[i]]) for i in range(3)]) for p in itertools.permutations(range(3))]
    s.add(Not(Or(perms)))
    t = time.time()
    r = s.check()
    out = f"Q4 vertex multiset preserved orient={mode}: mismatch {r} ({time.time() - t:.1f}s)"
    if r == sat:
        m = s.model()
        vals = {str(k): m[k] for k in m.decls()}
        out += "\n   cex: " + str({k: (v.as_signed_long() if hasattr(v, 'as_signed_long') else v) for k, v in vals.items()})
    return out, r


def run_q2():
    aX, aY, ab, bX, bY, bc, cX, cY, ca = mk_inputs()
    enc = encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, orient_double, False)
    s = Solver()
    s.add(enc[10])
    sg, _ = orient_exact_sign(aX, aY, bX, bY, cX, cY)
    s.add(sg != 0)  # non-degenerate triangle
    t = time.time()
    r = s.check()
    out = f"Q2 'Could not encode' reachable for a non-degenerate triangle (IEEE orient): {r} ({time.time() - t:.1f}s)"
    if r == sat:
        m = s.model()
        vals = {str(k): m[k] for k in m.decls()}
        out += "\n   cex: " + str({k: (v.as_signed_long() if hasattr(v, 'as_signed_long') else v) for k, v in vals.items()})
    return out, r


def run_q3():
    aX, aY, ab, bX, bY, bc, cX, cY, ca = mk_inputs()
    enc = encode(aX, aY, ab, bX, bY, bc, cX, cY, ca, orient_double, False)
    dec = decode(enc)
    s = Solver()
    s.add(Not(enc[10]))
    sg, _ = orient_exact_sign(dec[0], dec[1], dec[3], dec[4], dec[6], dec[7])
    s.add(sg == -1)
    t = time.time()
    r = s.check()
    out = f"Q3 stored triangle strictly CW (IEEE orient): {r} ({time.time() - t:.1f}s)"
    if r == sat:
        m = s.model()
        vals = {str(k): m[k] for k in m.decls()}
        out += "\n   cex: " + str({k: (v.as_signed_long() if hasattr(v, 'as_signed_long') else v) for k, v in vals.items()})
    return out, r


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    res = []
    if which in ("q1p", "all"):
        res.append(run_q1p(False)[0])
        res.append(run_q1p(True)[0])
    if which in ("q1", "all"):
        res.append(run_q1(False, "exact")[0])
        res.append(run_q1(True, "exact")[0])
    if which in ("q1ieee", "all"):
        res.append(run_q1(True, "ieee")[0])
    if which in ("q4", "all"):
        res.append(run_q4("exact")[0])
    if which in ("q4ieee", "all"):
        res.append(run_q4("ieee")[0])
    if which in ("q3", "all"):
        res.append(run_q3()[0])
    if which in ("q2", "all"):
        res.append(run_q2()[0])
    for r in res:
        print(r, flush=True)

# int4BitDotProduct / int4DibitDotProduct: compositional proof.
# Lemma A (QF_BV): popcount of an AND of 4-byte / 8-byte lanes (any byte order) == sum of per-byte
#   popcounts of the byte-wise ANDs.  Hence every lane popcount in either implementation equals a sum
#   of the per-byte terms c[s][k] = bitCount(q[s*stripe + k] & d[dOff + k]) in [0, 8].
# Structure (cvc5 --solve-bv-as-int=sum): with c[s][k] free 64-bit values in [0, 8], the exact
#   Panama loop/tail/reduction/weighting structure (int4BitDotProduct256WithOffset /
#   int4BitDotProduct128WithOffset, PanamaVectorUtilSupport ~L1384-1452) equals the scalar
#   DefaultVectorUtilSupport.int4BitDotProductImpl structure, for every stripe size listed.
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa

def lemma_a():
    ok = True
    for nb in (4, 8):
        x = sym("x", nb, 8); y = sym("y", nb, 8)
        for order in ("le", "be"):
            xs = list(reversed(x)) if order == "le" else x
            ys = list(reversed(y)) if order == "le" else y
            lhs = popcount(z3.Concat(*xs) & z3.Concat(*ys))
            rhs = reduce_add([popcount(a & b) for a, b in zip(x, y)])
            s = z3.Solver(); s.add(lhs != rhs)
            r = s.check(); ok &= (r == z3.unsat)
            print(f"LEMMA {'PROVED' if r == z3.unsat else 'FAILED'}: bitCount({nb*8}-bit lane AND, {order}) == sum of {nb} byte bitCounts", flush=True)
    return ok

def scalar(c, stripe):
    ret = bvv(0, 64)
    for i in range(4):
        sub = bvv(0, 64); r = 0; ub = stripe & -4
        while r < ub:
            sub = sub + reduce_add([c[i][r + t] for t in range(4)]); r += 4
        while r < stripe:
            sub = sub + c[i][r]; r += 1
        ret = ret + (sub << i)
    return ret

def v256(c, stripe):
    sub = [bvv(0, 64)] * 4; i = 0
    if stripe >= 64:
        limit = java_loop_bound(stripe, 32)
        acc = [[bvv(0, 64)] * 4 for _ in range(4)]
        while i < limit:
            for s in range(4):  # 4 long lanes of 8 bytes each
                acc[s] = [acc[s][L] + reduce_add([c[s][i + 8 * L + t] for t in range(8)]) for L in range(4)]
            i += 32
        sub = [sub[s] + reduce_add(acc[s]) for s in range(4)]
    if stripe - i >= 16:
        limit = java_loop_bound(stripe, 16)
        acc = [[bvv(0, 64)] * 2 for _ in range(4)]
        while i < limit:
            for s in range(4):
                acc[s] = [acc[s][L] + reduce_add([c[s][i + 8 * L + t] for t in range(8)]) for L in range(2)]
            i += 16
        sub = [sub[s] + reduce_add(acc[s]) for s in range(4)]
    while i < stripe:
        for s in range(4):
            sub[s] = sub[s] + c[s][i]
        i += 1
    return sub[0] + (sub[1] << 1) + (sub[2] << 2) + (sub[3] << 3)

def v128(c, stripe):
    acc = [[bvv(0, 32)] * 4 for _ in range(4)]; i = 0
    limit = java_loop_bound(stripe, 16)
    while i < limit:
        for s in range(4):  # 4 int lanes of 4 bytes, BIT_COUNT in int lanes
            acc[s] = [acc[s][L] + trunc(reduce_add([c[s][i + 4 * L + t] for t in range(4)]), 32) for L in range(4)]
        i += 16
    sub = [sext(reduce_add(acc[s]), 64) for s in range(4)]
    while i < stripe:
        for s in range(4):
            sub[s] = sub[s] + c[s][i]
        i += 1
    return sub[0] + (sub[1] << 1) + (sub[2] << 2) + (sub[3] << 3)

def main():
    assert lemma_a()
    stripes = list(range(0, 200)) + [255, 256, 257, 511, 512, 513]
    for VB, f in ((128, v128), (256, v256)):
        t0 = time.time(); ok = True
        for st in stripes:
            c = [[z3.BitVec(f"c{s}_{k}", 64) for k in range(st)] for s in range(4)]
            bounds = [z3.ULE(x, bvv(8, 64)) for row in c for x in row]
            sc = scalar(c, st)
            vc = f(c, st) if st >= 16 else sc   # Panama dispatch: d.length >= 16 (stripeSize >= 16 for dibit)
            r = cvc5_check(bounds + [vc != sc], f"int4bitabs_VB{VB}_st{st}")
            if r != "unsat":
                ok = False; print(f"FAIL VB={VB} stripe={st}: {r}", flush=True); break
        if ok:
            print(f"PROVED int4BitDotProduct{VB if VB > 128 else 128}WithOffset == int4BitDotProductImpl for every stripe size in 0..199 and {stripes[200:]} (covers int4BitDotProduct d.length=stripe and int4DibitDotProduct's two stripes) ({time.time()-t0:.1f}s)", flush=True)

main()

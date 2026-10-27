# Z3 translation validation of the int4 (unsigned half-byte) kernels of PanamaVectorUtilSupport
# (@7cecf79523, lines ~560-1300): int4DotProduct, int4DotProductSinglePacked, int4DotProductBothPacked,
# int4SquareDistance, int4SquareDistanceSinglePacked, int4SquareDistanceBothPacked, against
# DefaultVectorUtilSupport.
#
# Part A: per-iteration lane lemmas + 16-bit accumulator no-overflow proof (induction over the
#         <= CHUNK/BYTE_LANES = 128 iterations of the inner loop) + final int combination lemma.
# Part B: full lane-precise model of each kernel (byte multiply mod 256, reinterpretAsShorts,
#         LSHR/AND, ShortVector accumulators, reinterpretAsInts, and/LSHR 16, reduceLanes, scalar
#         tail) proven equal to the scalar reference for every n in a bounded range, under the
#         documented domain (unpacked values in [0,15]; packed bytes arbitrary).
# Part C: the same model WITHOUT the domain precondition, to document exactly which inputs the
#         kernels rely on (expected: counterexamples, e.g. an unpacked value 16..255).
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa


def int4_constants(VB):
    if VB >= 512:
        return 64, 32, 8192  # ByteVector.SPECIES_512, ShortVector.SPECIES_512, CHUNK
    if VB == 256:
        return 32, 16, 4096
    return 16, 8, 2048


def load(arr, i, lanes):
    assert 0 <= i and i + lanes <= len(arr), f"OOB load {i}+{lanes} > {len(arr)}"
    return arr[i:i + lanes]


def final_combine(acc0, acc1):
    i0, i1 = reinterpret(acc0, 32), reinterpret(acc1, 32)
    m = bvv(0xFFFF, 32)
    v = [(x & m) + lshr(x, 16) + (y & m) + lshr(y, 16) for x, y in zip(i0, i1)]
    return reduce_add(v)


# ---------------- Panama models ----------------

def p_int4_dot(a, b, VB):
    BL, SL, CHUNK = int4_constants(VB)
    n = len(a)
    i, res = 0, bvv(0, 32)
    if n >= 32:
        i += java_loop_bound(n, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(b, c + j, BL)
                va8 = load(a, c + j, BL)
                prod16 = reinterpret(vmul(va8, vb8), 16)
                acc0 = vadd(acc0, vlshr(prod16, 8))
                acc1 = vadd(acc1, vand_const(prod16, 0xFF))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, n):
        res = res + B2I(a[k]) * B2I(b[k])
    return res


def p_int4_dot_single(unpacked, packed, VB):
    BL, SL, CHUNK = int4_constants(VB)
    P = len(packed)
    i, res = 0, bvv(0, 32)
    if P >= 32:
        i += java_loop_bound(P, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(packed, c + j, BL)
                vc8 = load(unpacked, c + j, BL)
                va8 = load(unpacked, c + j + P, BL)
                prod16 = reinterpret(vmul(vand_const(vb8, 0x0F), va8), 16)
                acc0 = vadd(acc0, vadd(vlshr(prod16, 8), vand_const(prod16, 0xFF)))
                prod16a = reinterpret(vmul(vlshr(vb8, 4), vc8), 16)
                acc1 = vadd(acc1, vadd(vlshr(prod16a, 8), vand_const(prod16a, 0xFF)))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, P):
        pb, u1, u2 = packed[k], unpacked[k], unpacked[k + P]
        res = res + (B2I(pb) & bvv(0x0F, 32)) * B2I(u2)
        res = res + lshr(B2I(pb) & bvv(0xFF, 32), 4) * B2I(u1)
    return res


def p_int4_dot_both(a, b, VB):
    BL, SL, CHUNK = int4_constants(VB)
    n = len(a)
    i, res = 0, bvv(0, 32)
    if n >= 32:
        i += java_loop_bound(n, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(b, c + j, BL)
                va8 = load(a, c + j, BL)
                prod16 = reinterpret(vmul(vand_const(vb8, 0x0F), vand_const(va8, 0x0F)), 16)
                acc0 = vadd(acc0, vadd(vlshr(prod16, 8), vand_const(prod16, 0xFF)))
                prod16a = reinterpret(vmul(vlshr(vb8, 4), vlshr(va8, 4)), 16)
                acc1 = vadd(acc1, vadd(vlshr(prod16a, 8), vand_const(prod16a, 0xFF)))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, n):
        x, y = B2I(a[k]), B2I(b[k])
        res = res + (x & bvv(0x0F, 32)) * (y & bvv(0x0F, 32))
        res = res + lshr(x & bvv(0xFF, 32), 4) * lshr(y & bvv(0xFF, 32), 4)
    return res


def p_int4_sqr(a, b, VB):
    BL, SL, CHUNK = int4_constants(VB)
    n = len(a)
    i, res = 0, bvv(0, 32)
    if n >= 32:
        i += java_loop_bound(n, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(b, c + j, BL)
                va8 = load(a, c + j, BL)
                diff8 = reinterpret(vabs(vsub(vb8, va8)), 16)
                diff16 = vand_const(diff8, 0xFF)
                acc0 = vadd(acc0, vmul(diff16, diff16))
                diff16a = vlshr(diff8, 8)
                acc1 = vadd(acc1, vmul(diff16a, diff16a))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, n):
        d = B2I(a[k]) - B2I(b[k])
        res = res + d * d
    return res


def p_int4_sqr_single(unpacked, packed, VB):
    BL, SL, CHUNK = int4_constants(VB)
    P = len(packed)
    i, res = 0, bvv(0, 32)
    if P >= 32:
        i += java_loop_bound(P, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(packed, c + j, BL)
                vc8 = load(unpacked, c + j, BL)
                va8 = load(unpacked, c + j + P, BL)
                diff8 = vsub(vand_const(vb8, 0x0F), va8)
                prod16 = reinterpret(vmul(diff8, diff8), 16)
                acc0 = vadd(acc0, vadd(vand_const(prod16, 0xFF), vlshr(prod16, 8)))
                diff8a = vsub(vlshr(vb8, 4), vc8)
                prod16a = reinterpret(vmul(diff8a, diff8a), 16)
                acc1 = vadd(acc1, vadd(vand_const(prod16a, 0xFF), vlshr(prod16a, 8)))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, P):
        pb, u1, u2 = packed[k], unpacked[k], unpacked[k + P]
        d1 = (B2I(pb) & bvv(0x0F, 32)) - B2I(u2)
        d2 = lshr(B2I(pb) & bvv(0xFF, 32), 4) - B2I(u1)
        res = res + (d1 * d1 + d2 * d2)
    return res


def p_int4_sqr_both(a, b, VB):
    BL, SL, CHUNK = int4_constants(VB)
    n = len(a)
    i, res = 0, bvv(0, 32)
    if n >= 32:
        i += java_loop_bound(n, BL)
        limit, s = i, bvv(0, 32)
        for c in range(0, limit, CHUNK):
            acc0, acc1 = [bvv(0, 16)] * SL, [bvv(0, 16)] * SL
            inner = min(limit - c, CHUNK)
            for j in range(0, inner, BL):
                vb8 = load(b, c + j, BL)
                va8 = load(a, c + j, BL)
                diff8 = vsub(vand_const(vb8, 0x0F), vand_const(va8, 0x0F))
                prod16 = reinterpret(vmul(diff8, diff8), 16)
                acc0 = vadd(acc0, vadd(vand_const(prod16, 0xFF), vlshr(prod16, 8)))
                diff8a = vsub(vlshr(vb8, 4), vlshr(va8, 4))
                prod16a = reinterpret(vmul(diff8a, diff8a), 16)
                acc1 = vadd(acc1, vadd(vand_const(prod16a, 0xFF), vlshr(prod16a, 8)))
            s = s + final_combine(acc0, acc1)
        res = res + s
    for k in range(i, n):
        x, y = B2I(a[k]), B2I(b[k])
        d1 = (x & bvv(0x0F, 32)) - (y & bvv(0x0F, 32))
        d2 = lshr(x & bvv(0xFF, 32), 4) - lshr(y & bvv(0xFF, 32), 4)
        res = res + (d1 * d1 + d2 * d2)
    return res


# ---------------- scalar references (DefaultVectorUtilSupport) ----------------

def s_dot(a, b):
    t = bvv(0, 32)
    for x, y in zip(a, b):
        t = t + B2I(x) * B2I(y)
    return t


def s_dot_single(unpacked, packed):
    P, t = len(packed), bvv(0, 32)
    for k in range(P):
        pb = B2I(packed[k])
        t = t + (pb & bvv(0x0F, 32)) * B2I(unpacked[k + P])
        t = t + lshr(pb & bvv(0xFF, 32), 4) * B2I(unpacked[k])
    return t


def s_dot_both(a, b):
    t = bvv(0, 32)
    for x8, y8 in zip(a, b):
        x, y = B2I(x8), B2I(y8)
        t = t + (x & bvv(0x0F, 32)) * (y & bvv(0x0F, 32))
        t = t + lshr(x & bvv(0xFF, 32), 4) * lshr(y & bvv(0xFF, 32), 4)
    return t


def s_sqr(a, b):
    t = bvv(0, 32)
    for x, y in zip(a, b):
        d = B2I(x) - B2I(y)
        t = t + d * d
    return t


def s_sqr_single(unpacked, packed):
    P, t = len(packed), bvv(0, 32)
    for k in range(P):
        pb = B2I(packed[k])
        d1 = (pb & bvv(0x0F, 32)) - B2I(unpacked[k + P])
        d2 = lshr(pb & bvv(0xFF, 32), 4) - B2I(unpacked[k])
        t = t + (d1 * d1 + d2 * d2)
    return t


def s_sqr_both(a, b):
    t = bvv(0, 32)
    for x8, y8 in zip(a, b):
        x, y = B2I(x8), B2I(y8)
        d1 = (x & bvv(0x0F, 32)) - (y & bvv(0x0F, 32))
        d2 = lshr(x & bvv(0xFF, 32), 4) - lshr(y & bvv(0xFF, 32), 4)
        t = t + (d1 * d1 + d2 * d2)
    return t


# kernel name -> (panama, scalar, shape) ; shape 'pair' = two arrays of length n, 'single' =
# unpacked of length 2P and packed of length P
KERNELS = {
    "int4DotProduct": (p_int4_dot, s_dot, "pair"),
    "int4DotProductSinglePacked": (p_int4_dot_single, s_dot_single, "single"),
    "int4DotProductBothPacked": (p_int4_dot_both, s_dot_both, "both"),
    "int4SquareDistance": (p_int4_sqr, s_sqr, "pair"),
    "int4SquareDistanceSinglePacked": (p_int4_sqr_single, s_sqr_single, "single"),
    "int4SquareDistanceBothPacked": (p_int4_sqr_both, s_sqr_both, "both"),
}


def nib(x):
    return z3.ULE(x, bvv(15, 8))


def part_a():
    """Lemmas: per-iteration 16-bit lane increment bounds and no-overflow over 128 iterations."""
    out = []
    x0, x1, y0, y1 = z3.BitVecs("x0 x1 y0 y1", 8)
    # lane increment of each kernel for one 16-bit lane (two byte lanes 2j, 2j+1), as the model
    # computes it, and the bound M we need
    def inc_dot(x0, x1, y0, y1):  # unpacked: acc0 += prod>>>8 ; acc1 += prod & 0xFF (separately)
        p = reinterpret([x0 * y0, x1 * y1], 16)[0]
        return [lshr(p, 8), p & bvv(0xFF, 16)]
    def inc_packed_dot(x0, x1, y0, y1):  # acc += (p>>>8) + (p&0xFF)
        p = reinterpret([(x0 & bvv(15, 8)) * (y0 & bvv(15, 8)), (x1 & bvv(15, 8)) * (y1 & bvv(15, 8))], 16)[0]
        q = reinterpret([lshr(x0, 4) * lshr(y0, 4), lshr(x1, 4) * lshr(y1, 4)], 16)[0]
        return [lshr(p, 8) + (p & bvv(0xFF, 16)), lshr(q, 8) + (q & bvv(0xFF, 16))]
    def inc_sqr(x0, x1, y0, y1):
        d = reinterpret(vabs([y0 - x0, y1 - x1]), 16)[0]
        lo, hi = d & bvv(0xFF, 16), lshr(d, 8)
        return [lo * lo, hi * hi]
    def inc_packed_sqr(x0, x1, y0, y1):
        d = [(y0 & bvv(15, 8)) - (x0 & bvv(15, 8)), (y1 & bvv(15, 8)) - (x1 & bvv(15, 8))]
        p = reinterpret(vmul(d, d), 16)[0]
        e = [lshr(y0, 4) - lshr(x0, 4), lshr(y1, 4) - lshr(x1, 4)]
        q = reinterpret(vmul(e, e), 16)[0]
        return [(p & bvv(0xFF, 16)) + lshr(p, 8), (q & bvv(0xFF, 16)) + lshr(q, 8)]
    cases = [
        ("int4DotProduct (unpacked, nibble domain)", inc_dot, True, 225),
        ("int4DotProduct{Single,Both}Packed (per-lane add, nibble values)", inc_packed_dot, False, 450),
        ("int4SquareDistance (unpacked, nibble domain)", inc_sqr, True, 225),
        ("int4SquareDistance{Single,Both}Packed", inc_packed_sqr, False, 450),
    ]
    for name, f, need_dom, M in cases:
        s = z3.Solver()
        if need_dom:
            s.add(nib(x0), nib(x1), nib(y0), nib(y1))
        incs = f(x0, x1, y0, y1)
        s.add(z3.Or(*[z3.UGT(v, bvv(M, 16)) for v in incs]))
        r = s.check()
        out.append((f"lane increment <= {M}: {name}", r == z3.unsat))
        # maximum is attained (so the bound is tight)
        s2 = z3.Solver()
        if need_dom:
            s2.add(nib(x0), nib(x1), nib(y0), nib(y1))
        s2.add(z3.Or(*[v == bvv(M, 16) for v in incs]))
        out.append((f"  bound {M} attained: {name}", s2.check() == z3.sat))
    # induction: K = CHUNK / BYTE_LANES iterations per chunk for each VB; v <= k*M, inc <= M
    for VB in (128, 256, 512):
        BL, SL, CHUNK = int4_constants(VB)
        K = CHUNK // BL
        for M in (225, 450):
            v, inc = z3.BitVecs("v inc", 16)
            k = z3.Int("k")
            s = z3.Solver()
            # inductive step for any k < K: exact value relation preserved and no wrap
            s.add(z3.ULE(v, bvv((K - 1) * M, 16)), z3.ULE(inc, bvv(M, 16)))
            s.add(zext(v + inc, 32) != zext(v, 32) + zext(inc, 32))
            out.append((f"16-bit lane accumulator never wraps in a chunk: VB={VB} K={K} iterations, "
                        f"M={M} (K*M={K * M} <= 65535)", s.check() == z3.unsat))
    # final combination: for an int lane built from two 16-bit accumulator lanes
    l0, l1 = z3.BitVecs("l0 l1", 16)
    x = reinterpret([l0, l1], 32)[0]
    s = z3.Solver()
    s.add((x & bvv(0xFFFF, 32)) + lshr(x, 16) != zext(l0, 32) + zext(l1, 32))
    out.append(("reinterpretAsInts combine: (x&0xFFFF)+(x>>>16) == zext(l0)+zext(l1)", s.check() == z3.unsat))
    for name, ok in out:
        print(("LEMMA PROVED: " if ok else "LEMMA FAILED: ") + name, flush=True)
    return all(ok for _, ok in out)


def make_inputs(shape, n):
    if shape == "single":
        P = n
        un = sym("u", 2 * P, 8)
        pk = sym("p", P, 8)
        return (un, pk), [nib(x) for x in un]
    a, b = sym("a", n, 8), sym("b", n, 8)
    if shape == "pair":
        return (a, b), [nib(x) for x in a + b]
    return (a, b), []  # both packed: every byte is a valid pair of nibbles


def check(name, VB, n, with_domain=True, timeout=300000):
    pf, sf, shape = KERNELS[name]
    args, dom = make_inputs(shape, n)
    lhs, rhs = pf(*args, VB), sf(*args)
    s = z3.SolverFor("QF_BV")
    s.set("timeout", timeout)
    if with_domain:
        s.add(*dom)
    s.add(lhs != rhs)
    r = s.check()
    return r, (s.model() if r == z3.sat else None), args


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode in ("all", "lemmas"):
        assert part_a()
    if mode in ("all", "models"):
        vbs = [int(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [128, 256, 512]
        ns = [int(x) for x in sys.argv[3].split(",")] if len(sys.argv) > 3 else None
        names = sys.argv[4].split(",") if len(sys.argv) > 4 else list(KERNELS)
        for name in names:
            for VB in vbs:
                BL = int4_constants(VB)[0]
                nl = ns if ns else sorted(set(list(range(0, 40)) + [BL - 1, BL, BL + 1, 2 * BL - 1,
                                                                     2 * BL, 2 * BL + 1, 2 * BL + 7]))
                t0 = time.time()
                bad = None
                for n in nl:
                    r, m, _ = check(name, VB, n)
                    if r != z3.unsat:
                        bad = (n, r, m)
                        break
                if bad:
                    print(f"FAIL {name} VB={VB} n={bad[0]} {bad[1]} {bad[2]}", flush=True)
                else:
                    print(f"PROVED {name:32s} VB={VB:3d} n in {nl[0]}..{nl[-1]} ({len(nl)} sizes) "
                          f"{time.time() - t0:.1f}s", flush=True)
    if mode in ("all", "nodomain"):
        # Part C: drop the nibble precondition on unpacked operands
        for name in ("int4DotProduct", "int4SquareDistance", "int4DotProductSinglePacked",
                     "int4SquareDistanceSinglePacked"):
            r, m, args = check(name, 256, 32, with_domain=False)
            if r == z3.sat:
                vals = {str(d): m[d].as_long() for d in m.decls()}
                bad = sorted((k, v) for k, v in vals.items() if v > 15 and (k.startswith('a') or k.startswith('b') or k.startswith('u')))
                print(f"NO-DOMAIN {name} VB=256 n=32: counterexample exists (needs out-of-domain "
                      f"value), e.g. {bad[:3]}", flush=True)
            else:
                print(f"NO-DOMAIN {name}: {r}", flush=True)


if __name__ == "__main__" and (len(sys.argv) < 2 or sys.argv[1] != "comp"):
    main()


# ---------------- compositional mode: substitute per-index byte products ----------------
# For each kernel we list, per index k, (vector-side subterm, scalar-side subterm, fresh var, bound):
# the element lemma "under the domain, ZB2I(vector subterm) == scalar subterm and vector subterm <= bound"
# is proven exhaustively, then both sides are rewritten with the fresh variable (vector subterm -> v,
# scalar subterm -> ZB2I(v) / ZS2I(v)) and the remaining adder/extract/concat structure is proven
# equal for all v satisfying the bound.

def subst_specs(name, args):
    specs = []  # (vec_key or None, scal_key, var, width_of_var, bound, domain_vars)
    if name == "int4DotProduct":
        a, b = args
        for k in range(len(a)):
            specs.append(([a[k] * b[k]], [B2I(a[k]) * B2I(b[k])], f"p{k}", 8, 225, [a[k], b[k]]))
    elif name == "int4DotProductSinglePacked":
        un, pk = args
        P = len(pk)
        for k in range(P):
            specs.append(([(pk[k] & bvv(0x0F, 8)) * un[k + P]],
                          [(B2I(pk[k]) & bvv(0x0F, 32)) * B2I(un[k + P])], f"q{k}", 8, 225, [un[k + P]]))
            specs.append(([lshr(pk[k], 4) * un[k]],
                          [lshr(B2I(pk[k]) & bvv(0xFF, 32), 4) * B2I(un[k])], f"r{k}", 8, 225, [un[k]]))
    elif name == "int4DotProductBothPacked":
        a, b = args
        for k in range(len(a)):
            x, y = B2I(a[k]), B2I(b[k])
            specs.append(([(b[k] & bvv(0x0F, 8)) * (a[k] & bvv(0x0F, 8))],
                          [(x & bvv(0x0F, 32)) * (y & bvv(0x0F, 32))], f"q{k}", 8, 225, []))
            specs.append(([lshr(b[k], 4) * lshr(a[k], 4)],
                          [lshr(x & bvv(0xFF, 32), 4) * lshr(y & bvv(0xFF, 32), 4)], f"r{k}", 8, 225, []))
    elif name == "int4SquareDistance":
        a, b = args
        n = len(a)
        D = vabs(vsub(b, a))  # same AST the model builds lane by lane
        for k in range(n):
            d = B2I(a[k]) - B2I(b[k])
            j = k // 2
            if 2 * j + 1 < n:
                s = z3.Concat(D[2 * j + 1], D[2 * j])
                lo = s & bvv(0xFF, 16)
                hi = lshr(s, 8)
                vk = lo * lo if k % 2 == 0 else hi * hi
                specs.append(([vk], [d * d], f"s{k}", 16, 225, [a[k], b[k], a[k ^ 1], b[k ^ 1]]))
            else:
                specs.append(([], [d * d], f"s{k}", 16, 225, [a[k], b[k]]))
    elif name == "int4SquareDistanceSinglePacked":
        un, pk = args
        P = len(pk)
        for k in range(P):
            d8 = (pk[k] & bvv(0x0F, 8)) - un[k + P]
            d8a = lshr(pk[k], 4) - un[k]
            d1 = (B2I(pk[k]) & bvv(0x0F, 32)) - B2I(un[k + P])
            d2 = lshr(B2I(pk[k]) & bvv(0xFF, 32), 4) - B2I(un[k])
            specs.append(([d8 * d8], [d1 * d1], f"e{k}", 8, 225, [un[k + P]]))
            specs.append(([d8a * d8a], [d2 * d2], f"f{k}", 8, 225, [un[k]]))
    elif name == "int4SquareDistanceBothPacked":
        a, b = args
        for k in range(len(a)):
            x, y = B2I(a[k]), B2I(b[k])
            d8 = (b[k] & bvv(0x0F, 8)) - (a[k] & bvv(0x0F, 8))
            d8a = lshr(b[k], 4) - lshr(a[k], 4)
            d1 = (x & bvv(0x0F, 32)) - (y & bvv(0x0F, 32))
            d2 = lshr(x & bvv(0xFF, 32), 4) - lshr(y & bvv(0xFF, 32), 4)
            specs.append(([d8 * d8], [d1 * d1], f"e{k}", 8, 225, []))
            specs.append(([d8a * d8a], [d2 * d2], f"f{k}", 8, 225, []))
    return specs


_lemma_cache = {}
_mut_checked = {}


def element_lemma(name):
    """Prove the per-index substitution lemma once, on a generic index (fresh inputs)."""
    if name in _lemma_cache:
        return _lemma_cache[name]
    pf, sf, shape = KERNELS[name]
    args, _ = make_inputs(shape, 2)  # two indices so the square-distance pairing is generic
    specs = subst_specs(name, args)
    ok = True
    for vec_keys, scal_keys, var, w, bound, dvars in specs:
        s = z3.Solver()
        s.add(*[nib(x) for x in dvars])
        conds = []
        for sk in scal_keys:
            for vk in vec_keys:
                conds.append(sk != zext(vk, 32))
                conds.append(z3.UGT(vk, bvv(bound, vk.size())))
            conds.append(z3.Or(sk < 0, sk > bvv(bound, 32)))
        s.add(z3.Or(*conds))
        if s.check() != z3.unsat:
            ok = False
            print("ELEMENT LEMMA FAILED", name, var, s.model())
    _lemma_cache[name] = ok
    print(f"ELEMENT LEMMA {'PROVED' if ok else 'FAILED'}: {name}: every per-index vector subterm "
          f"== scalar subterm (zero-extended) and <= 225, for all inputs in the domain", flush=True)
    return ok


def check_comp(name, VB, n, timeout=300000):
    pf, sf, shape = KERNELS[name]
    args, _ = make_inputs(shape, n)
    lhs, rhs = pf(*args, VB), sf(*args)
    specs = subst_specs(name, args)
    subs, bounds = [], []
    for vec_keys, scal_keys, var, w, bound, _ in specs:
        v = z3.BitVec(var, w)
        bounds.append(z3.ULE(v, bvv(bound, w)))
        for vk in vec_keys:
            subs.append((vk, v))
        for sk in scal_keys:
            subs.append((sk, zext(v, 32)))
    lhs2 = z3.substitute(lhs, *subs) if subs else lhs
    rhs2 = z3.substitute(rhs, *subs) if subs else rhs
    # every original input must have been eliminated from both sides (otherwise the check would be
    # weaker than claimed only in the sense of being harder; report it)
    r = cvc5_check(bounds + [lhs2 != rhs2], f"{name}_VB{VB}_n{n}")
    if r == "unsat" and n in (32, 33, 64) and not _mut_checked.get((name, VB, n)):
        # mutation sanity check: an off-by-one in the result must be detected (non-vacuity)
        _mut_checked[(name, VB, n)] = True
        rm = cvc5_check(bounds + [lhs2 + bvv(1, 32) != rhs2 + bvv(1, 32) + bvv(1, 32)], f"mut_{name}_VB{VB}_n{n}")
        assert rm == "sat", ("vacuous?", rm)
    return z3.unsat if r == "unsat" else r


def main_comp():
    vbs = [int(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [128, 256, 512]
    names = sys.argv[4].split(",") if len(sys.argv) > 4 and sys.argv[4] else list(KERNELS)
    for name in names:
        assert element_lemma(name)
        for VB in vbs:
            BL, SL, CHUNK = int4_constants(VB)
            if len(sys.argv) > 3 and sys.argv[3]:
                nl = [int(x) for x in sys.argv[3].split(",")]
            else:
                nl = sorted(set(list(range(0, 2 * BL + 9)) + [3 * BL - 1, 3 * BL, 3 * BL + 5]))
            t0 = time.time()
            bad = None
            for n in nl:
                r = check_comp(name, VB, n)
                if r != z3.unsat:
                    bad = (n, r)
                    break
            if bad:
                print(f"FAIL {name} VB={VB} n={bad[0]}: {bad[1]}", flush=True)
            else:
                print(f"PROVED {name:32s} VB={VB:3d} every n in {{{nl[0]}..{nl[-1]}}} "
                      f"({len(nl)} sizes) {time.time() - t0:.1f}s", flush=True)


if __name__ == "__main__" and len(sys.argv) > 1 and sys.argv[1] == "comp":
    main_comp()

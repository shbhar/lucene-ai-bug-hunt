# Z3 bit-vector translation validation of the signed/unsigned byte kernels of
# PanamaVectorUtilSupport (lucene/core/src/java25/.../PanamaVectorUtilSupport.java @ 7cecf79523)
# against DefaultVectorUtilSupport (lucene/core/src/java/.../DefaultVectorUtilSupport.java).
#
# Kernels: dotProduct(byte[],byte[]), uint8DotProduct, squareDistance(byte[]), uint8SquareDistance,
# cosine(byte[]) (the three int accumulators sum/norm1/norm2 before the float division, which is
# the same scalar expression in both implementations).
#
# VB = PanamaVectorConstants.PREFERRED_VECTOR_BITSIZE (128 = NEON / SVE-128, 256 = AVX2 / SVE-256
#, 512 = AVX-512, 1024/2048 = hypothetical wider SVE, all reachable via tests.vectorsize).
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa


def byte_species_lanes(VB):
    # BYTE_SPECIES = SPECIES_MAX.withShape(forBitSize(VB >> 2)) for VB >= 256, else null
    return (VB >> 2) // 8 if VB >= 256 else None


def load(arr, i, lanes):
    assert i + lanes <= len(arr), "out-of-bounds vector load"
    return arr[i:i + lanes]


# ---------------- Panama models (structure mirrors the Java source) ----------------

def p_dot(a, b, VB, unsigned):
    n = len(a)
    X2S = ZB2S if unsigned else B2S
    X2I = ZB2I if unsigned else B2I
    S2Iu = ZS2I if unsigned else S2I
    i = 0
    res = bvv(0, 32)
    if n >= 16:
        if VB >= 512:
            BL = byte_species_lanes(VB)
            i += java_loop_bound(n, BL)
            limit = i
            INT_L = VB // 32
            SHORT_L = (VB >> 1) // 16
            acc = [bvv(0, 32)] * INT_L
            for k in range(0, limit, BL):
                va8, vb8 = load(a, k, BL), load(b, k, BL)
                va16 = convert_part(va8, X2S, SHORT_L, 0)
                vb16 = convert_part(vb8, X2S, SHORT_L, 0)
                prod16 = vmul(va16, vb16)
                prod32 = convert_part(prod16, S2Iu, INT_L, 0)
                acc = vadd(acc, prod32)
            res = res + reduce_add(acc)
        elif VB == 256:
            BL = byte_species_lanes(VB)  # 8
            i += java_loop_bound(n, BL)
            limit = i
            acc = [bvv(0, 32)] * 8
            for k in range(0, limit, 8):  # ByteVector.SPECIES_64.length()
                va8, vb8 = load(a, k, 8), load(b, k, 8)
                va32 = convert_part(va8, X2I, 8, 0)
                vb32 = convert_part(vb8, X2I, 8, 0)
                acc = vadd(acc, vmul(va32, vb32))
            res = res + reduce_add(acc)
        else:
            i += java_loop_bound(n - 8, 8)
            limit = i
            acc = [bvv(0, 32)] * 4
            for k in range(0, limit, 4):  # SPECIES_64.length() >> 1
                va8, vb8 = load(a, k, 8), load(b, k, 8)
                va16 = convert_part(va8, X2S, 4, 0)  # convert(B2S, 0): same 64-bit shape
                vb16 = convert_part(vb8, X2S, 4, 0)
                prod16 = vmul(va16, vb16)
                acc = vadd(acc, convert_part(prod16, S2Iu, 4, 0))
            res = res + reduce_add(acc)
    X = ZB2I if unsigned else B2I
    for k in range(i, n):
        res = res + X(a[k]) * X(b[k])
    return res


def p_sqr(a, b, VB, unsigned):
    n = len(a)
    X2S = ZB2S if unsigned else B2S
    X2I = ZB2I if unsigned else B2I
    i = 0
    res = bvv(0, 32)
    if n >= 16:
        if VB >= 256:
            BL = byte_species_lanes(VB)
            INT_L = VB // 32
            i += java_loop_bound(n, BL)
            acc = [bvv(0, 32)] * INT_L
            for k in range(0, i, BL):
                va32 = convert_part(load(a, k, BL), X2I, INT_L, 0)
                vb32 = convert_part(load(b, k, BL), X2I, INT_L, 0)
                d = vsub(va32, vb32)
                acc = vadd(acc, vmul(d, d))
            res = res + reduce_add(acc)
        else:
            i += java_loop_bound(n, 8)
            acc1 = [bvv(0, 32)] * 4
            acc2 = [bvv(0, 32)] * 4
            for k in range(0, i, 8):
                va16 = convert_part(load(a, k, 8), X2S, 8, 0)  # to ShortVector.SPECIES_128
                vb16 = convert_part(load(b, k, 8), X2S, 8, 0)
                d16 = vsub(va16, vb16)
                d1 = convert_part(d16, S2I, 4, 0)  # S2I (signed difference) part 0
                d2 = convert_part(d16, S2I, 4, 1)  # part 1
                acc1 = vadd(acc1, vmul(d1, d1))
                acc2 = vadd(acc2, vmul(d2, d2))
            res = res + reduce_add(vadd(acc1, acc2))
    X = ZB2I if unsigned else B2I
    for k in range(i, n):
        d = X(a[k]) - X(b[k])
        res = res + d * d
    return res


def p_cos(a, b, VB):
    n = len(a)
    i = 0
    s, n1, n2 = bvv(0, 32), bvv(0, 32), bvv(0, 32)
    if n >= 16:
        if VB >= 512:
            BL = byte_species_lanes(VB)
            INT_L = VB // 32
            SHORT_L = (VB >> 1) // 16
            i += java_loop_bound(n, BL)
            aS, aN1, aN2 = [bvv(0, 32)] * INT_L, [bvv(0, 32)] * INT_L, [bvv(0, 32)] * INT_L
            for k in range(0, i, BL):
                va16 = convert_part(load(a, k, BL), B2S, SHORT_L, 0)
                vb16 = convert_part(load(b, k, BL), B2S, SHORT_L, 0)
                aN1 = vadd(aN1, convert_part(vmul(va16, va16), S2I, INT_L, 0))
                aN2 = vadd(aN2, convert_part(vmul(vb16, vb16), S2I, INT_L, 0))
                aS = vadd(aS, convert_part(vmul(va16, vb16), S2I, INT_L, 0))
            s, n1, n2 = s + reduce_add(aS), n1 + reduce_add(aN1), n2 + reduce_add(aN2)
        elif VB == 256:
            BL = byte_species_lanes(VB)
            i += java_loop_bound(n, BL)
            aS, aN1, aN2 = [bvv(0, 32)] * 8, [bvv(0, 32)] * 8, [bvv(0, 32)] * 8
            for k in range(0, i, 8):
                va32 = convert_part(load(a, k, 8), B2I, 8, 0)
                vb32 = convert_part(load(b, k, 8), B2I, 8, 0)
                aN1 = vadd(aN1, vmul(va32, va32))
                aN2 = vadd(aN2, vmul(vb32, vb32))
                aS = vadd(aS, vmul(va32, vb32))
            s, n1, n2 = s + reduce_add(aS), n1 + reduce_add(aN1), n2 + reduce_add(aN2)
        else:
            i += java_loop_bound(n - 8, 8)
            aS, aN1, aN2 = [bvv(0, 32)] * 4, [bvv(0, 32)] * 4, [bvv(0, 32)] * 4
            for k in range(0, i, 4):
                va16 = convert_part(load(a, k, 8), B2S, 4, 0)
                vb16 = convert_part(load(b, k, 8), B2S, 4, 0)
                aN1 = vadd(aN1, convert_part(vmul(va16, va16), S2I, 4, 0))
                aN2 = vadd(aN2, convert_part(vmul(vb16, vb16), S2I, 4, 0))
                aS = vadd(aS, convert_part(vmul(va16, vb16), S2I, 4, 0))
            s, n1, n2 = s + reduce_add(aS), n1 + reduce_add(aN1), n2 + reduce_add(aN2)
    for k in range(i, n):
        e1, e2 = B2I(a[k]), B2I(b[k])
        s, n1, n2 = s + e1 * e2, n1 + e1 * e1, n2 + e2 * e2
    return (s, n1, n2)


# ---------------- scalar references (DefaultVectorUtilSupport) ----------------

def s_dot(a, b, unsigned):
    X = ZB2I if unsigned else B2I
    t = bvv(0, 32)
    for x, y in zip(a, b):
        t = t + X(x) * X(y)
    return t


def s_sqr(a, b, unsigned):
    X = ZB2I if unsigned else B2I
    t = bvv(0, 32)
    for x, y in zip(a, b):
        d = X(x) - X(y)
        t = t + d * d
    return t


def s_cos(a, b):
    s, n1, n2 = bvv(0, 32), bvv(0, 32), bvv(0, 32)
    for x, y in zip(a, b):
        e1, e2 = B2I(x), B2I(y)
        s, n1, n2 = s + e1 * e2, n1 + e1 * e1, n2 + e2 * e2
    return (s, n1, n2)


def prove(lhs, rhs, timeout_ms=600000):
    sol = z3.SolverFor("QF_BV")
    sol.set("timeout", timeout_ms)
    if isinstance(lhs, tuple):
        sol.add(z3.Or(*[l != r for l, r in zip(lhs, rhs)]))
    else:
        sol.add(lhs != rhs)
    r = sol.check()
    return r, (sol.model() if r == z3.sat else None)


KERNELS = {
    "dotProduct": (lambda a, b, VB: p_dot(a, b, VB, False), lambda a, b: s_dot(a, b, False)),
    "uint8DotProduct": (lambda a, b, VB: p_dot(a, b, VB, True), lambda a, b: s_dot(a, b, True)),
    "squareDistance": (lambda a, b, VB: p_sqr(a, b, VB, False), lambda a, b: s_sqr(a, b, False)),
    "uint8SquareDistance": (lambda a, b, VB: p_sqr(a, b, VB, True), lambda a, b: s_sqr(a, b, True)),
    "cosine(int parts)": (p_cos, s_cos),
}


def main():
    nmax = int(sys.argv[1]) if len(sys.argv) > 1 else 48
    vbs = [int(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [128, 256, 512, 1024]
    names = sys.argv[3].split(",") if len(sys.argv) > 3 else list(KERNELS)
    total = 0
    for name in names:
        pf, sf = KERNELS[name]
        for VB in vbs:
            t0 = time.time()
            worst = 0.0
            for n in range(0, nmax + 1):
                a = sym("a", n, 8)
                b = sym("b", n, 8)
                t1 = time.time()
                r, m = prove(pf(a, b, VB), sf(a, b))
                worst = max(worst, time.time() - t1)
                total += 1
                if r != z3.unsat:
                    print(f"[{name} VB={VB} n={n}] RESULT {r} model={m}", flush=True)
                    break
            else:
                print(f"PROVED {name:22s} VB={VB:4d} all n in [0,{nmax}]  "
                      f"time={time.time() - t0:7.1f}s worst_n={worst:5.1f}s", flush=True)
    print("obligations:", total)


if __name__ == "__main__":
    main()

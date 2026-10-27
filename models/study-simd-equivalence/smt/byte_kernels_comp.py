# Compositional Z3 proof of the byte kernels (signed/unsigned dot product, square distance,
# cosine int accumulators) for every length n in [0, NMAX] and VB in {128,256,512,1024,2048}.
#
# Step 1 (element lemmas, exhaustive over all 2^16 byte pairs, QF_BV): the per-lane term each
#   vector path computes for one index k (e.g. S2I(B2S(a)*B2S(b)) on the 128/512-bit paths)
#   equals the scalar per-element term (B2I(a)*B2I(b)).
# Step 2 (structure, per n): build the exact lane-precise model of the Panama kernel (byte_kernels.py,
#   same loop bounds, loads, conversions, part selection, accumulators, tail) and the scalar model;
#   replace, on both sides, every occurrence of the index-k lane term and the index-k scalar term by
#   one fresh 32-bit variable t_k (sound by Step 1: the replaced subterms are equal for all inputs);
#   then prove the two sides equal for ALL t (QF_BV).  A missed / duplicated index, an off-by-one
#   loop bound or a wrong part selection makes the sums of t differ and yields a counterexample.
#   Out-of-bounds vector loads are caught by an assertion in the model's load().
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa
from byte_kernels import p_dot, p_sqr, p_cos, s_dot, s_sqr, s_cos


def element_lemmas():
    a, b = z3.BitVecs("a b", 8)
    lemmas = {
        "dot signed 128/512: S2I(B2S a * B2S b) == B2I a * B2I b":
            S2I(B2S(a) * B2S(b)) == B2I(a) * B2I(b),
        "dot unsigned 128/512: ZS2I(ZB2S a * ZB2S b) == ZB2I a * ZB2I b":
            ZS2I(ZB2S(a) * ZB2S(b)) == ZB2I(a) * ZB2I(b),
        "sqr signed 128: (S2I(B2S a - B2S b))^2 == (B2I a - B2I b)^2":
            (lambda d, e: d * d == e * e)(S2I(B2S(a) - B2S(b)), B2I(a) - B2I(b)),
        "sqr unsigned 128: (S2I(ZB2S a - ZB2S b))^2 == (ZB2I a - ZB2I b)^2":
            (lambda d, e: d * d == e * e)(S2I(ZB2S(a) - ZB2S(b)), ZB2I(a) - ZB2I(b)),
        "cos norm 128/512: S2I(B2S a * B2S a) == B2I a * B2I a":
            S2I(B2S(a) * B2S(a)) == B2I(a) * B2I(a),
    }
    for name, f in lemmas.items():
        s = z3.Solver()
        s.add(z3.Not(f))
        r = s.check()
        print(f"LEMMA {'PROVED' if r == z3.unsat else 'FAILED ' + str(r)}: {name}")
        assert r == z3.unsat


def lane_terms(kernel, ak, bk):
    """All syntactic forms of the index-k term that occur in the vector or scalar model."""
    if kernel == "dot":
        return [S2I(B2S(ak) * B2S(bk)), B2I(ak) * B2I(bk)]
    if kernel == "udot":
        return [ZS2I(ZB2S(ak) * ZB2S(bk)), ZB2I(ak) * ZB2I(bk)]
    if kernel == "sqr":
        d1 = S2I(B2S(ak) - B2S(bk))
        d2 = B2I(ak) - B2I(bk)
        return [d1 * d1, d2 * d2]
    if kernel == "usqr":
        d1 = S2I(ZB2S(ak) - ZB2S(bk))
        d2 = ZB2I(ak) - ZB2I(bk)
        return [d1 * d1, d2 * d2]
    raise ValueError(kernel)


def check_eq(lhs, rhs, subs):
    lhs = z3.substitute(lhs, *subs) if subs else lhs
    rhs = z3.substitute(rhs, *subs) if subs else rhs
    s = z3.SolverFor("QF_BV")
    s.set("timeout", 300000)
    s.add(lhs != rhs)
    return s.check()


def run(kernel, VB, n):
    a, b = sym("a", n, 8), sym("b", n, 8)
    if kernel == "cos":
        lhs, rhs = p_cos(a, b, VB), s_cos(a, b)
        res = []
        for idx in range(3):
            subs = []
            for k in range(n):
                t = z3.BitVec(f"t{k}", 32)
                u = z3.BitVec(f"u{k}", 32)
                v = z3.BitVec(f"v{k}", 32)
                subs += [(S2I(B2S(a[k]) * B2S(b[k])), t), (B2I(a[k]) * B2I(b[k]), t),
                         (S2I(B2S(a[k]) * B2S(a[k])), u), (B2I(a[k]) * B2I(a[k]), u),
                         (S2I(B2S(b[k]) * B2S(b[k])), v), (B2I(b[k]) * B2I(b[k]), v)]
            res.append(check_eq(lhs[idx], rhs[idx], subs))
        return z3.unsat if all(r == z3.unsat for r in res) else [r for r in res]
    if kernel in ("dot", "udot"):
        lhs, rhs = p_dot(a, b, VB, kernel == "udot"), s_dot(a, b, kernel == "udot")
    else:
        lhs, rhs = p_sqr(a, b, VB, kernel == "usqr"), s_sqr(a, b, kernel == "usqr")
    subs = []
    for k in range(n):
        t = z3.BitVec(f"t{k}", 32)
        for form in lane_terms(kernel, a[k], b[k]):
            subs.append((form, t))
    return check_eq(lhs, rhs, subs)


def main():
    element_lemmas()
    nmax = int(sys.argv[1]) if len(sys.argv) > 1 else 200
    vbs = [int(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [128, 256, 512, 1024, 2048]
    kernels = sys.argv[3].split(",") if len(sys.argv) > 3 else ["dot", "udot", "sqr", "usqr", "cos"]
    nlist = list(range(0, nmax + 1))
    if len(sys.argv) > 4:
        nlist += [int(x) for x in sys.argv[4].split(",")]
    for kernel in kernels:
        for VB in vbs:
            t0 = time.time()
            ok = True
            for n in nlist:
                r = run(kernel, VB, n)
                if r != z3.unsat:
                    print(f"FAIL {kernel} VB={VB} n={n}: {r}", flush=True)
                    ok = False
                    break
            if ok:
                print(f"PROVED {kernel:5s} VB={VB:5d} n in [0,{nmax}]"
                      f"{' + ' + sys.argv[4] if len(sys.argv) > 4 else ''}  ({time.time() - t0:.1f}s)",
                      flush=True)


if __name__ == "__main__":
    main()

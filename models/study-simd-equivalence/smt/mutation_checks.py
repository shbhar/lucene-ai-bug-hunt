# Non-vacuity / sensitivity checks: seeded mutations of the models must be refuted.
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa
import byte_kernels as bk
import byte_kernels_comp as bkc
import int4_kernels as ik

def mut1():
    # 128-bit dotProduct with loop bound loopBound(n - 4) instead of loopBound(n - 8)
    orig = bk.java_loop_bound
    def p_dot_mut(a, b, VB, unsigned):
        bk.java_loop_bound = lambda n, l: orig(n - 4 + 8, l) if l == 8 and n == len(a) - 8 else orig(n, l)
        try:
            return bk.p_dot(a, b, VB, unsigned)
        finally:
            bk.java_loop_bound = orig
    for n in (16, 17, 20, 23, 24):
        a, b = sym("a", n, 8), sym("b", n, 8)
        try:
            lhs = p_dot_mut(a, b, 128, False)
        except AssertionError as e:
            print(f"MUTANT 1 (128-bit loop bound n-4) n={n}: rejected by the model: {e}"); continue
        subs = [(f, z3.BitVec(f"t{k}", 32)) for k in range(n) for f in bkc.lane_terms("dot", a[k], b[k])]
        r = bkc.check_eq(lhs, bk.s_dot(a, b, False), subs)
        print(f"MUTANT 1 (128-bit loop bound n-4, an equivalent mutant: still in-bounds and covering) n={n}: {'KILLED' if r == z3.sat else 'equivalent (' + str(r) + ')'}")

def mut1b():
    # 128-bit dotProduct body with the classic error: advance by a full 8-byte load per iteration while
    # only consuming its first 4 lanes (convert(B2S, 0)), i.e. half of the elements are skipped
    def p_dot_mut(a, b):
        n = len(a); i = 0; res = bvv(0, 32)
        if n >= 16:
            i = java_loop_bound(n - 8, 8)
            acc = [bvv(0, 32)] * 4
            for k in range(0, i, 8):
                va16 = convert_part(a[k:k + 8], B2S, 4, 0); vb16 = convert_part(b[k:k + 8], B2S, 4, 0)
                acc = vadd(acc, convert_part(vmul(va16, vb16), S2I, 4, 0))
            res = res + reduce_add(acc)
        for k in range(i, n):
            res = res + B2I(a[k]) * B2I(b[k])
        return res
    for n in (16, 24):
        a, b = sym("a", n, 8), sym("b", n, 8)
        subs = [(f, z3.BitVec(f"t{k}", 32)) for k in range(n) for f in bkc.lane_terms("dot", a[k], b[k])]
        r = bkc.check_eq(p_dot_mut(a, b), bk.s_dot(a, b, False), subs)
        print(f"MUTANT 1b (128-bit step 8 instead of 4) n={n}: {'KILLED (counterexample)' if r == z3.sat else r}")

def mut2():
    # int4 CHUNK doubled (2x iterations per short accumulator): the no-overflow lemma must fail
    for VB in (128, 256, 512):
        BL, SL, CHUNK = ik.int4_constants(VB)
        K = 2 * CHUNK // BL
        v, inc = z3.BitVecs("v inc", 16)
        s = z3.Solver()
        s.add(z3.ULE(v, bvv((K - 1) * 450 % 65536 if (K - 1) * 450 < 65536 else 65535, 16)), z3.ULE(inc, bvv(450, 16)))
        s.add(zext(v + inc, 32) != zext(v, 32) + zext(inc, 32))
        print(f"MUTANT 2 (CHUNK x2, VB={VB}, K={K}, packed M=450): {'KILLED (16-bit wrap witness)' if s.check() == z3.sat else 'survived'}")

def mut3():
    # int4DotProductBothPacked with the high-nibble product using AND 0xF0 instead of LSHR 4
    def bad(a, b, VB):
        save = ik.vlshr
        ik.vlshr = lambda v, k: [x & bvv(0xF0, 8) for x in v] if k == 4 else save(v, k)
        try:
            return ik.p_int4_dot_both(a, b, VB)
        finally:
            ik.vlshr = save
    a, b = sym("a", 32, 8), sym("b", 32, 8)
    s = z3.Solver(); s.add(bad(a, b, 128) != ik.s_dot_both(a, b))
    print("MUTANT 3 (int4DotProductBothPacked high nibble not shifted):", "KILLED" if s.check() == z3.sat else "survived")

mut1(); mut1b(); mut2(); mut3()

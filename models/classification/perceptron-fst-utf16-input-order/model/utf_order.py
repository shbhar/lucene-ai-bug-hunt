"""
Z3 (QF_BV) model: does the iteration order of BooleanPerceptronClassifier's weight map (String natural order =
UTF-16 code-unit order) agree with the input order FSTCompiler requires (unsigned UTF-8 byte order)?

Mapping (Lucene main @ ea57360912ed):
  weights = new ConcurrentSkipListMap<String, Double>()   BooleanPerceptronClassifier.java:117  (String.compareTo order)
  updateFST: for entry in weights (that order) ->
      scratchBytes.copyChars(key)  (UTF-16 -> UTF-8)      BooleanPerceptronClassifier.java:214-217
      fstCompiler.add(Util.toIntsRef(bytes))              (labels = unsigned bytes, FST.INPUT_TYPE.BYTE1)
  FSTCompiler.add requires input >= lastInput (IntsRef order = unsigned byte order); checked only by an
      assert (FSTCompiler.java:854-855), so with -da out-of-order input is accepted silently.

Strings are modelled as sequences of up to 2 Unicode scalar values (no lone surrogates), each encoded to UTF-16
code units (1 or 2) and UTF-8 bytes (1..4).  Lexicographic comparison with length tie-break is encoded exactly.

Queries:
  D1  exists s, t: s <_utf16 t  and  s >_utf8 t                                         (sat: witness)
  D2  any disagreement implies: at the first differing scalar position, one scalar is supplementary (>= U+10000)
      and the other is in U+E000..U+FFFF                                                (unsat of negation)
  D3  the condition of D2 implies disagreement                                          (unsat of negation)
  VAC sanity: equal strings compare equal in both orders; BMP-only (< U+D800) strings always agree.
"""
import sys
import z3

BV = lambda n: z3.BitVecSort(n)
results = []


def scalar(name):
    c = z3.BitVec(name, 21)
    valid = z3.And(z3.ULE(c, 0x10FFFF), z3.Or(z3.ULT(c, 0xD800), z3.UGT(c, 0xDFFF)))
    return c, valid


def utf16(c):
    """returns (len, [u0,u1]) as 16-bit units"""
    is_supp = z3.UGE(c, 0x10000)
    v = c - 0x10000
    hi = z3.Extract(15, 0, z3.ZeroExt(0, 0xD800 + z3.LShR(v, 10)))
    lo = z3.Extract(15, 0, 0xDC00 + (v & 0x3FF))
    u0 = z3.If(is_supp, hi, z3.Extract(15, 0, c))
    u1 = z3.If(is_supp, lo, z3.BitVecVal(0, 16))
    ln = z3.If(is_supp, z3.IntVal(2), z3.IntVal(1))
    return ln, [u0, u1]


def utf8(c):
    """returns (len, [b0..b3]) as 8-bit unsigned bytes"""
    b = lambda e: z3.Extract(7, 0, e)
    c21 = c
    l1 = z3.ULT(c21, 0x80)
    l2 = z3.And(z3.UGE(c21, 0x80), z3.ULT(c21, 0x800))
    l3 = z3.And(z3.UGE(c21, 0x800), z3.ULT(c21, 0x10000))
    ln = z3.If(l1, z3.IntVal(1), z3.If(l2, z3.IntVal(2), z3.If(l3, z3.IntVal(3), z3.IntVal(4))))
    zero = z3.BitVecVal(0, 8)
    b0 = z3.If(l1, b(c21), z3.If(l2, b(0xC0 | z3.LShR(c21, 6)), z3.If(l3, b(0xE0 | z3.LShR(c21, 12)),
                                                                    b(0xF0 | z3.LShR(c21, 18)))))
    b1 = z3.If(l1, zero, z3.If(l2, b(0x80 | (c21 & 0x3F)), z3.If(l3, b(0x80 | (z3.LShR(c21, 6) & 0x3F)),
                                                                 b(0x80 | (z3.LShR(c21, 12) & 0x3F)))))
    b2 = z3.If(z3.Or(l1, l2), zero, z3.If(l3, b(0x80 | (c21 & 0x3F)), b(0x80 | (z3.LShR(c21, 6) & 0x3F))))
    b3 = z3.If(z3.Or(l1, l2, l3), zero, b(0x80 | (c21 & 0x3F)))
    return ln, [b0, b1, b2, b3]


def concat(parts, width_units):
    """parts: list of (present, (len, units)); returns (total_len, flat list of (index-> unit) via If chains)"""
    # Build a fixed-size array of length sum(max lens) with symbolic positions.
    maxlen = sum(len(u) for _, (_, u) in parts)
    total = z3.IntVal(0)
    arr = [z3.BitVecVal(0, width_units) for _ in range(maxlen)]
    for present, (ln, units) in parts:
        for k, u in enumerate(units):
            for pos in range(maxlen):
                arr[pos] = z3.If(z3.And(present, k < ln, total + k == pos), u, arr[pos])
        total = total + z3.If(present, ln, z3.IntVal(0))
    return total, arr


def lex_lt(la, a, lb, b):
    """lexicographic a < b (unsigned units), shorter prefix is smaller"""
    n = max(len(a), len(b))
    res = z3.BoolVal(False)
    # iterate from the end so that the first differing position decides
    for i in reversed(range(n)):
        ai = a[i] if i < len(a) else None
        bi = b[i] if i < len(b) else None
        both = z3.And(i < la, i < lb)
        lt_here = z3.ULT(ai, bi)
        eq_here = ai == bi
        # at position i: if both have it: lt if ai<bi; equal -> continue (res); else false
        # if a ended at i (la == i) and b continues -> true; if b ended -> false
        res = z3.If(both, z3.If(lt_here, z3.BoolVal(True), z3.If(eq_here, res, z3.BoolVal(False))),
                    z3.And(la == i, lb > i))
    return res


def string(tag, nmax=2):
    cs, valids, pres = [], [], []
    ln = z3.Int(f"{tag}_n")
    cons = [ln >= 0, ln <= nmax]
    for i in range(nmax):
        c, v = scalar(f"{tag}_c{i}")
        cs.append(c)
        cons.append(z3.Implies(i < ln, v))
        cons.append(z3.Implies(i >= ln, c == 0))
        pres.append(i < ln)
    l16, a16 = concat([(pres[i], utf16(cs[i])) for i in range(nmax)], 16)
    l8, a8 = concat([(pres[i], utf8(cs[i])) for i in range(nmax)], 8)
    return cs, ln, cons, (l16, a16), (l8, a8)


def check(name, cons, expect):
    s = z3.Solver()
    s.set("timeout", 600000)
    s.add(*cons)
    r = str(s.check())
    ok = r == expect
    results.append(ok)
    msg = f"{name}: {r} (expect {expect}) {'OK' if ok else 'MISMATCH'}"
    if r == "sat":
        m = s.model()
        vals = {d.name(): m[d] for d in m.decls() if d.arity() == 0}
        msg += "  " + " ".join(f"{k}={('U+%04X' % vals[k].as_long()) if '_c' in k else vals[k]}"
                               for k in sorted(vals) if k.startswith(("s_", "t_")))
    print(msg, flush=True)


def main():
    sc, sn, scons, s16, s8 = string("s")
    tc, tn, tcons, t16, t8 = string("t")
    base = scons + tcons
    lt16 = lex_lt(s16[0], s16[1], t16[0], t16[1])
    lt8_rev = lex_lt(t8[0], t8[1], s8[0], s8[1])
    lt8 = lex_lt(s8[0], s8[1], t8[0], t8[1])
    lt16_rev = lex_lt(t16[0], t16[1], s16[0], s16[1])
    disagree = z3.Or(z3.And(lt16, lt8_rev), z3.And(lt8, lt16_rev))

    check("D1 exists utf16-order != utf8-order", base + [lt16, lt8_rev], "sat")

    # first differing scalar position k: all earlier equal, and (k < both lengths, differ) [prefix cases agree]
    def cond_at(k):
        earlier_eq = z3.And(*[sc[i] == tc[i] for i in range(k)]) if k else z3.BoolVal(True)
        a, b = sc[k], tc[k]
        supp_vs_high = z3.Or(z3.And(z3.UGE(a, 0x10000), z3.UGE(b, 0xE000), z3.ULE(b, 0xFFFF)),
                             z3.And(z3.UGE(b, 0x10000), z3.UGE(a, 0xE000), z3.ULE(a, 0xFFFF)))
        return z3.And(earlier_eq, k < sn, k < tn, a != b, supp_vs_high)

    cond = z3.Or(cond_at(0), cond_at(1))
    check("D2 disagreement => supplementary vs U+E000..U+FFFF at first difference", base + [disagree, z3.Not(cond)],
          "unsat")
    check("D3 that condition => disagreement", base + [cond, z3.Not(disagree)], "unsat")
    # vacuity / sanity
    same = z3.And(sn == tn, *[sc[i] == tc[i] for i in range(2)])
    check("VAC1 equal strings: neither order says <", base + [same, z3.Or(lt16, lt8)], "unsat")
    bmp_low = z3.And(*[z3.ULT(c, 0xD800) for c in sc + tc])
    check("VAC2 scalars < U+D800 only: orders agree", base + [bmp_low, disagree], "unsat")
    check("VAC3 hypotheses satisfiable", base + [sn == 2, tn == 2], "sat")
    ok = all(results)
    print(f"{sum(results)}/{len(results)} as expected -> {'ALL_EXPECTED' if ok else 'SOME_UNEXPECTED'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

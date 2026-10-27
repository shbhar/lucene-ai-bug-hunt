# Classification of the gcd == 1 failures of the encoded-domain range scan (QF_BV, 64-bit).
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from dv_gcd_transform import *
lo, hi, v, m, g, s = z3.BitVecs("lo hi v m g s", W)
base = writer_reachable(v, m, g, s, "gcd1") + [lo <= hi]
def q(name, *extra):
    sol = z3.Solver(); sol.set("timeout", 600000)
    sol.add(*base, *extra)
    r = sol.check()
    out = {"query": name, "result": str(r)}
    if r == z3.sat:
        md = sol.model(); val = lambda x: md.eval(x, model_completion=True).as_signed_long()
        out["cex"] = {k: val(x) for k, x in dict(lo=lo, hi=hi, v=v, m=m, s=s).items()}
    print(json.dumps(out), flush=True)
ref, enc = reference_match(s, lo, hi, g, m), encoded_match(s, lo, hi, g, m)
q("false negative exists", ref, z3.Not(enc))
q("false positive exists", z3.Not(ref), enc)
q("false negative with packed s >= 0 (no wrap)", ref, z3.Not(enc), s >= 0)
q("false positive with packed s >= 0 and s != Long.MAX_VALUE", z3.Not(ref), enc, s >= 0, s != c(MAX))
q("any mismatch when s >= 0 and s != Long.MAX_VALUE (expect unsat = proof)", ref != enc, s >= 0, s != c(MAX))
q("any mismatch when the field span max-min fits in 63 bits, i.e. bitsPerValue <= 63 (s <= span)", ref != enc, z3.Not(sub_overflows(v, m)), s != c(MAX))
q("false negative with a NON-saturated upper bound (hi - min does not overflow)", ref, z3.Not(enc), z3.Not(sub_overflows(hi, m)))

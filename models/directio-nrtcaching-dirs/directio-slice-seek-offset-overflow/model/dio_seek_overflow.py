"""Z3 query: with the seek target unbounded (any long >= 0, the model's other queries cap it at 2^61), can seek(x)
on a slice in an invariant state end in anything but EOFException or the known in-window quirk?
Java: DirectIODirectory.java:413 absolutePos = pos + offset; 429-434 seekInternal; 496-510 fill/channel.read."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import BitVec, Solver, sat, Or, And  # noqa: E402
import dio_model as M  # noqa: E402
from dio_model import V  # noqa: E402
import dio_induct as I  # noqa: E402

for B in (4096, 512):
    for mode in ("PEND", "LOADED"):
        S = BitVec("S", 64)
        s0 = I.fresh_state("ov", False)
        x = BitVec("x", 64)
        ctx = M.Ctx(B, S)
        s1 = M.seek(ctx, s0, x)
        s = Solver()
        s.set("timeout", 120000)
        s.add(I.inv(s0, B, S, mode), x >= 0, x > s0.len)
        s.add(s1.status != V(M.EOF), s1.status != V(M.QUIRK))
        r = s.check()
        out = f"B={B} slice {mode}: {r}"
        if r == sat:
            m = s.model()
            g = lambda e: m.eval(e, model_completion=True).as_signed_long()
            out += (f"  witness: off={g(s0.off)} len={g(s0.len)} S={g(S)} x={g(x)} -> status "
                    f"{M.STATUS_NAMES[g(s1.status)]}")
        print(out, flush=True)
        # the same query restricted to targets where pos + offset does not overflow: must be UNSAT
        s2 = Solver()
        s2.set("timeout", 300000)
        s2.add(I.inv(s0, B, S, mode), x >= 0, x > s0.len, x <= V((1 << 63) - 1) - s0.off)
        s2.add(s1.status != V(M.EOF), s1.status != V(M.QUIRK))
        print(f"B={B} slice {mode} without overflow (x <= Long.MAX_VALUE - off): {s2.check()}", flush=True)

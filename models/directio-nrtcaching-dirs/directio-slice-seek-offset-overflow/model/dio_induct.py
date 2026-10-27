"""
Unbounded proof (1-induction) for the DirectIOIndexInput model in dio_model.py, for every trace of any length.

Inductive invariant Inv(s) over a state with status OK (B = block size, C = capacity, S = channel size, al(x) =
x - x % B):
  common   : 0 <= off, 0 <= len, off + len <= S <= 2^60, C % B == 0, B <= C <= 2^19 * B
  PEND     : pd >= 0, pd == off - al(off), fp == al(off) - C, p == 0, lim == 0, g == 0
  TOPINIT  : pd == -1, top, off == 0, len == S, fp == -C, p == 0, lim == 0, g == 0
  LOADED   : pd == -1, fp >= 0, fp % B == 0, loaded == fp, lim == min(C, S - fp), 0 <= p <= lim,
             g == fp + p - off, 0 <= g <= len
  top      : off == 0 and len == S (a top-level input or a clone of one)

Obligations (each a Z3 query that must be UNSAT; mode/top are split explicitly):
  I0  capacity(k*B, B, r) == k*B for every native address residue r (so slices/clones get C exactly and the
      top-level filePos = -bufferSize equals -C)            [Java 337, 352, 372-376]
  I1  Inv holds for a freshly opened input (bufferSize = k*B)                                   [333-344]
  I2  slice(o, l) of an Inv state with legal bounds gives an Inv (PEND) child                     [603-612]
  I3  clone() of an Inv state: no exception, child satisfies Inv with g = parent g                 [593-601]
  I4  seek(x), 0 <= x <= len: completes, Inv afterwards with g = x                                [401-436]
  I5  seek(x), x > len: top-level -> EOFException; slice -> EOFException or the known window quirk [401-436]
  I6  readByte(): g < len -> returns file[off + g], Inv afterwards; g >= len -> top-level throws EOF, a slice throws
      EOF or serves the file's own next byte (known quirk)                                        [444-450, 479-516]
  I7  one iteration of the readBytes loop (state, toRead, request end E = g + toRead): the chunk copied starts at
      file[off + g]; no exception while E <= len; afterwards Inv (or the loop is done); progress when the window was
      empty; top-level: completing with E > len is impossible                                     [518-533]
  I8  getFilePointer() == g and its assertion holds in every Inv state                              [386-399]
Vacuity: every obligation is also run against mutants that must make it SAT (see MUTANTS) and Inv is checked
satisfiable in each mode.
"""
import argparse
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import BitVec, Bool, And, Or, Not, Implies, Solver, sat, unsat, SRem, URem, If, BoolVal  # noqa: E402
import dio_model as M  # noqa: E402
from dio_model import V  # noqa: E402

MUTANT = os.environ.get("DIO_MUTANT", "")


def al(x, B):
    return x - M.jrem(x, B)


def fresh_state(tag, top):
    f = {n: BitVec(f"{n}_{tag}", 64) for n in ["off", "len", "fp", "p", "lim", "pd", "loaded", "g", "C"]}
    return M.St(top=top, status=V(M.OK), **f)


def inv(s, B, S, mode):
    common = [s.off >= 0, s.len >= 0, s.off <= S, s.len <= S, s.off + s.len <= S, S <= V(1 << 60), S >= 0,
              M.jrem(s.C, B) == 0, s.C >= V(B), s.C <= V(B << 19), s.status == V(M.OK)]
    if s.top:
        common += [s.off == 0, s.len == S]
    if mode == "PEND":
        m = [s.pd >= 0, s.pd == s.off - al(s.off, B), s.fp == al(s.off, B) - s.C, s.p == 0, s.lim == 0, s.g == 0]
    elif mode == "TOPINIT":
        m = [s.pd == -1, s.off == 0, s.len == S, s.fp == -s.C, s.p == 0, s.lim == 0, s.g == 0]
    elif mode == "LOADED":
        m = [s.pd == -1, s.fp >= 0, M.jrem(s.fp, B) == 0, s.loaded == s.fp,
             s.lim == If(S - s.fp < s.C, S - s.fp, s.C), s.p >= 0, s.p <= s.lim,
             s.g == s.fp + s.p - s.off, s.g >= 0, s.g <= s.len]
    else:
        raise ValueError(mode)
    return And(*common, *m)


def inv_any(s, B, S):
    modes = ["PEND", "LOADED"] + (["TOPINIT"] if s.top else [])
    return Or(*[inv(s, B, S, m) for m in modes])


ONLY = os.environ.get("DIO_ONLY", "")


def check(name, pre, goal_violation, timeout_ms):
    """UNSAT expected: pre AND violation."""
    if ONLY and not any(name.startswith(o) for o in ONLY.split(",")):
        return {"obligation": name, "result": "skipped"}
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(*pre)
    s.add(goal_violation)
    t = time.time()
    r = s.check()
    out = {"obligation": name, "result": str(r), "s": round(time.time() - t, 2)}
    print(f"  {name}: {r} ({out['s']} s)", flush=True)
    if r == sat:
        m = s.model()
        out["model"] = {str(d): m[d].as_signed_long() if hasattr(m[d], "as_signed_long") else str(m[d])
                        for d in m.decls()}
    return out


def ctx_viol(ctx, names):
    conds = [c for (n, c) in ctx.viol if n in names]
    return Or(*conds) if conds else BoolVal(False)


ALL_BAD = ["wrong_bytes", "spurious_exception", "hang", "missing_eof_top", "quirk_wrong_bytes", "fp_mismatch",
           "fp_assert", "seek_assert", "clone_exception"]


def obligations(B, timeout_ms):
    res = []
    S = BitVec("S", 64)
    # I0
    k = BitVec("k0", 64)
    r = BitVec("r0", 64)
    res.append(check("I0 capacity(k*B)==k*B", [k >= 1, k <= V(1 << 19), r >= 0, r < V(B)],
                     M.capacity(k * V(B), B, r) != k * V(B), timeout_ms))
    # I1
    ctx = M.Ctx(B, S)
    st = M.ctor_top(ctx, k * V(B), k * V(B))
    res.append(check("I1 init satisfies Inv", [k >= 1, k <= V(1 << 19), S >= 0, S <= V(1 << 60)],
                     Not(inv(st, B, S, "TOPINIT")), timeout_ms))
    for top in [True, False]:
        modes = ["PEND", "LOADED"] + (["TOPINIT"] if top else [])
        for mode in modes:
            tag = f"{'top' if top else 'sl'}_{mode}"
            # vacuity: Inv satisfiable
            s0 = fresh_state("v" + tag, top)
            sv = Solver()
            sv.add(inv(s0, B, S, mode))
            res.append({"obligation": f"VAC Inv[{tag}] satisfiable", "result": str(sv.check())})
            # I8
            s0 = fresh_state("i8" + tag, top)
            res.append(check(f"I8 getFilePointer[{tag}]", [inv(s0, B, S, mode)],
                             Or(M.get_fp(s0) != s0.g, Not(M.fp_assert_ok(s0))), timeout_ms))
            # I2 slice
            s0 = fresh_state("i2" + tag, top)
            o, l = BitVec("o_" + tag, 64), BitVec("l_" + tag, 64)
            ctx = M.Ctx(B, S)
            ch = M.slice_(ctx, s0, o, l)
            res.append(check(f"I2 slice[{tag}]", [inv(s0, B, S, mode), o >= 0, l >= 0, o <= s0.len, l <= s0.len, o + l <= s0.len],
                             Not(inv(ch, B, S, "PEND")), timeout_ms))
            # I3 clone
            s0 = fresh_state("i3" + tag, top)
            ctx = M.Ctx(B, S)
            c = M.clone(ctx, s0)
            res.append(check(f"I3 clone[{tag}]", [inv(s0, B, S, mode)],
                             Or(ctx_viol(ctx, ALL_BAD), Not(c.ok()), c.g != s0.g, Not(inv(c, B, S, "LOADED"))),
                             timeout_ms))
            # I4 seek in bounds
            s0 = fresh_state("i4" + tag, top)
            x = BitVec("x_" + tag, 64)
            ctx = M.Ctx(B, S)
            s1 = M.seek(ctx, s0, x)
            res.append(check(f"I4 seek x<=len[{tag}]", [inv(s0, B, S, mode), x >= 0, x <= s0.len],
                             Or(ctx_viol(ctx, ALL_BAD), Not(s1.ok()), s1.g != x, Not(inv_any(s1, B, S))),
                             timeout_ms))
            # I5 seek past the end
            s0 = fresh_state("i5" + tag, top)
            x = BitVec("x5_" + tag, 64)
            ctx = M.Ctx(B, S)
            s1 = M.seek(ctx, s0, x)
            allowed = (s1.status == V(M.EOF)) if top else Or(s1.status == V(M.EOF), s1.status == V(M.QUIRK))
            res.append(check(f"I5 seek x>len[{tag}]", [inv(s0, B, S, mode), x > s0.len, x <= V(1 << 61)],
                             Or(Not(allowed), ctx_viol(ctx, ["wrong_bytes", "hang", "fp_assert", "quirk_wrong_bytes"])),
                             timeout_ms))
            # I6 readByte
            s0 = fresh_state("i6" + tag, top)
            ctx = M.Ctx(B, S)
            s1 = M.read_byte(ctx, s0)
            inb = s0.g < s0.len
            outb_allowed = (s1.status == V(M.EOF)) if top else Or(s1.status == V(M.EOF), s1.status == V(M.QUIRK))
            res.append(check(f"I6 readByte[{tag}]", [inv(s0, B, S, mode)],
                             Or(ctx_viol(ctx, ALL_BAD),
                                And(inb, Or(Not(s1.ok()), Not(inv_any(s1, B, S)))),
                                And(Not(inb), Not(outb_allowed))),
                             timeout_ms))
            # I7 one readBytes iteration (loop body Java 521-531), with request end E = g + toRead
            s0 = fresh_state("i7" + tag, top)
            toRead = BitVec("t_" + tag, 64)
            ctx = M.Ctx(B, S)
            E = s0.g + toRead
            left = s0.lim - s0.p
            partial = left < toRead
            start_ok = s0.loaded + s0.p == s0.off + s0.g
            pc = s0.copy(p=s0.lim, g=s0.g + left)
            pr = M.refill(ctx, pc, toRead - left)
            prog_ok = Or(Not(pr.ok()), pr.lim - pr.p > 0)
            inbE = E <= s0.len
            # partial branch: chunk correct if left > 0; refill exception only allowed when E > len; then Inv
            bad_partial = And(partial, Or(And(left > 0, Not(start_ok)),
                                          And(Not(pr.ok()), inbE),
                                          And(pr.ok(), Not(inv_any(pr, B, S))),
                                          And(pr.ok(), left == 0, Not(prog_ok)),
                                          Not(Or(pr.ok(), pr.status == V(M.EOF)))))
            # final branch: chunk correct if toRead > 0; top-level must not complete beyond len
            bad_final = And(Not(partial), Or(And(toRead > 0, Not(start_ok)),
                                             And(BoolVal(top), Not(inbE))))
            pre7 = [inv(s0, B, S, mode), toRead >= 0, toRead <= V((1 << 31) - 1)]
            if os.environ.get("DIO_SPLIT_I7"):
                # the same obligation split into its disjuncts (each must be UNSAT); used when the joint query is slow
                parts = {
                    "a-chunk": And(partial, left > 0, Not(start_ok)),
                    "b-spurious": And(partial, Not(pr.ok()), inbE),
                    # the same disjunct with the two cases of the hand proof made explicit
                    "b1-spurious-fullwindow": And(partial, Not(pr.ok()), inbE, s0.lim == s0.C),
                    "b2-spurious-eofwindow": And(partial, Not(pr.ok()), inbE, s0.lim < s0.C),
                    "c-inv": And(partial, pr.ok(), Not(inv_any(pr, B, S))),
                    "d-progress": And(partial, pr.ok(), left == 0, Not(prog_ok)),
                    "e-exc-type": And(partial, Not(Or(pr.ok(), pr.status == V(M.EOF)))),
                    "f-final": bad_final,
                }
                for pn, pv in parts.items():
                    res.append(check(f"I7{pn} readBytes-iteration[{tag}]", pre7, pv, timeout_ms))
            else:
                res.append(check(f"I7 readBytes-iteration[{tag}]", pre7, Or(bad_partial, bad_final), timeout_ms))
    return res


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--block", type=int, default=4096)
    ap.add_argument("--timeout-ms", type=int, default=600000)
    ap.add_argument("--json", default=None)
    a = ap.parse_args()
    t = time.time()
    res = obligations(a.block, a.timeout_ms)
    summary = {"block": a.block, "mutant": MUTANT or None, "wall_s": round(time.time() - t, 1),
               "unsat": sum(1 for r in res if r["result"] == "unsat"),
               "sat": [r["obligation"] for r in res if r["result"] == "sat" and not r["obligation"].startswith("VAC")],
               "unknown": [r["obligation"] for r in res if r["result"] == "unknown"],
               "vacuity_sat": sum(1 for r in res if r["obligation"].startswith("VAC") and r["result"] == "sat")}
    print(json.dumps(summary, indent=1))
    if a.json:
        with open(a.json, "w") as f:
            json.dump({"summary": summary, "obligations": res}, f, indent=1)

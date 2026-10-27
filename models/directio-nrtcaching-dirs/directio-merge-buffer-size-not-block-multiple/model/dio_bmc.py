"""
Bounded model checking driver for dio_model.py: every sequence of up to K operations (readByte, readBytes(n),
seek(x), clone(), slice(o, l)) applied to a freshly opened DirectIOIndexInput, with every parameter symbolic
(64-bit), and every violation class queried separately.

Usage: dio_bmc.py --block 4096 --K 3 --config valid|anysize [--R 3] [--json out.json]
  valid   : bufferSize = k * blockSize, 1 <= k <= 8 (the capacity then equals bufferSize; checked by query Q0)
  anysize : 1 <= bufferSize <= 2^31 - blockSize, native address mod blockSize = r symbolic (0 <= r < blockSize,
            r % 8 == 0 as for malloc'ed memory)
Channel size S symbolic in [0, 2^40]; seek targets in [0, 2^41]; readBytes lengths in [0, 2^31 - 1] (traces that would
need more than R refills inside one readBytes are excluded, status LOOPBOUND).
"""
import argparse
import itertools
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import BitVec, And, Or, Not, Solver, sat, unsat, URem  # noqa: E402
import dio_model as M  # noqa: E402

OPS = ["RB", "RBS", "SK", "CL", "SL"]
VIOLS = ["wrong_bytes", "spurious_exception", "wrong_exception", "hang", "missing_eof_top", "quirk_wrong_bytes", "fp_mismatch",
         "fp_assert", "seek_assert", "clone_exception"]


def build(seq, B, config, R):
    S = BitVec("S", 64)
    ctx = M.Ctx(B, S)
    pre = [S >= 0, S <= M.V(1 << 40)]
    BS = BitVec("BS", 64)
    r = BitVec("r", 64)
    if config == "valid":
        k = BitVec("k", 64)
        pre += [k >= 1, k <= 8, BS == k * M.V(B), r >= 0, r < B, URem(r, M.V(8)) == 0]
    else:
        pre += [BS >= 1, BS <= M.V((1 << 31) - B), r >= 0, r < B, URem(r, M.V(8)) == 0]
    C = M.capacity(BS, B, r)
    st = M.ctor_top(ctx, BS, C)
    params = {"S": S, "BS": BS, "r": r}
    for i, op in enumerate(seq):
        if op == "RB":
            st = M.read_byte(ctx, st)
        elif op == "RBS":
            n = BitVec(f"n{i}", 64)
            pre += [n >= 0, n <= M.V((1 << 31) - 1)]
            params[f"n{i}"] = n
            st = M.read_bytes(ctx, st, n, R)
        elif op == "SK":
            x = BitVec(f"x{i}", 64)
            pre += [x >= 0, x <= M.V(1 << 41)]
            params[f"x{i}"] = x
            st = M.seek(ctx, st, x)
        elif op == "CL":
            st = M.clone(ctx, st)
        elif op == "SL":
            o = BitVec(f"o{i}", 64)
            l = BitVec(f"l{i}", 64)
            pre += [o >= 0, l >= 0, o <= st.len, l <= st.len, o + l <= st.len, o <= M.V(1 << 40), l <= M.V(1 << 40)]
            params[f"o{i}"] = o
            params[f"l{i}"] = l
            st = M.slice_(ctx, st, o, l)
    return ctx, pre, params, st, C


def run(B, K, config, R, timeout_ms, only=None):
    results = []
    t0 = time.time()
    seqs = []
    for k in range(1, K + 1):
        seqs += list(itertools.product(OPS, repeat=k))
    # a sequence that never reads, seeks or clones cannot violate anything new beyond its prefix; keep all for
    # simplicity but skip trailing SL (a fresh slice is checked by the next op of a longer sequence)
    seqs = [s for s in seqs if s[-1] != "SL"]
    found = {}
    for seq in seqs:
        ctx, pre, params, st, C = build(seq, B, config, R)
        per = {}
        groups = {}
        for name, cond in ctx.viol:
            groups.setdefault(name, []).append(cond)
        for name in VIOLS:
            if only and name not in only:
                continue
            conds = groups.get(name, [])
            if not conds:
                per[name] = "n/a"
                continue
            s = Solver()
            s.set("timeout", timeout_ms)
            s.add(*pre)
            s.add(Or(*conds))
            res = s.check()
            if res == sat:
                m = s.model()
                wit = {k: m.eval(v, model_completion=True).as_signed_long() for k, v in params.items()}
                wit["C"] = m.eval(C, model_completion=True).as_signed_long()
                per[name] = {"sat": wit}
                found.setdefault(name, []).append({"seq": list(seq), "witness": wit})
            else:
                per[name] = str(res)
        results.append({"seq": list(seq), "verdicts": per})
    return {"block": B, "K": K, "config": config, "R": R, "n_sequences": len(seqs), "wall_s": round(time.time() - t0, 1),
            "found": {k: v[:5] for k, v in found.items()}, "found_counts": {k: len(v) for k, v in found.items()},
            "unknown": sum(1 for r in results for v in r["verdicts"].values() if v == "unknown"),
            "results": results}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--block", type=int, default=4096)
    ap.add_argument("--K", type=int, default=2)
    ap.add_argument("--config", default="valid")
    ap.add_argument("--R", type=int, default=3)
    ap.add_argument("--timeout-ms", type=int, default=120000)
    ap.add_argument("--only", default=None)
    ap.add_argument("--json", default=None)
    a = ap.parse_args()
    out = run(a.block, a.K, a.config, a.R, a.timeout_ms, a.only.split(",") if a.only else None)
    summary = {k: out[k] for k in ["block", "K", "config", "R", "n_sequences", "wall_s", "found_counts", "unknown"]}
    print(json.dumps(summary, indent=1))
    for k, v in out["found"].items():
        print(k, json.dumps(v[0]))
    if a.json:
        with open(a.json, "w") as f:
            json.dump(out, f, indent=1)

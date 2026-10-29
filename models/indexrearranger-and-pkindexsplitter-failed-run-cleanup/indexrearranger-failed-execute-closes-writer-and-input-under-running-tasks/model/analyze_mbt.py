"""Summarise the ROW lines of TestH409FailurePointMBT (one run per line, tab-separated key=value fields).

usage: python3 -I analyze_mbt.py RUN_LOG OUT_JSON
"""
import collections
import json
import re
import sys


def parse(path):
    rows = []
    with open(path, errors="replace") as f:
        for line in f:
            if not line.startswith("ROW\t"):
                continue
            parts = line.rstrip("\n").split("\t")
            r = {"tool": parts[1]}
            for p in parts[2:]:
                k, _, v = p.partition("=")
                r[k] = v
            rows.append(r)
    return rows


def op_of(fired):
    if fired in (None, "null", "-"):
        return "-"
    # out:MockDirectoryWrapper.createOutput#3 -> out:createOutput ; in:openInput _0.cfs#2 -> in:openInput
    m = re.match(r"([a-z0-9]+):(?:Mock\w+\.)?(\w+)", fired)
    return f"{m.group(1)}:{m.group(2)}" if m else fired


def main():
    rows = parse(sys.argv[1])
    out = {"runs": len(rows), "by_tool": {}}
    for tool in sorted({r["tool"] for r in rows}):
        rs = [r for r in rows if r["tool"] == tool]
        s = {
            "runs": len(rs),
            "verdicts": collections.Counter(r["verdict"].split("->")[0] for r in rs),
            "failed_states": collections.Counter(r["verdict"] for r in rs if r["verdict"].startswith("FAILED")),
            "silent_mismatch_rows": [r for r in rs if r["verdict"] == "SILENT_MISMATCH"][:20],
            "replaced_rows": [r for r in rs if r["verdict"] == "REPLACED_OR_SPURIOUS"][:20],
            "fault_ops": collections.Counter(op_of(r.get("fired")) for r in rs),
            "close_problems": collections.Counter(
                v for r in rs for k, v in r.items() if k.startswith("close") and v not in ("null", "")
            ),
        }
        if tool == "rearranger":
            failed = [r for r in rs if r["verdict"].startswith("FAILED")]
            s["failed_runs"] = len(failed)
            s["failed_runs_with_leaked_pool_threads"] = sum(1 for r in failed if int(r["leakedThreads"]) > 0)
            s["ok_runs_with_leaked_pool_threads"] = sum(
                1 for r in rs if r["verdict"] == "OK" and int(r["leakedThreads"]) > 0
            )
            s["failed_runs_task_active_at_return"] = sum(1 for r in failed if int(r["activeAtReturn"]) > 0)
            s["failed_runs_selector_called_after_return"] = sum(
                1 for r in failed if int(r["selectorCallsAfterReturn"]) > 0
            )
            if "uncaught" in rs[0]:
                s["runs_with_uncaught_thread_exception"] = sum(1 for r in rs if int(r["uncaught"]) > 0)
                s["runs_with_uncaught_after_return"] = sum(1 for r in rs if int(r["uncaughtAfterReturn"]) > 0)
            per_cfg = collections.defaultdict(collections.Counter)
            for r in rs:
                per_cfg[f"nSel={r['nSel']} del={r['del']} target={r['target']}"][r["verdict"]] += 1
            s["per_config"] = {k: dict(v) for k, v in sorted(per_cfg.items())}
        else:
            if "uncaught" in rs[0]:
                s["runs_with_uncaught_thread_exception"] = sum(1 for r in rs if int(r["uncaught"]) > 0)
            per_cfg = collections.defaultdict(collections.Counter)
            for r in rs:
                per_cfg[f"deleteB={r['deleteB']} target={r['target']}"][r["verdict"]] += 1
            s["per_config"] = {k: dict(v) for k, v in sorted(per_cfg.items())}
        out["by_tool"][tool] = {k: (dict(v) if isinstance(v, collections.Counter) else v) for k, v in s.items()}
    summaries = []
    with open(sys.argv[1], errors="replace") as f:
        for line in f:
            if line.startswith("SUMMARY"):
                summaries.append(line.strip())
    out["summary_lines"] = summaries
    with open(sys.argv[2], "w") as f:
        json.dump(out, f, indent=1, sort_keys=True)
    for tool, s in out["by_tool"].items():
        print(tool, json.dumps({k: v for k, v in s.items() if k not in ("per_config", "silent_mismatch_rows", "replaced_rows")}))


if __name__ == "__main__":
    main()

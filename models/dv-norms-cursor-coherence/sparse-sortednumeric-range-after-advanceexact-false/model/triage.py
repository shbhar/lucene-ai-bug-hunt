"""Aggregate Coherence run logs and classify mismatch signatures.

usage: python3 -I triage.py LOG...   (prints totals, then classes with an example each)
"""
import collections
import re
import sys


def classify(view, typ, kind, seq, example):
    single = None
    m = re.search(r"truth=(.*)$", example)
    truth = m.group(1) if m else ""
    multi_vals = re.search(r"=\[\d+,", truth) is not None
    if view.startswith("MUTANT"):
        return "MUTANT (vacuity probe, must fire)"
    if ("multi-" in view or "slowComposite" in view) and "NullPointerException" in kind and seq.endswith("advance"):
        return "KNOWN LFV-136 (MultiDocValues advance after advanceExact NPE)"
    if ("multi-" in view or "slowComposite" in view) and typ == "NORMS" and "advance" in kind:
        return "KNOWN LFV-142 (MultiDocValues norms advance order)"
    if view.startswith("hookSorted-flush") and kind == "intoBitSet-docID" and typ in ("NUMERIC", "NORMS", "SORTED_NUMERIC"):
        return "KNOWN LFV-165 (sorting flush numeric dense intoBitSet end position)"
    if view.startswith("overlay") and typ == "SORTED_NUMERIC":
        return "KNOWN-PENDING dv-overlays/sortednumeric-dv-update (overlay sorted-numeric re-advanceExact stale cursor)"
    if view == "hook-flush" and ("intoBitSet" in seq) and kind in ("value", "final-value", "docValueCount", "final-docValueCount") or (view == "hook-flush" and "ArrayIndexOutOfBounds" in kind and "intoBitSet" in seq):
        if typ == "SORTED_SET" and not multi_vals:
            return "CAND-D + KNOWN LFV-091 (flush Buffered* stale value cursor; single-valued SS also singleton)"
        return "CAND-D (flush Buffered* intoBitSet does not advance the value cursor)"
    if typ == "SORTED_SET" and "intoBitSet" in seq and kind in ("value", "final-value") and not multi_vals and view in ("singletonSS", "exitable", "hook-merge", "hookSorted-flush", "hookSorted-merge", "codec", "mergeInstance", "filter", "asserting", "parallel", "multi-2seg", "multi-3seg", "slowComposite-2seg", "slowComposite-3seg", "sortingCodecReader", "sortingCodecReader-merge", "slowComposite-mergeInstance-2seg", "slowComposite-mergeInstance-3seg"):
        return "KNOWN LFV-091 (SingletonSortedSetDocValues.intoBitSet stale ord)"
    if view.startswith("ssSelector") and "intoBitSet" in seq:
        return "LEAD-G (SortedSetSelector.intoBitSet re-runs setOrd when intoBitSet does not move)"
    if re.search(r"advanceExact>(rangeIntoBitSet|ordinalRangeIntoBitSet)$", seq) or (
        ("rangeIntoBitSet" in kind or "ordinalRange" in seq) and ("advanceExact" in seq or "longValues" in seq or "binaryValues" in seq)):
        return "LEAD-B (bulk range call starting on an advanceExact()==false doc)"
    if typ in ("SORTED_NUMERIC", "SORTED_SET") and "read1>intoBitSet" in seq and kind in ("value", "final-value"):
        return "LEAD-A (intoBitSet(upTo<=docID) resets/advances the value cursor of the current doc)"
    if "range-left-position" in kind or "position-beyond" in kind:
        return "OBS (range call left the iterator beyond toDoc)"
    return "UNCLASSIFIED"


def main(paths):
    totals = collections.Counter()
    agg = collections.Counter()
    ex = {}
    for p in paths:
        lines = open(p).read().split("\n")
        for i, l in enumerate(lines):
            if l.startswith("TOTAL "):
                for kv in l.split()[1:]:
                    k, v = kv.split("=")
                    totals[k] += int(v)
            if l.startswith("SIG "):
                _, cnt, sig = l.split(" ", 2)
                view, typ, kind, seq = sig.split("|", 3)
                e = lines[i + 1].strip() if i + 1 < len(lines) else ""
                c = classify(view, typ, kind, seq, e)
                agg[(c, view, typ, kind)] += int(cnt)
                ex.setdefault((c, view, typ, kind), (seq, e[:600]))
    print("TOTALS", dict(totals))
    byclass = collections.Counter()
    for (c, v, t, k), n in agg.items():
        byclass[c] += n
    for c, n in sorted(byclass.items()):
        print("CLASS", n, c)
    for key in sorted(agg):
        print(agg[key], key, ex[key][0])
        print("     ", ex[key][1])


if __name__ == "__main__":
    main(sys.argv[1:])

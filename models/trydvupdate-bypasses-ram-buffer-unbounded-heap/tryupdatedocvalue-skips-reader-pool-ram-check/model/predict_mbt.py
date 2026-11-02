"""abstract models of the reader pool's pending doc-values bytes P, checked against the real-jar MBT observations.

File:line map (main 645d4fe14a2):
  try burst T/B/S  -> IndexWriter.tryUpdateDocValue (IndexWriter.java:1721-1781) -> ReadersAndUpdates.addDVUpdate (ReadersAndUpdates.java:153-164)
  RAM rule         -> IndexWriter.writeSomeDocValuesUpdates (IndexWriter.java:807-881): write when P > 0.5 * buffer
  rule call site   -> only the frozen-packet apply loop, IndexWriter.forceApply (IndexWriter.java:6600)
  F / R / C        -> writeReaderPool -> ReaderPool.writeAllDocValuesUpdates (IndexWriter.java:4117-4126, 4424, 608, 3827)
  U                -> buffered in the delete queue; resolved only when a packet is frozen and applied (flush, NRT open, commit, delete-RAM flush)
  X                -> automatic segment flush; applies a frozen global packet only if term updates are buffered

Model H (as implemented): T/B/S add their exact per-burst bytes; only F/R/C and a packet apply (X or F after buffered U) write.
Model R (documented RAM-buffer rule, LiveIndexWriterConfig.java:158-163 + IndexWriter.java:811): every operation that adds resolved
bytes runs the 50% rule, so P <= 0.5 * buffer + one call's bytes <= buffer after every operation.
usage: python3 -I predict_mbt.py <mbt-main.txt> <mbt-fix.txt>
"""
import sys

BUF = 1048576
DELTA = {"T": 668000, "B": 1060000, "S": 668000}   # per-burst bytes, from the length-1 run (334 / 530 / 334 bytes per call)
MAX_CALL = 530


def parse(path):
    out = {}
    for line in open(path):
        if line.startswith("#") or " P=" not in line:
            continue
        seq, rest = line.split(" P=")
        p = [int(v) for v in rest.split(" ")[0].split(",")]
        out[seq] = (p, line.rstrip().endswith("VIOLATION"))
    return out


def model_h(seq):
    """Returns (predicted P list or None where not exactly predictable, predicted violation)."""
    p, buffered, exact, preds, viol = 0, False, True, [], False
    for op in seq:
        if op in DELTA:
            p += DELTA[op]
        elif op == "U":
            buffered = True
            exact = False          # resolved bytes of the U packet are not modelled exactly
        elif op in "FRC":
            p, buffered = 0, False
        elif op == "X":
            if buffered:           # packet applied during the segment flush -> writeSomeDocValuesUpdates
                if p > 0.5 * BUF:
                    p = 0
                buffered = False
        # D, A: no change
        viol |= p > BUF
        preds.append(p if exact else None)
    return preds, viol


def main(main_path, fix_path):
    obs_main, obs_fix = parse(main_path), parse(fix_path)
    assert obs_main.keys() == obs_fix.keys(), "sequence sets differ"
    n = len(obs_main)
    h_status_mismatch, h_exact_checked, h_exact_mismatch = [], 0, []
    for seq, (p, v) in obs_main.items():
        preds, pv = model_h(seq)
        if pv != v:
            h_status_mismatch.append(seq)
        for i, q in enumerate(preds):
            if q is not None:
                h_exact_checked += 1
                if q != p[i]:
                    h_exact_mismatch.append((seq, i, q, p[i]))
    r_main_viol = sum(1 for (_, v) in obs_main.values() if v)
    r_fix_bad = [(s, p) for s, (p, v) in obs_fix.items() if max(p) > 0.5 * BUF + MAX_CALL]
    tryfree = [s for s in obs_main if not any(c in s for c in "TBS")]
    tryfree_viol = [s for s in tryfree if obs_main[s][1]]
    print(f"sequences={n} (alphabet TBSUDAXFRC)")
    print(f"model H vs main: violation-status mismatches={len(h_status_mismatch)} {h_status_mismatch[:5]}")
    print(f"model H vs main: exact P checks={h_exact_checked} mismatches={len(h_exact_mismatch)} {h_exact_mismatch[:5]}")
    print(f"model R vs main: R predicts 0 violations, main has {r_main_viol}")
    print(f"model R vs fix : sequences with P > 0.5*buffer + one call = {len(r_fix_bad)} {r_fix_bad[:3]}")
    print(f"sequences without any try-update op (T/B/S): {len(tryfree)}, violations among them: {len(tryfree_viol)}")
    print(f"max P on main = {max(max(p) for p, _ in obs_main.values())} bytes; max P with fix = {max(max(p) for p, _ in obs_fix.values())} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

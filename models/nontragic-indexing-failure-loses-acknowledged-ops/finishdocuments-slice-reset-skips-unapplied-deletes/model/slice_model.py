"""Bounded exhaustive model of one in-memory segment's delete slice (DocumentsWriterPerThread + DeleteSlice).

Model element -> Java (main e1faeba08bde)
  queue (list of node ids)          DocumentsWriterDeleteQueue tail/next chain
  head, tail (indices into queue)   DeleteSlice.sliceHead / sliceTail            DocumentsWriterDeleteQueue.java:339-372
  apply()                           DeleteSlice.apply: nodes head+1..tail, reset() only after the last  :348-367
  reset()                           head = tail                                    :369-372
  op 'del'                          IndexWriter.deleteDocuments -> queue.addDelete (global apply only)
  op 'add'                          updateDocuments -> finishDocuments(null): updateSlice; <0 -> apply, else reset()
                                    DocumentsWriterPerThread.java:376-386, DocumentsWriterDeleteQueue.java:284-293
  op 'upd'                          updateDocument -> finishDocuments(node): add(node, slice) sets sliceTail=node
                                    (:166), tryApplyGlobalSlice (:168, may throw = fault 'G'), then apply (:375)
  op 'commit'                       prepareFlush: freezeGlobalBuffer sets sliceTail = queue tail, apply, reset (:427-438)
  fault 'P' at node k               a node's apply throws in the per-thread apply (ByteBlockPool overflow)
  fault 'G'                         tryApplyGlobalSlice throws inside add(node, slice) (global buffer overflow, #160939)
Faults are non-aborting on stock (writer stays open). Fix = a throw out of finishDocuments aborts (writer closes).
Oracle: at a commit that succeeds, every node acknowledged before it (a 'del' that returned, an 'upd' whose node was
queued) is in the applied set, or the writer is closed. The fault happens at most once (the call that throws is not
acknowledged; its own node, if any, is not required). After a fault, 'persistent' mode makes every later apply that
reaches a not-yet-applied node fail again (the overflowed pool), 'transient' mode does not.
"""
import itertools, sys

OPS = ["del", "add", "upd", "commit"]


def run(seq, fault, mode, fixed):
    """seq: list of ops; fault: (step index, kind 'P' or 'G', k) or None. Returns (violation, detail)."""
    queue = [0]  # sentinel node 0
    head = tail = 0
    applied = set()
    acked = set()
    failed_once = False
    closed = False

    def apply_slice(fail_at=None):
        # apply nodes head+1..tail; fail_at = 1-based position in the slice where a node throws
        nonlocal head, failed_once
        if fail_at is not None and fail_at > tail - head:
            raise ValueError("no fault possible")  # the apply never reaches position fail_at
        pos = 0
        for n in range(head + 1, tail + 1):
            pos += 1
            node = queue[n]
            if fail_at is not None and pos == fail_at:
                failed_once = True
                raise RuntimeError("P")
            if mode == "persistent" and failed_once and node not in applied:
                raise RuntimeError("P")
            applied.add(node)
        head = tail  # reset() after the last node

    for i, op in enumerate(seq):
        if closed:
            break
        f = fault if (fault is not None and fault[0] == i) else None
        try:
            if op == "del":
                queue.append(len(queue)); acked.add(queue[-1])
            elif op == "add":
                if tail != len(queue) - 1:          # updateSlice: new deletes since last update
                    tail = len(queue) - 1
                    apply_slice(f[2] if f and f[1] == "P" else None)
                else:
                    if f and f[1] == "P":
                        raise ValueError("no fault possible")  # nothing to apply: fault cannot fire
                    # invariant: without an earlier failed apply this reset() is a no-op (slice already empty)
                    assert failed_once or head == tail, "reset() dropped nodes without a prior failure"
                    head = tail                     # reset()
            elif op == "upd":
                queue.append(len(queue)); node = queue[-1]
                tail = len(queue) - 1               # add(node, slice): sliceTail = node
                if f and f[1] == "G":
                    failed_once = True
                    raise RuntimeError("G")        # tryApplyGlobalSlice threw; per-thread apply never ran
                apply_slice(f[2] if f and f[1] == "P" else None)
                acked.add(node)
            elif op == "commit":
                tail = len(queue) - 1
                apply_slice()                       # prepareFlush apply (a failure here is LFV-1078's root)
                missing = acked - applied
                if missing:
                    return True, "commit at step %d lacks acknowledged nodes %s" % (i, sorted(missing))
                applied.clear(); acked.clear()      # flushed: start a fresh segment state
                queue = [0]; head = tail = 0
        except ValueError:
            return None, "fault not applicable"
        except RuntimeError as e:
            if fixed:
                closed = True                       # fix: aborting -> tragic close
            elif str(e) == "P" and fault is not None and fault[0] != i:
                # a later persistent failure: the call throws, writer stays open (stock)
                pass
    return False, ""


def main():
    maxlen = int(sys.argv[1]) if len(sys.argv) > 1 else 6
    for mode in ("transient", "persistent"):
        for fixed in (False, True):
            total = viol = control_viol = inapplicable = 0
            shortest = None
            by_kind = {"P": 0, "G": 0}
            short_g = None
            for n in range(1, maxlen + 1):
                for seq in itertools.product(OPS, repeat=n):
                    seq = list(seq)
                    if seq[-1] != "commit":
                        continue
                    v0, _ = run(seq, None, mode, fixed)       # control: no fault
                    control_viol += bool(v0)
                    for i, op in enumerate(seq):
                        kinds = []
                        if op in ("add", "upd"):
                            kinds += [("P", k) for k in range(1, n + 2)]
                        if op == "upd":
                            kinds.append(("G", 0))
                        for kind, k in kinds:
                            v, d = run(seq, (i, kind, k), mode, fixed)
                            if v is None:
                                inapplicable += 1
                                continue
                            total += 1
                            if v:
                                viol += 1
                                by_kind[kind] += 1
                                if kind == "G" and (short_g is None or len(seq) < len(short_g[0])):
                                    short_g = (seq, (i, kind, k), d)
                                if shortest is None or len(seq) < len(shortest[0]):
                                    shortest = (seq, (i, kind, k), d)
            print("mode=%-10s %-5s scenarios=%d (fault not applicable: %d) violations=%d control(no fault) violations=%d"
                  % (mode, "fix" if fixed else "stock", total, inapplicable, viol, control_viol))
            print("   violations by fault kind:", by_kind)
            if shortest:
                print("   shortest counterexample:", shortest)
            if short_g:
                print("   shortest G (global-buffer) counterexample:", short_g)


if __name__ == "__main__":
    main()

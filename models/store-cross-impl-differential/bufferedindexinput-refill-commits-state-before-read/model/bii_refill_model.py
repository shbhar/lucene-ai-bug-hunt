"""
Explicit-state model check of BufferedIndexInput's buffer invariant under readInternal failures.

Model element -> Java (lucene/core/src/java/org/apache/lucene/store/BufferedIndexInput.java @ ea57360912ed):
  state (bufferStart, position, limit, content[])  <-> fields bufferStart, buffer.position(), buffer.limit(), buffer bytes
  op_read_byte                                     <-> readByte()                      :51-57
  op_seek(p)                                       <-> seek(long)                      :325-334
  op_read_byte_at(p)                               <-> readByte(long) / resolvePositionInBuffer :221-248
  refill(fail)                                     <-> refill()                        :289-311 (readInternal at 305)
  ORDER = "main": position/limit/bufferStart set before readInternal (302-305)
  ORDER = "fixed": on a readInternal failure the buffer is emptied (limit 0), the fix sketch in fix/
  ORDER = "pre-9271": state committed only after readInternal returns (the LUCENE-1262 fix, 2008-2020)
A readInternal failure leaves the buffer bytes untouched (the closed-channel case).

Property (safety): every byte a read returns equals file[file pointer before the read] (sequential) or
file[pos] (positional). Exhaustive over all op sequences of length <= DEPTH with failures injected at any refill.
"""
import itertools, json, sys

def check(order, L, B, depth):
    file = [10 + i for i in range(L)]
    ops = [("rb",)] + [("seek", p) for p in range(L + 1)] + [("rba", p) for p in range(L)]
    # each op may run with the next readInternal failing (f=True) or not
    alphabet = [(o, f) for o in ops for f in (False, True)]
    violations = []
    explored = 0
    for n in range(1, depth + 1):
        for seq in itertools.product(alphabet, repeat=n):
            explored += 1
            st = {"bs": 0, "pos": 0, "lim": 0, "buf": [None] * B, "alloc": False}
            def refill(fail):
                start = st["bs"] + st["pos"]
                end = min(start + B, L)
                newlen = end - start
                if newlen <= 0:
                    raise EOFError
                if order in ("main", "fixed"):
                    st["pos"], st["lim"], st["bs"] = 0, newlen, start
                    if fail:
                        if order == "fixed":
                            st["lim"] = 0; st["pos"] = 0
                        raise IOError
                    for i in range(newlen):
                        st["buf"][i] = file[start + i]
                else:  # pre-9271: read first, commit after
                    if fail:
                        raise IOError
                    for i in range(newlen):
                        st["buf"][i] = file[start + i]
                    st["pos"], st["lim"], st["bs"] = 0, newlen, start
            bad = None
            for (op, fail) in seq:
                try:
                    if op[0] == "rb":
                        fp = st["bs"] + st["pos"]
                        if st["pos"] >= st["lim"]:
                            refill(fail)
                        v = st["buf"][st["pos"]]; st["pos"] += 1
                        if fp >= L or v != file[fp]:
                            bad = ("rb", fp, v); break
                    elif op[0] == "seek":
                        p = op[1]
                        if st["bs"] <= p < st["bs"] + st["lim"]:
                            st["pos"] = p - st["bs"]
                        else:
                            st["bs"] = p; st["lim"] = 0; st["pos"] = 0
                    else:  # positional readByte(p): resolvePositionInBuffer, forward/backward reposition then refill
                        p = op[1]
                        idx = p - st["bs"]
                        if not (0 <= idx <= st["lim"] - 1):
                            if idx < 0:
                                s = max(st["bs"] - B, p + 1 - B); s = max(s, 0); s = min(s, p)
                                st["bs"] = s
                            else:
                                st["bs"] = p
                            st["lim"] = 0; st["pos"] = 0
                            refill(fail)
                            idx = p - st["bs"]
                        v = st["buf"][idx]
                        if v != file[p]:
                            bad = ("rba", p, v); break
                except (IOError, EOFError):
                    continue  # the caller catches and goes on (retry), as TestFieldsReader#testExceptions does
            if bad:
                violations.append({"seq": [list(map(str, o)) + (["FAIL"] if f else []) for (o, f) in seq], "bad": bad})
                if len(violations) >= 3:
                    return explored, violations
    return explored, violations

if __name__ == "__main__":
    out = {}
    for order in ("main", "fixed", "pre-9271"):
        for (L, B, depth) in ((4, 2, 4), (5, 2, 4), (6, 3, 4)):
            explored, v = check(order, L, B, depth)
            out[f"{order} L={L} B={B} depth={depth}"] = {"sequences": explored, "violations": len(v), "first": v[:1]}
            print(order, L, B, depth, "sequences", explored, "violations", len(v), v[:1])
    json.dump(out, open(sys.argv[1] if len(sys.argv) > 1 else "/dev/stdout", "w"), indent=1)

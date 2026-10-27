"""Z3 BV model of the byte-count / padding / offset arithmetic of the dense byte-aligned bulk decode.

Exact Java semantics: int = BitVec(32) with wrap, long = BitVec(64), (long) int = SignExt.

Model element -> Java line (Lucene main @ ea57360912ed)
  canBulkDecodeByteAligned          Lucene90DocValuesProducer.java:644-646
  isContiguous                      :648-650
  paddingBytesNeededForBulkDecode   :652-659
  bytesPerValue                     :675
  byteCountLong + guard             :676-679   (byteCountLong > Integer.MAX_VALUE -> return null = fallback)
  byteCount                         :680
  readByteCount                     :681-682   (int addition, wraps)
  offset                            :683
  slice-length guard                :684-686   (offset + readByteCount > slice.length() -> fallback)
  allocation                        :687-689   (new byte[readByteCount] when bytes.length < readByteCount)
  readBytes / decode                :690-694, DefaultDocValuesBulkDecodeSupport.java:94-164,
                                    PanamaDocValuesBulkDecodeSupport.java:43-54
  slice.length()                    = numValues * bpv/8 + DirectWriter.paddingBytesNeeded(bpv)
                                      (DirectWriter.java:145-178, 247; Lucene90DocValuesConsumer.java:535)

Preconditions (documented limits):
  maxDoc in [1, IndexWriter.MAX_DOCS = 2^31 - 129], dense: numValues == maxDoc
  contiguous docs d0 .. d0+size-1 inside [0, maxDoc)
  JDK 25 HotSpot max byte[] length = 2^31 - 3 = 2147483645 (measured: max-byte-array-jdk25.txt)

Properties checked for every bpv in {8,16,...,64} when both guards let the bulk path run:
  P1 readByteCount >= 0                       (no int overflow)
  P2 readByteCount <= VM_MAX_BYTE_ARRAY       (the new byte[] cannot fail with 'exceeds VM limit')
  P3 the decoder's last read ends <= readByteCount (decode reads only the bytes that were read)
  P4 (completeness, not safety) the slice guard never sends a legal contiguous request to the fallback
  P5 Panama: bytesOffset + i*Long.BYTES and the loop bound do not overflow int
"""
import json
import sys
import time
from z3 import (BitVec, BitVecVal, SignExt, Extract, If, Solver, And, Or, Not, sat, unsat, ULE)

INT_MAX = 2**31 - 1
MAX_DOCS = 2**31 - 129
VM_MAX_BYTE_ARRAY = 2**31 - 3


def i32(v):
    return BitVecVal(v, 32)


def i64(v):
    return BitVecVal(v, 64)


def L(x32):
    return SignExt(32, x32)


def producer_pad(bpv):
    if bpv == 24:
        return 1
    if bpv in (40, 48, 56):
        return 8 - bpv // 8
    return 0


def directwriter_pad_bytes(bpv):
    if bpv > 32:
        bits = 64 - bpv
    elif bpv > 16:
        bits = 32 - bpv
    elif bpv > 8:
        bits = 16 - bpv
    else:
        bits = 0
    return (bits + 7) // 8


READ_WIDTH = {8: 1, 16: 2, 24: 4, 32: 4, 40: 8, 48: 8, 56: 8, 64: 8}


def model(bpv):
    size = BitVec("size", 32)
    d0 = BitVec("d0", 32)
    maxDoc = BitVec("maxDoc", 32)
    bytesPerValue = i32(bpv // 8)
    pre = And(maxDoc >= 1, maxDoc <= MAX_DOCS, d0 >= 0, size >= 1, d0 < maxDoc,
              L(d0) + L(size) <= L(maxDoc))  # contiguous docs inside the segment
    byteCountLong = L(size) * L(bytesPerValue)
    guard1_pass = Not(byteCountLong > i64(INT_MAX))
    byteCount = Extract(31, 0, byteCountLong)
    readByteCount = If(byteCount == i32(0), i32(0), byteCount + i32(producer_pad(bpv)))
    offset = If(byteCount == i32(0), i64(0), L(d0) * L(bytesPerValue))
    sliceLength = L(maxDoc) * L(bytesPerValue) + i64(directwriter_pad_bytes(bpv))
    guard2_pass = Not(offset + L(readByteCount) > sliceLength)
    return dict(size=size, d0=d0, maxDoc=maxDoc, pre=pre, g1=guard1_pass, g2=guard2_pass,
                byteCountLong=byteCountLong, byteCount=byteCount, readByteCount=readByteCount,
                offset=offset, sliceLength=sliceLength)


def enumerate_sizes(bpv, prop_violation, limit=50):
    """All sizes (up to limit) for which some legal (d0, maxDoc) reaches the bulk path and violates."""
    m = model(bpv)
    s = Solver()
    s.set("timeout", 300000)
    s.add(m["pre"], m["g1"], m["g2"], prop_violation(m))
    out = []
    while len(out) < limit and s.check() == sat:
        mod = s.model()
        sz = mod[m["size"]].as_signed_long()
        out.append(dict(size=sz, d0=mod[m["d0"]].as_signed_long(), maxDoc=mod[m["maxDoc"]].as_signed_long(),
                        byteCount=mod.eval(m["byteCount"]).as_signed_long(),
                        readByteCount=mod.eval(m["readByteCount"]).as_signed_long()))
        s.add(m["size"] != mod[m["size"]])
    final = s.check() if len(out) < limit else "limit"
    return out, str(final)


def main():
    report = {"VM_MAX_BYTE_ARRAY": VM_MAX_BYTE_ARRAY, "MAX_DOCS": MAX_DOCS, "per_bpv": {}}
    for bpv in (8, 16, 24, 32, 40, 48, 56, 64):
        w = READ_WIDTH[bpv]
        props = {
            "P1_readByteCount_negative": lambda m: m["readByteCount"] < i32(0),
            "P2_exceeds_vm_array_limit": lambda m: And(m["readByteCount"] >= i32(0),
                                                       m["readByteCount"] > i32(VM_MAX_BYTE_ARRAY)),
            # last decoder read: byte index (size-1)*bpv8 .. +w must be <= readByteCount (64-bit math)
            "P3_decode_reads_past_readByteCount": lambda m, w=w: And(
                m["readByteCount"] >= i32(0),
                (L(m["size"]) - i64(1)) * i64(bpv // 8) + i64(w) > L(m["readByteCount"])),
        }
        res = {}
        for name, viol in props.items():
            t = time.time()
            sols, final = enumerate_sizes(bpv, viol)
            res[name] = {"violations": sols, "exhausted": final, "secs": round(time.time() - t, 2)}
            print(f"  bpv={bpv} {name}: {len(sols)} violation(s), final={final}, {time.time() - t:.1f}s", flush=True)
        # P4 completeness: guard2 rejects some legal contiguous request?
        m = model(bpv)
        s = Solver()
        s.set("timeout", 300000)
        s.add(m["pre"], m["g1"], Not(m["g2"]), m["readByteCount"] >= i32(0))
        t = time.time()
        r4 = s.check()
        print(f"  bpv={bpv} P4: {r4} {time.time() - t:.1f}s", flush=True)
        res["P4_slice_guard_rejects_legal_nonoverflow_request"] = str(r4)
        # P5 Panama 64-bit loop arithmetic (only bpv 64 takes the vector path)
        if bpv == 64:
            size = m["size"]
            i = BitVec("i", 32)
            s5 = Solver()
            s5.set("timeout", 300000)
            s5.add(m["pre"], m["g1"], m["g2"], i >= 0, i <= size)
            s5.add(Or(L(i) * i64(8) > i64(INT_MAX)))
            res["P5_panama_index_overflow"] = str(s5.check())
        report["per_bpv"][bpv] = res
        print(f"bpv={bpv}: " + "; ".join(
            f"{k}={len(v['violations'])} [{v['exhausted']}]" if isinstance(v, dict) else f"{k}={v}"
            for k, v in res.items()), flush=True)
        for k, v in res.items():
            if isinstance(v, dict):
                for sol in v["violations"]:
                    print(f"   {k}: {sol}")
    json.dump(report, open(sys.argv[1], "w"), indent=1)


if __name__ == "__main__":
    main()

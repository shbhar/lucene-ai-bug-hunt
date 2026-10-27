"""Z3 BV model of ByteBuffersDataOutput.copyBytes(DataInput, long numBytes).

Model element -> Java line mapping (Lucene main @ ea57360912ed):
  int length = (int) numBytes             ByteBuffersDataOutput.java:314  (Java narrowing: low 32 bits, two's complement)
  while (length > 0) { ... length -= chunk } ByteBuffersDataOutput.java:315-327 (heap blocks: copies exactly `length`)
  if (length > 0) super.copyBytes(...)    ByteBuffersDataOutput.java:329-331 (direct blocks: rest via DataOutput)
  contract                                DataOutput.java:279-280  "Copy numBytes bytes from input to ourself." (long)
  reference                               DataOutput.java:281-293  long left = numBytes; loops until left == 0

So the override copies copied(numBytes) = max(0, (int) numBytes) bytes; the reference copies numBytes bytes.

Queries (numBytes is a 64-bit BV constrained to the asserted precondition numBytes >= 0):
  Q1  exists numBytes >= 0 with copied != numBytes                          -> SAT, minimal witness 2^31
  Q2  numBytes <= Integer.MAX_VALUE  =>  copied == numBytes                  -> UNSAT of the negation
  Q3  exists numBytes > 0 with copied == 0 (silently copies nothing)         -> SAT; region [2^31, 2^32) included
  Q4  exists numBytes >= 2^32 with copied == numBytes mod 2^32 != 0 (partial) -> SAT
  Q5  fix: long length = numBytes; copied == numBytes for all numBytes >= 0 -> UNSAT of the negation
  probe False -> UNSAT
"""
import json
import sys
from z3 import BitVec, BitVecVal, Extract, SignExt, If, Solver, Optimize, Not, And, sat, BoolVal, UGE, ULT

def copied_main(nb):
    length32 = Extract(31, 0, nb)
    length = SignExt(32, length32)               # (int) numBytes, as a signed 64-bit value
    return If(length > 0, length, BitVecVal(0, 64))

def copied_fix(nb):
    return If(nb > 0, nb, BitVecVal(0, 64))

def main():
    out = {}
    nb = BitVec("numBytes", 64)
    pre = nb >= 0  # signed: the method asserts numBytes >= 0

    o = Optimize(); o.add(pre, copied_main(nb) != nb); h = o.minimize(nb)
    r = o.check(); out["Q1"] = str(r); out["Q1_min_witness"] = o.model()[nb].as_long() if r == sat else None

    s = Solver(); s.add(pre, nb <= 0x7FFFFFFF, copied_main(nb) != nb); out["Q2_negation"] = str(s.check())

    s = Solver(); s.add(pre, nb > 0, copied_main(nb) == 0, UGE(nb, 1 << 31), ULT(nb, 1 << 32))
    out["Q3_copies_nothing_in_[2^31,2^32)"] = str(s.check())
    s = Solver(); s.add(pre, nb > 0, copied_main(nb) == 0, Not(And(UGE(nb, 1 << 31), ULT(nb, 1 << 32))),
                        ULT(nb, 1 << 33))
    r = s.check(); out["Q3b_copies_nothing_outside_that_range_below_2^33"] = str(r)
    out["Q3b_witness"] = s.model()[nb].as_long() if r == sat else None

    s = Solver(); s.add(pre, UGE(nb, 1 << 32), copied_main(nb) == (nb & 0xFFFFFFFF), copied_main(nb) != 0,
                        copied_main(nb) != nb)
    r = s.check(); out["Q4_partial_copy"] = str(r); out["Q4_witness"] = s.model()[nb].as_long() if r == sat else None

    s = Solver(); s.add(pre, copied_fix(nb) != nb); out["Q5_fix_negation"] = str(s.check())
    s = Solver(); s.add(BoolVal(False)); out["probe_false"] = str(s.check())
    print(json.dumps(out))
    with open(sys.argv[1] if len(sys.argv) > 1 else "copybytes_cast_z3.json", "w") as f:
        json.dump(out, f, indent=1)

if __name__ == "__main__":
    main()

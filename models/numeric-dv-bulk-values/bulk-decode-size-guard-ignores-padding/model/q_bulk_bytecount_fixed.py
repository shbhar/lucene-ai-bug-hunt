"""Same obligations as q_bulk_bytecount.py with the candidate fix (fix/fix.diff): the guard compares the
PADDED byte count, computed in long, with ArrayUtil.MAX_ARRAY_LENGTH = 2^31 - 1 - 16."""
import sys
sys.path.insert(0, ".")
import q_bulk_bytecount as q
from z3 import Not, If, And, Solver, sat

MAX_ARRAY_LENGTH = 2**31 - 1 - 16
orig_model = q.model


def fixed_model(bpv):
    m = orig_model(bpv)
    pad = q.producer_pad(bpv)
    rbcl = If(m["byteCountLong"] == q.i64(0), q.i64(0), m["byteCountLong"] + q.i64(pad))
    m["g1"] = Not(rbcl > q.i64(MAX_ARRAY_LENGTH))
    return m


q.model = fixed_model
bad = 0
for bpv in (8, 16, 24, 32, 40, 48, 56, 64):
    for name, viol in {
        "P1_readByteCount_negative": lambda m: m["readByteCount"] < q.i32(0),
        "P2_exceeds_vm_array_limit": lambda m: m["readByteCount"] > q.i32(q.VM_MAX_BYTE_ARRAY),
    }.items():
        sols, final = q.enumerate_sizes(bpv, viol)
        bad += len(sols)
        print(f"fixed bpv={bpv} {name}: {len(sols)} violation(s) [{final}]", flush=True)
print("TOTAL violations with fix:", bad)

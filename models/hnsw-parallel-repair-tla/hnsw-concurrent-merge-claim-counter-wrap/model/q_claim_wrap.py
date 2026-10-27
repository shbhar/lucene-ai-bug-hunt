# Z3 (bit-vector, exact Java int semantics) model of the claim counters in
# HnswConcurrentMergeBuilder (Lucene main @ ea57360912ed):
#   insertion: start = workProgress.getAndAdd(2048); if (start < maxOrd) ... (lines 305-312)
#   repair:    from = repairProgress.getAndAdd(64);  while (from < total) (line 188)
# The k-th getAndAdd (k = 0, 1, ...) returns (int)(k * B) (32-bit two's complement). Exactly
# n = ceil(N / B) claims succeed honestly; then each of the T tasks makes one more claim, so the
# claims k = n .. n + T - 1 are evaluated. A claim with k >= n whose Java value is < N (it wrapped
# negative) is a spurious success: addGraphNode / IntsRef with negative ordinals.
# Q1: for each worker count W, the exact set of N in [1, MAX_DOCS] with a spurious success is
#     N > 2^31 - B*W  (SAT witness at the boundary, UNSAT below it).
# Q2: the "end" overflow in run(): end = min(maxOrd, start + 2048) wraps for a legal start.
# Q3: repair: total <= MAX_DOCS - 1 (at least one deleted node), taskCount = min(W, ceil(total/64)).
import json, sys
from z3 import LShR, BitVec, BitVecVal, Solver, And, Or, ULT, ULE, UGE, Extract, ZeroExt, SignExt, sat, unsat, If

MAX_DOCS = 2**31 - 129
out = {}

def spurious(N64, k64, B, W64):
    """N, k, W as 64-bit unsigned BVs (no wrap); returns the condition of a spurious success."""
    sh = B.bit_length() - 1                  # B is a power of two (2048, 64)
    assert 1 << sh == B
    n = LShR(N64 + (B - 1), sh)              # ceil(N/B) for N >= 1
    val64 = k64 << sh                         # exact k*B (k < 2^34 below)
    jv = SignExt(32, Extract(31, 0, val64))   # Java int value, sign-extended to 64 bits
    return And(UGE(k64, n), ULT(k64, n + W64), jv < N64)  # signed compare on 64-bit

def check(B, W, lo, hi):
    s = Solver()
    N = BitVec('N', 64); k = BitVec('k', 64)
    s.add(ULE(BitVecVal(lo, 64), N), ULE(N, BitVecVal(hi, 64)), ULT(k, BitVecVal(2**34, 64)))
    s.add(spurious(N, k, B, BitVecVal(W, 64)))
    r = s.check()
    m = s.model() if r == sat else None
    return str(r), (m[N].as_long() if m is not None else None), (m[k].as_long() if m is not None else None)

res = {}
for B, label in ((2048, 'insert'), (64, 'repair')):
    for W in (1, 2, 3, 4, 8, 16, 64, 512):
        bound = 2**31 - B * W   # predicted: spurious iff N > bound
        below = check(B, W, 1, min(bound, MAX_DOCS))
        above = check(B, W, bound + 1, MAX_DOCS) if bound + 1 <= MAX_DOCS else ('n/a (bound >= MAX_DOCS)', None, None)
        res[f'{label} B={B} W={W}'] = {'predicted_first_bad_N': bound + 1,
                                       'N<=bound': below[0], 'N>bound': above[0],
                                       'witness_N': above[1], 'witness_k': above[2]}
out['Q1'] = res

# Q2: end = Math.min(maxOrd, start + 2048) overflows for a legal start (start < maxOrd <= MAX_DOCS)
s = Solver(); st = BitVec('start', 32); mo = BitVec('maxOrd', 32)
s.add(st >= 0, st < mo, mo <= MAX_DOCS, (st % 2048) == 0)
s.add(st + 2048 < st)  # signed wrap
r = s.check(); out['Q2_end_overflow'] = {'result': str(r), 'witness': (s.model()[st].as_long(), s.model()[mo].as_long()) if r == sat else None}

# Q3: repair counter reachable? total = flagged nodes on one level <= live nodes <= MAX_DOCS - 1
q3 = {}
for W in (1, 2, 3, 4, 8, 16, 64):
    T = W  # taskCount = min(W, ceil(total/64)) = W for total > 64*W
    bound = 2**31 - 64 * T
    r = check(64, T, bound + 1, MAX_DOCS - 1) if bound + 1 <= MAX_DOCS - 1 else ('unsat (empty range)', None, None)
    q3[f'W={W}'] = {'first_bad_total': bound + 1, 'max_legal_total': MAX_DOCS - 1, 'result': r[0], 'witness_total': r[1],
                    'max_deleted_nodes_for_it': (MAX_DOCS - (bound + 1)) if bound + 1 <= MAX_DOCS - 1 else None}
out['Q3_repair'] = q3
json.dump(out, sys.stdout, indent=1)
print()

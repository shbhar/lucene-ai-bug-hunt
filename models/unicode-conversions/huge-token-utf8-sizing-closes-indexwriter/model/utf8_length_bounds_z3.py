"""
Z3 bit-vector model of Lucene's UTF-16 -> UTF-8 length helpers and of the buffer-sizing chain that
converts an indexed token to UTF-8 (unicode-conversions, unicode-conversions).

Model element -> Java line map (Lucene main ea57360912ed):
  step(code, next, hasNext)   UnicodeUtil.java:251-273 calcUTF16toUTF8Length loop body
                              (identical branch structure to UTF16toUTF8 char[] :144-178 and
                               CharSequence :204-237; equality of the three is checked exhaustively
                               on the real jar by harness/UnicodeTV.java)
  maxUTF8Length(n)            UnicodeUtil.java:677-679  Math.multiplyExact(n, 3)
  MAX_ARRAY_LENGTH            ArrayUtil.java:33-34      Integer.MAX_VALUE - NUM_BYTES_ARRAY_HEADER (16 on
                              the 64-bit JVMs used here; the message 'maximum array in java (2147483631)'
                              in repro/main/result.json confirms the runtime value)
  oversize(minSize, 1) throws ArrayUtil.java:146        minTargetSize > MAX_ARRAY_LENGTH -> IAE
  copyChars                   BytesRefBuilder.java:121-133 growNoCopy(maxUTF8Length(len))
  getBytesRef                 CharTermAttributeImpl.java:92-95 builder.copyChars(termBuffer, 0, termLength)
  aborting region             IndexingChain.java:2019-2046 add(getBytesRef()) inside catch(Throwable)->onAbortingException
  MAX_TERM_LENGTH             IndexWriter.java:258  BYTE_BLOCK_SIZE - 2 = 32766
  MAX_STORED_STRING_LENGTH    IndexWriter.java:261-262 MAX_ARRAY_LENGTH / 3

Queries (each prints SAT/UNSAT and, for SAT, the model):
  Q1  per-step invariant: bytes added in one loop step b and units consumed c satisfy c <= b <= 3c
      (negation UNSAT => by induction utf8Len(s) in [len, 3*len] for every length: maxUTF8Length is a
      sound upper bound and the UTF-8 length is never below the UTF-16 length).
  Q2  multiplyExact(n,3) overflows iff n > 715,827,882 (for 0 <= n <= MAX_ARRAY_LENGTH).
  Q3  copyChars(n) fails with an explicit exception (ArithmeticException or oversize IAE), whatever the heap,
      iff n >= 715,827,878. (Below that, 3n bytes are allocated; an OutOfMemoryError for lack of heap is not modelled.)
  Q4  reachability of an aborting conversion failure for an immense term: exists n with
      MAX_TERM_LENGTH < n <= MAX_ARRAY_LENGTH (a legal CharTermAttribute length) whose conversion
      fails -> SAT, minimal witness 715,827,878.
  Q5  stored-field path (LUCENE-7538 fix) is safe: n <= MAX_STORED_STRING_LENGTH => 3n <= MAX_ARRAY_LENGTH
      (negation UNSAT).
  Q6  vacuity: the per-step encoding admits each branch (1,2,3-byte, pair, lone surrogate) -> SAT each;
      and 'False' probe UNSAT; a mutant bound 'b <= 2c' is refuted (SAT).
"""
import json
import sys

from z3 import (
    And,
    BitVec,
    BitVecVal,
    BoolVal,
    If,
    Not,
    Or,
    Solver,
    ULE,
    ULT,
    UGE,
    UGT,
    ZeroExt,
    BV2Int,
    sat,
    unsat,
)

results = {}


def run(name, s, expect):
    r = s.check()
    out = {"result": str(r), "expect": expect}
    if r == sat:
        out["model"] = {str(d): str(s.model()[d]) for d in s.model().decls()}
    results[name] = out
    ok = (str(r) == expect)
    print(f"{name}: {r} (expect {expect}) {'OK' if ok else 'UNEXPECTED'}")
    if r == sat:
        print("   model:", out["model"])
    return ok


all_ok = True

# ---------------- Q1: one loop step of calcUTF16toUTF8Length ----------------
code = BitVec("code", 32)  # (int) s.charAt(i), zero-extended char
nxt = BitVec("next", 32)  # (int) s.charAt(i+1)
has_next = BitVec("hasNext", 1)  # i < end - 1


def step(code, nxt, has_next):
    """returns (bytes_added, units_consumed) exactly as UnicodeUtil.java:252-272."""
    is_pair = And(
        UGE(code, 0xD800), ULT(code, 0xDC00), has_next == 1, UGE(nxt, 0xDC00), ULE(nxt, 0xDFFF)
    )
    b = If(
        ULT(code, 0x80),
        BitVecVal(1, 32),
        If(
            ULT(code, 0x800),
            BitVecVal(2, 32),
            If(
                Or(ULT(code, 0xD800), UGT(code, 0xDFFF)),
                BitVecVal(3, 32),
                If(is_pair, BitVecVal(4, 32), BitVecVal(3, 32)),
            ),
        ),
    )
    c = If(And(UGE(code, 0xD800), ULE(code, 0xDFFF), is_pair), BitVecVal(2, 32), BitVecVal(1, 32))
    return b, c


dom = And(ULE(code, 0xFFFF), ULE(nxt, 0xFFFF))
b, c = step(code, nxt, has_next)
s = Solver()
s.add(dom, Not(And(ULE(c, b), ULE(b, 3 * c))))
all_ok &= run("Q1_step_bytes_between_units_and_3units", s, "unsat")

# mutant bound: b <= 2c must be refuted (shows Q1 is not vacuous)
s = Solver()
s.add(dom, Not(ULE(b, 2 * c)))
all_ok &= run("Q1m_mutant_bound_2c_refuted", s, "sat")

# branch reachability (vacuity)
for label, cond in [
    ("1byte", ULT(code, 0x80)),
    ("2byte", And(UGE(code, 0x80), ULT(code, 0x800))),
    ("3byte", And(UGE(code, 0x800), Or(ULT(code, 0xD800), UGT(code, 0xDFFF)))),
    ("pair", b == 4),
    ("lone", And(UGE(code, 0xD800), ULE(code, 0xDFFF), b == 3)),
]:
    s = Solver()
    s.add(dom, cond)
    all_ok &= run(f"Q6_branch_{label}_reachable", s, "sat")
s = Solver()
s.add(dom, BoolVal(False))
all_ok &= run("Q6_false_probe", s, "unsat")

# ---------------- Q2/Q3: sizing arithmetic (exact Java int semantics) ----------------
INT_MAX = 2**31 - 1
MAX_ARRAY_LENGTH = INT_MAX - 16
n = BitVec("n", 32)
n64 = ZeroExt(32, n)
prod64 = n64 * 3
overflow = UGT(prod64, BitVecVal(INT_MAX, 64))  # multiplyExact throws
legal_n = ULE(n, MAX_ARRAY_LENGTH)  # a char[]/String length the JVM can hold (n >= 0 implied by ULE)

s = Solver()
s.add(legal_n, overflow != UGT(n, 715_827_882))
all_ok &= run("Q2_multiplyExact_overflow_iff_n_gt_715827882", s, "unsat")

fails = Or(overflow, UGT(prod64, BitVecVal(MAX_ARRAY_LENGTH, 64)))  # ArithmeticException or oversize IAE
s = Solver()
s.add(legal_n, fails != UGE(n, 715_827_878))
all_ok &= run("Q3_copyChars_fails_iff_n_ge_715827878", s, "unsat")

# ---------------- Q4: an immense term whose conversion fails inside the aborting region ----------------
MAX_TERM_LENGTH = 32768 - 2
s = Solver()
s.add(legal_n, UGT(n, MAX_TERM_LENGTH), fails)
# minimise by asking for the smallest witness
from z3 import Optimize

o = Optimize()
o.add(legal_n, UGT(n, MAX_TERM_LENGTH), fails)
h = o.minimize(BV2Int(n))
r = o.check()
wit = o.model()[n].as_long() if r == sat else None
results["Q4_immense_term_conversion_fails_in_aborting_region"] = {
    "result": str(r),
    "expect": "sat",
    "minimal_n": wit,
}
print(f"Q4_immense_term_conversion_fails_in_aborting_region: {r} minimal n = {wit}")
all_ok &= (r == sat and wit == 715_827_878)

# ---------------- Q5: the stored-field guard (LUCENE-7538) is sufficient ----------------
MAX_STORED = MAX_ARRAY_LENGTH // 3
s = Solver()
s.add(ULE(n, MAX_STORED), fails)
all_ok &= run("Q5_stored_guard_prevents_sizing_failure", s, "unsat")

results["constants"] = {
    "INT_MAX": INT_MAX,
    "MAX_ARRAY_LENGTH": MAX_ARRAY_LENGTH,
    "MAX_TERM_LENGTH": MAX_TERM_LENGTH,
    "MAX_STORED_STRING_LENGTH": MAX_STORED,
}
results["all_as_expected"] = bool(all_ok)
out = sys.argv[1] if len(sys.argv) > 1 else None
if out:
    with open(out, "w") as f:
        json.dump(results, f, indent=1)
print("ALL AS EXPECTED" if all_ok else "SOME QUERY UNEXPECTED")
sys.exit(0 if all_ok else 1)

"""Z3 model of the Lucene90 term-vectors CHUNK LAYOUT agreement between writer and reader:
which optional sections each side writes / expects, as a function of the per-field-instance flags,
term counts and term frequencies of one chunk.

Run:  python3 -I tv_chunk_layout.py

File:line mapping (Lucene main @ ea57360912ed)
------------------------------------------------------------------------------------------------
section            writer writes it iff                         reader reads it iff
AvgCharsPerTerm    any field instance has OFFSETS               TotalOffsets > 0, i.e. some OFFSETS
                   (Writer.java:627-646: hasOffsets |=           instance has sum(freq) > 0
                   fd.hasOffsets; return only if !hasOffsets)   (Reader.java:551-568 totals, :587-592)
StartOffsets/      one value per occurrence of an OFFSETS        TotalOffsets values (Reader.java:593-598)
Lengths blocks     instance (Writer.java:659-699)
Positions block    one value per occurrence of a POSITIONS       TotalPositions values (Reader.java:572)
                   instance (Writer.java:602-621)
PayloadLengths     one value per occurrence of a PAYLOADS        TotalPayloads > 0 (Reader.java:649)
                   instance (Writer.java:702-714)
numTerms >= 1 is guaranteed for the indexing chain (TermVectorsConsumerPerField.java:69-74 skips
fields without terms) but NOT for the merge / addIndexes copier: TermVectorsWriter.java:278-289 passes
terms.size() (or the counted terms) to startField without checking for 0, and :267-271 skips a field
whose Terms is null after startDocument(numFields) already counted it.

Obligations
  L1 AvgCharsPerTerm presence agrees, for every chunk the codec API accepts      (expected SAT:
     a counterexample exists - an OFFSETS instance with numTerms == 0)
  L2 same, under the indexing-chain precondition numTerms >= 1 for every instance  (expected UNSAT)
  L3 block value counts agree (positions, offsets, payload lengths)                (expected UNSAT,
     also without the precondition: both sides count occurrences)
Bounds: up to 4 field instances per chunk, numTerms and per-term frequencies unbounded integers
(freq >= 1 per term, so sumFreq_i >= numTerms_i and sumFreq_i == 0 <=> numTerms_i == 0).
"""
import sys
from z3 import Bool, Int, Solver, Or, And, Not, If, Sum, sat, unsat


def chunk(n):
    off = [Bool("off%d" % i) for i in range(n)]
    pos = [Bool("pos%d" % i) for i in range(n)]
    pay = [Bool("pay%d" % i) for i in range(n)]
    nt = [Int("numTerms%d" % i) for i in range(n)]
    sf = [Int("sumFreq%d" % i) for i in range(n)]
    dom = []
    for i in range(n):
        dom += [nt[i] >= 0, sf[i] >= nt[i], (sf[i] == 0) == (nt[i] == 0),
                Or(Not(pay[i]), pos[i])]  # payloads require positions (TermVectorsConsumerPerField:158-169)
    w_cpt = Or(off)
    total_offsets = Sum([If(off[i], sf[i], 0) for i in range(n)])
    r_cpt = total_offsets > 0
    # value counts per block: writer emits one value per occurrence of a flagged instance; reader
    # expects Total* values (same sums)
    w_pos = Sum([If(pos[i], sf[i], 0) for i in range(n)])
    r_pos = Sum([If(pos[i], sf[i], 0) for i in range(n)])
    w_pay = Sum([If(pay[i], sf[i], 0) for i in range(n)])
    r_pay = Sum([If(pay[i], sf[i], 0) for i in range(n)])
    return dom, w_cpt, r_cpt, (w_pos, r_pos, w_pay, r_pay, total_offsets), nt


def run(name, assertions, expect):
    s = Solver()
    s.add(*assertions)
    r = s.check()
    extra = ""
    if r == sat:
        m = s.model()
        extra = " model: " + ", ".join("%s=%s" % (d.name(), m[d]) for d in sorted(m.decls(), key=str))
    ok = r == expect
    print("%-60s %-6s expected %-6s %s%s" % (name, r, expect, "OK" if ok else "MISMATCH", extra))
    return ok


def main():
    ok = True
    for n in range(1, 5):
        dom, w, r, counts, nt = chunk(n)
        ok &= run("L1 cpt presence agrees (any accepted chunk), n=%d" % n, dom + [w != r], sat)
        ok &= run("L2 cpt presence agrees (numTerms>=1), n=%d" % n,
                  dom + [x >= 1 for x in nt] + [w != r], unsat)
        w_pos, r_pos, w_pay, r_pay, _ = counts
        ok &= run("L3 block counts agree, n=%d" % n, dom + [Or(w_pos != r_pos, w_pay != r_pay)], unsat)
    print("ALL AS EXPECTED" if ok else "SOME RESULT NOT AS EXPECTED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

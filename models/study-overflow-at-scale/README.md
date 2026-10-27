# Formal models and scripts: study-overflow-at-scale

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- BooleanScorer loops forever on the last 4096-doc window of a segment of about 2^31 documents, and a query timeout cannot stop it (bug LFV-048; [repro](../../repro/LFV-048/))
- Bulk BinaryDocValues#binaryValues (new in 10.6) sizes its buffer in int: it fails at 2 GiB and can return wrong bytes at 4 GiB (bug LFV-003; [repro](../../repro/LFV-003/), [bug page](../../bugs/LFV-003.html))
- SparseFixedBitSet.nextClearBit returns NO_MORE_DOCS in the last 4096-bit block (bug LFV-058; [repro](../../repro/LFV-058/))
- RoaringDocIdSet.Builder.add(min, max) throws on the last 2^16-doc block (bug LFV-066; [repro](../../repro/LFV-066/))
- LongHeap and TernaryLongHeap stop growing at 715,827,883 elements despite 'grows unbounded' (bug LFV-069; [repro](../../repro/LFV-069/))

## Files

- [z3/overflow_obligations.py](z3/overflow_obligations.py)
- [z3/smt2/A1.smt2](z3/smt2/A1.smt2)
- [z3/smt2/A1s.smt2](z3/smt2/A1s.smt2)
- [z3/smt2/A2.smt2](z3/smt2/A2.smt2)
- [z3/smt2/A3.smt2](z3/smt2/A3.smt2)
- [z3/smt2/A3s.smt2](z3/smt2/A3s.smt2)
- [z3/smt2/A4.smt2](z3/smt2/A4.smt2)
- [z3/smt2/A4b.smt2](z3/smt2/A4b.smt2)
- [z3/smt2/A5.smt2](z3/smt2/A5.smt2)
- [z3/smt2/A6.smt2](z3/smt2/A6.smt2)
- [z3/smt2/A6p.smt2](z3/smt2/A6p.smt2)
- [z3/smt2/A6s.smt2](z3/smt2/A6s.smt2)
- [z3/smt2/A7.smt2](z3/smt2/A7.smt2)
- [z3/smt2/A8.smt2](z3/smt2/A8.smt2)
- [z3/smt2/A9.smt2](z3/smt2/A9.smt2)
- [z3/smt2/A9b.smt2](z3/smt2/A9b.smt2)
- [z3/smt2/B1.smt2](z3/smt2/B1.smt2)
- [z3/smt2/B1w.smt2](z3/smt2/B1w.smt2)
- [z3/smt2/B2.smt2](z3/smt2/B2.smt2)
- [z3/smt2/B3.smt2](z3/smt2/B3.smt2)
- [z3/smt2/B3s.smt2](z3/smt2/B3s.smt2)
- [z3/smt2/B3t.smt2](z3/smt2/B3t.smt2)
- [z3/smt2/B4.smt2](z3/smt2/B4.smt2)
- [z3/smt2/B5.smt2](z3/smt2/B5.smt2)
- [z3/smt2/B6.smt2](z3/smt2/B6.smt2)
- [z3/smt2/C1.smt2](z3/smt2/C1.smt2)
- [z3/smt2/D1.smt2](z3/smt2/D1.smt2)
- [z3/smt2/D2.smt2](z3/smt2/D2.smt2)
- [z3/smt2/D3.smt2](z3/smt2/D3.smt2)
- [z3/smt2/D4.smt2](z3/smt2/D4.smt2)
- [z3/smt2/D5.smt2](z3/smt2/D5.smt2)
- [z3/smt2/E2.smt2](z3/smt2/E2.smt2)
- [z3/smt2/F1.smt2](z3/smt2/F1.smt2)
- [z3/smt2/F2.smt2](z3/smt2/F2.smt2)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

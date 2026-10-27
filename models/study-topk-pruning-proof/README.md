# Formal models and scripts: study-topk-pruning-proof

Tools: SMT-LIB (Z3/cvc5), Dafny, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MaxScoreBulkScorer can skip a document whose score equals the minimum competitive score (1-ulp rank inversion) (bug LFV-060; [repro](../../repro/LFV-060/), [bug page](../../bugs/LFV-060.html))

## Files

- [dafny/FpModel.dfy](dafny/FpModel.dfy)
- [dafny/Pruning.dfy](dafny/Pruning.dfy)
- [dafny/RequiredClauseUnsound.dfy](dafny/RequiredClauseUnsound.dfy)
- [dafny/Smoke.dfy](dafny/Smoke.dfy)
- [dafny/TopK.dfy](dafny/TopK.dfy)
- [smt/dump_o4.py](smt/dump_o4.py)
- [smt/fpcommon.py](smt/fpcommon.py)
- [smt/o1_sum_upper_bound.py](smt/o1_sum_upper_bound.py)
- [smt/o3_e2e.py](smt/o3_e2e.py)
- [smt/o3_nice.py](smt/o3_nice.py)
- [smt/o3_required_clause.py](smt/o3_required_clause.py)
- [smt/o4_monotone_sameorder.py](smt/o4_monotone_sameorder.py)
- [smt/o6_wand_scaled.py](smt/o6_wand_scaled.py)
- [smt/o8_dismax.py](smt/o8_dismax.py)
- [smt2/o4_double_n2.smt2](smt2/o4_double_n2.smt2)
- [smt2/o4_double_n3.smt2](smt2/o4_double_n3.smt2)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Dafny: `dafny verify <file>.dfy` (a failed obligation marks where the code's contract breaks).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

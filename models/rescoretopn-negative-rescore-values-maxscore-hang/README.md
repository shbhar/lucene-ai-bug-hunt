# Formal models and scripts: rescoretopn-negative-rescore-values-maxscore-hang

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- RescoreTopNQuery passes negative DoubleValuesSource values through as negative scores, so a top-k BooleanQuery that ORs it with another clause silently returns no hits (bug LFV-1015; [repro](../../repro/LFV-1015/))
- ScorerUtil.minRequiredScore (new in 10.3.0, #14827) never terminates when the remaining clauses' max score is negative at a minimum competitive score of 0, so with assertions disabled a top-k BooleanQuery with a negative-scoring SHOULD clause spins forever in MaxScoreBulkScorer, the infinite loop that LUCENE-10428 had turned into an exception (bug LFV-1016; [repro](../../repro/LFV-1016/))

## Files

- [minrequiredscore-unbounded-loop-negative-remaining-score/model/minrequiredscore_termination.py](minrequiredscore-unbounded-loop-negative-remaining-score/model/minrequiredscore_termination.py) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n1024_K3.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n2_K3.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q2_n3_K3.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e26.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e26.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e27.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e27.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e28.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e28.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e29.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e29.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e30.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e30.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e31.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e31.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e32.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n1024_e32.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e26.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e26.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e27.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e27.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e28.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e28.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e29.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e29.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e30.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e30.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e31.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e31.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e32.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q5_n2_e32.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n1024_K3.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n2_K3.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K1.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K1.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K2.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K2.smt2) (used for LFV-1016)
- [minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K3.smt2](minrequiredscore-unbounded-loop-negative-remaining-score/model/smt2/q6_n3_K3.smt2) (used for LFV-1016)
- [rescoretopn-negative-values-become-negative-scores/model/minrequiredscore_termination.py](rescoretopn-negative-values-become-negative-scores/model/minrequiredscore_termination.py) (used for LFV-1015)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

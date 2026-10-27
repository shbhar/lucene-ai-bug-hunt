# Formal models and scripts: distance-feature-pruning

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LatLonPoint.newDistanceFeatureQuery can skip a competitive document under top-k pruning: computeMaxDistance returns the lower end of its 1 m binary search, and the pruning box built from that radius adds only a 7 cm pad (bug LFV-379; [repro](../../repro/LFV-379/))

## Files

- [latlon-distance-feature-bisection-gap/model/decode_model.py](latlon-distance-feature-bisection-gap/model/decode_model.py) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/gen_smt.py](latlon-distance-feature-bisection-gap/model/gen_smt.py) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/p1_q2_no_goal.smt2](latlon-distance-feature-bisection-gap/model/p1_q2_no_goal.smt2) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/p2_q2_26_steps_insufficient.smt2](latlon-distance-feature-bisection-gap/model/p2_q2_26_steps_insufficient.smt2) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/p3_q2_24_steps_insufficient.smt2](latlon-distance-feature-bisection-gap/model/p3_q2_24_steps_insufficient.smt2) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/q1_postcondition.smt2](latlon-distance-feature-bisection-gap/model/q1_postcondition.smt2) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/q2_reach_2048.smt2](latlon-distance-feature-bisection-gap/model/q2_reach_2048.smt2) (used for LFV-379)
- [latlon-distance-feature-bisection-gap/model/q2_reach_full.smt2](latlon-distance-feature-bisection-gap/model/q2_reach_full.smt2) (used for LFV-379)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: indexsorted-leaf-delegate-points-work-after-mixed-leaf

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Since #16857 (upstream main and branch_11x, unreleased), a segment that its index sort terminates early can still build an unused points-based competitive iterator when an earlier segment of the same field-sorted search crossed the hits threshold without early termination: NumericComparator keeps hitsThresholdReached on the comparator that all leaves share, so IndexSortedLeafFieldComparator's not forwarding setHitsThresholdReached() no longer stops the delegate's BKD work (512 point doc IDs read in the repro, up to about cost/7 per segment, where 10.6.0 reads 0; performance regression, results correct) (finding LFV-1088; [repro](../../repro/LFV-1088/), [finding page](../../bugs/LFV-1088.html))

## Files

- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/IndexSortedLeafWork.tla](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/IndexSortedLeafWork.tla) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixA_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixA_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixA_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixA_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixB_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixB_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixB_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_fixB_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_main_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_main_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_main_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_main_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_p645_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_p645_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_p645_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_p645_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafA_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafA_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafA_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafA_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafB_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafB_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafB_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleafB_NoETWork.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleaf_NoBlindUpdate.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleaf_NoBlindUpdate.cfg) (used for LFV-1088)
- [indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleaf_NoETWork.cfg](indexsorted-leaf-delegate-points-work-from-comparator-wide-threshold/model/MC1_perleaf_NoETWork.cfg) (used for LFV-1088)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

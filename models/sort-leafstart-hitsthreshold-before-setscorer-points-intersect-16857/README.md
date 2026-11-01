# Formal models and scripts: sort-leafstart-hitsthreshold-before-setscorer-points-intersect-16857

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Since #16857 (upstream main and branch_11x, unreleased), a numeric field-sorted search reads a later segment's whole competitive points range before the scorer is set: TopFieldLeafCollector's constructor calls setHitsThresholdReached() while NumericComparator's iteratorCost is still its -1 'no scorer yet' value, so the 8x selectivity check never rejects (1,001 point doc IDs read where releases read 0; performance regression, results correct) (finding LFV-1057; [repro](../../repro/LFV-1057/), [finding page](../../bugs/LFV-1057.html))

## Files

- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_fixA.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_fixA.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_fixB.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_fixB.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_main.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_main.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_p645.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MC_p645.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_fixA.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_fixA.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_fixB.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_fixB.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_main.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_main.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_p645.cfg](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/MCvac_p645.cfg) (used for LFV-1057)
- [numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/SortLeafStart.tla](numeric-sort-leaf-start-intersects-points-before-scorer-cost/model/SortLeafStart.tla) (used for LFV-1057)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

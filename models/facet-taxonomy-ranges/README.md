# Formal models and scripts: facet-taxonomy-ranges

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Multi-valued range facet counts drop matches when a document's values do not arrive in ascending order (custom MultiLongValuesSource/MultiDoubleValuesSource, or raw-bit doubles with two or more negative values): LongRangeCounter.addMultiValued assumes sorted values (finding LFV-303; [repro](../../repro/LFV-303/), [finding page](../../bugs/LFV-303.html))
- Range facet counters add every hit the fastMatchQuery rejects to FacetResult.value, so the dimension total is too high and depends on the segment layout (finding LFV-151; [repro](../../repro/LFV-151/), [finding page](../../bugs/LFV-151.html))
- DoubleRangeFacetCounts orders -0.0 below +0.0 while DoubleRange.accept and getQuery treat the two zeros as equal: a document holding a zero of the opposite sign at an inclusive zero bound (for example -0.0 in [0, 1]) is matched by the range's own drill-down but not counted, and the accepted range [+0.0, -0.0] makes counting throw (finding LFV-310; [repro](../../repro/LFV-310/), [finding page](../../bugs/LFV-310.html))
- Facet DoubleRange, range-on-range DoubleRange and DimRange.fromDoubles/fromFloats do not exclude an exclusive bound that is an infinity: the empty ranges (+Inf, +Inf] and [-Inf, -Inf) are accepted and match the +Infinity / -Infinity documents instead of matching nothing (finding LFV-313; [repro](../../repro/LFV-313/), [finding page](../../bugs/LFV-313.html))

## Files

- [doublerange-exclusive-infinity-bound-not-excluded/model/range_counter_smt.py](doublerange-exclusive-infinity-bound-not-excluded/model/range_counter_smt.py) (used for LFV-313)
- [doublerange-signed-zero-counter-vs-accept/model/range_counter_smt.py](doublerange-signed-zero-counter-vs-accept/model/range_counter_smt.py) (used for LFV-310)
- [fastmatch-excluded-hits-counted-in-range-facet-value/model/range_counter_smt.py](fastmatch-excluded-hits-counted-in-range-facet-value/model/range_counter_smt.py) (used for LFV-151)
- [range-counter-assumes-ascending-multivalues/model/range_counter_smt.py](range-counter-assumes-ascending-multivalues/model/range_counter_smt.py) (used for LFV-303)
- [model/range_counter_smt.py](model/range_counter_smt.py) (used for LFV-303)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: overflow-at-scale-2

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LogMergePolicy.findMerges wraps 'start + mergeFactor' when mergeFactor is Integer.MAX_VALUE (or within about the segment count of it), which setMergeFactor accepts, so commit and flush throw once two size levels exist (every release since 2.3.0) (bug LFV-394; [repro](../../repro/LFV-394/), [bug page](../../bugs/LFV-394.html))
- Highlighter with the default QueryScorer returns no fragment for a matching PhraseQuery or MultiPhraseQuery that has a position hole (for example a removed stop word) and a slop within the number of holes of Integer.MAX_VALUE: WeightedSpanTermExtractor converts it to a SpanNearQuery with the plain int sum slop + positionGaps, which wraps to about Integer.MIN_VALUE, so the span query matches nothing (bug LFV-397; [repro](../../repro/LFV-397/), [bug page](../../bugs/LFV-397.html))
- FastVectorHighlighter with fragCharSize = Integer.MAX_VALUE (or any value above Integer.MAX_VALUE - spanStart) returns the whole field with only the first match highlighted: BaseFragListBuilder computes spanStart + fragCharSize in int, the sum wraps negative, and every later match is silently dropped (SimpleFragListBuilder, the default, and WeightedFragListBuilder) (bug LFV-401; [repro](../../repro/LFV-401/))
- PassageSelector.pickBest (MatchHighlighter) silently drops the highlight passages of permitted ranges that start after offset 0 when maxPassageWindow is Integer.MAX_VALUE: the int window shift 'to += range.from - from' wraps negative, so the second and later values of a multi-valued field lose their snippets (the residue of the LUCENE-10135 overflow fix) (bug LFV-387; [repro](../../repro/LFV-387/))
- Sequential DrillSideways never returns on a segment of more than 2^31 - 2048 documents: DrillSidewaysScorer's int chunk start wraps to Integer.MIN_VALUE, so its union and drill-down-advance loops never exit (released since 4.2.0) (bug LFV-383; [repro](../../repro/LFV-383/))

## Files

- [model/smt2/DS1.smt2](model/smt2/DS1.smt2) (used for LFV-383)
- [model/smt2/DS2.smt2](model/smt2/DS2.smt2) (used for LFV-383)
- [model/smt2/DS3.smt2](model/smt2/DS3.smt2) (used for LFV-383)
- [model/smt2/FV1.smt2](model/smt2/FV1.smt2) (used for LFV-401)
- [model/smt2/FV2.smt2](model/smt2/FV2.smt2) (used for LFV-401)
- [model/smt2/FV2b.smt2](model/smt2/FV2b.smt2) (used for LFV-401)
- [model/smt2/FV3.smt2](model/smt2/FV3.smt2) (used for LFV-401)
- [model/smt2/LM1.smt2](model/smt2/LM1.smt2) (used for LFV-394)
- [model/smt2/LM2.smt2](model/smt2/LM2.smt2) (used for LFV-394)
- [model/smt2/PS1.smt2](model/smt2/PS1.smt2) (used for LFV-387)
- [model/smt2/PS1s.smt2](model/smt2/PS1s.smt2) (used for LFV-387)
- [model/smt2/PS2.smt2](model/smt2/PS2.smt2) (used for LFV-387)
- [model/smt2/WS1.smt2](model/smt2/WS1.smt2) (used for LFV-397)
- [model/smt2/WS1s.smt2](model/smt2/WS1s.smt2) (used for LFV-397)
- [model/smt2/WS2.smt2](model/smt2/WS2.smt2) (used for LFV-397)
- [tools/triage.py](tools/triage.py) (used for LFV-383)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

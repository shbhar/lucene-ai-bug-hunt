# Formal models and scripts: span-containing-within-lockstep-misses-nested-little-spans

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SpanContainingQuery and SpanWithinQuery miss documents and spans when the little query's spans end out of order (for example a SpanOrQuery of a longer near clause and a shorter clause that can occur inside it): their single forward pass tests only the current little span (containing) or never goes back to a big span it skipped (within), so a later, shorter little span inside a big span is never tried (bug LFV-909; [repro](../../repro/LFV-909/), [bug page](../../bugs/LFV-909.html))

## Files

- [containspans-lockstep-assumes-monotone-little-ends/model/ContainSpans.tla](containspans-lockstep-assumes-monotone-little-ends/model/ContainSpans.tla) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugdoc_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugdoc_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugdoc_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugdoc_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugsafe_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugsafe_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugsafe_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_bugsafe_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_fix_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_fix_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_fix_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_fix_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_mut_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_mut_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_mut_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_containing_mut_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugdoc_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugdoc_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugdoc_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugdoc_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugsafe_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugsafe_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugsafe_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_bugsafe_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_fix_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_fix_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_fix_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_fix_N5.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_mut_N4.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_mut_N4.cfg) (used for LFV-909)
- [containspans-lockstep-assumes-monotone-little-ends/model/MC_within_mut_N5.cfg](containspans-lockstep-assumes-monotone-little-ends/model/MC_within_mut_N5.cfg) (used for LFV-909)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

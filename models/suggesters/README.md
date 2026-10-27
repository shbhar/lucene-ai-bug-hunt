# Formal models and scripts: suggesters

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- AnalyzingInfixSuggester.ensureOpen publishes its new IndexWriter before the SearcherManager opened on it and does not undo that when the manager cannot be opened, so refresh refreshes the manager over build's closed writer and throws AlreadyClosedException (bug LFV-263; [repro](../../repro/LFV-263/))

## Files

- [infix-ensureopen-publishes-writer-before-searchermanager/model/InfixNRT.tla](infix-ensureopen-publishes-writer-before-searchermanager/model/InfixNRT.tla) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc.cfg) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_fix.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_fix.cfg) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_noclose.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_noclose.cfg) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_noclose_fix.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_conc_noclose_fix.cfg) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_probe.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_probe.cfg) (used for LFV-263)
- [infix-ensureopen-publishes-writer-before-searchermanager/model/MC_seq.cfg](infix-ensureopen-publishes-writer-before-searchermanager/model/MC_seq.cfg) (used for LFV-263)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

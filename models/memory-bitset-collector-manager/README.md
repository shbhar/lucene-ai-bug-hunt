# Formal models and scripts: memory-bitset-collector-manager

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MemoryAccountingBitsetCollector sizes each collector's bitset to the doc-id span of its slice, so under concurrent search a slice whose segments are not adjacent in doc-id order allocates, and charges to the memory tracker, bits for the segments that other slices search (bug LFV-348; [repro](../../repro/LFV-348/))

## Files

- [model/MABC.tla](model/MABC.tla) (used for LFV-348)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

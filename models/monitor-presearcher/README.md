# Formal models and scripts: monitor-presearcher

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Monitor.register with the same query id twice in one call keeps both versions in the query index, so the query matches a mix of both versions, and after a reopen a persistent Monitor runs the older version while getQuery returns the newer one (bug LFV-332; [repro](../../repro/LFV-332/), [bug page](../../bugs/LFV-332.html))

## Files

- [register-batch-duplicate-id-keeps-stale-version/model/QueryIndexConc.tla](register-batch-duplicate-id-keeps-stale-version/model/QueryIndexConc.tla) (used for LFV-332)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

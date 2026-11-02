# Formal models and scripts: reader-open-leaf-sorter-failure-leaks

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A failed NRT reader open (DirectoryReader.open(IndexWriter), openIfChanged over the writer, or a SearcherManager/ReaderManager refresh) whose IndexWriterConfig leaf sorter throws never releases the IndexFileDeleter reference it took, so the still-open writer keeps every segment file of that point in time on disk for the rest of its life (finding LFV-1067; [repro](../../repro/LFV-1067/), [finding page](../../bugs/LFV-1067.html))

## Files

- [nrt-open-leaf-sorter-failure-keeps-deleter-references/model/NrtOpen.tla](nrt-open-leaf-sorter-failure-keeps-deleter-references/model/NrtOpen.tla) (used for LFV-1067)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

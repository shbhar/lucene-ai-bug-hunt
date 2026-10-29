# Formal models and scripts: codec-format-javadoc-grammar-overflow-liv-cfe-extension-table

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- The default codec's 'Summary of File Extensions' table (codecs/lucene104 package documentation) is stale: it omits.fdm,.tvm,.tmd,.psm,.dvs,.dvp and.vemf, which the default codec writes, credits.veq to the default HNSW vectors format (which does not write it) and gives 'vex' without its dot; Lucene104PostingsFormat's 'Files and detailed format' list also omits.tmd and.psm (documentation defect; the code is correct) (bug LFV-972; [repro](../../repro/LFV-972/))
- The file-format javadoc of the default live-docs format (Lucene90LiveDocsFormat) gives the.liv grammar as 'IndexHeader,Generation,Bits', but no separate Generation has been written since LUCENE-5969 (the deletes generation is the IndexHeader suffix), the codec footer the writer appends is missing, and the type list defines SegmentHeader instead of IndexHeader (documentation defect; the writer and reader agree) (bug LFV-970; [repro](../../repro/LFV-970/))
- The file-format javadoc of the default doc-values format (Lucene90DocValuesFormat) says terms dictionaries are 'written in chunks of 16, with the first value written completely', but since 9.0 they are written in blocks of 64 terms whose other 63 terms are prefix-coded and then LZ4-compressed; it also says table compression is used for fewer than 256 unique values, while exactly 256 are table-compressed (documentation defect; the writer and reader agree) (bug LFV-974; [repro](../../repro/LFV-974/))
- The file-format javadoc of the default compound format (Lucene90CompoundFormat) gives an incomplete.cfe entry-table grammar: it leaves out the codec footer the writer appends (the.cfs line beside it lists one), and it says each entry holds the file's name although the writer stores the name with the segment name stripped (documentation defect; the writer and reader agree) (bug LFV-971; [repro](../../repro/LFV-971/))

## Files

- [lucene104-package-info-extension-table-incomplete/model/mkfix.py](lucene104-package-info-extension-table-incomplete/model/mkfix.py) (used for LFV-972)
- [lucene90-compound-entries-javadoc-missing-footer/model/mkfix.py](lucene90-compound-entries-javadoc-missing-footer/model/mkfix.py) (used for LFV-971)
- [lucene90-docvalues-javadoc-terms-block-size-stale/model/mkfix.py](lucene90-docvalues-javadoc-terms-block-size-stale/model/mkfix.py) (used for LFV-974)
- [lucene90-livedocs-javadoc-grammar-stale/model/mkfix.py](lucene90-livedocs-javadoc-grammar-stale/model/mkfix.py) (used for LFV-970)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: bitsets-livedocs-acceptdocs

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ReqExclBulkScorer.WindowBits.applyMask adds offset + bitSet.length in int, so FILTER/MUST + MUST_NOT queries silently drop every match in a 4096-doc window based at doc 2^31-4096 or above (up to the last 3,967 doc IDs of a segment near IndexWriter.MAX_DOCS) (bug LFV-139; [repro](../../repro/LFV-139/), [bug page](../../bugs/LFV-139.html))
- Lucene90LiveDocsFormat.writeLiveDocs and CheckIndex.bitsCardinality step an int offset by 1024 that wraps to Integer.MIN_VALUE for segments of more than 2,147,482,624 documents, so a deletion in such a segment can never be committed (10.4.0+) and CheckIndex at level 2 or higher reports its valid live docs as an error (10.3.0+) (bug LFV-147; [repro](../../repro/LFV-147/))
- FixedBitSet.ensureCapacity computes the grown length as int arr.length &lt;&lt; 6, which wraps negative once the requested bit reaches about 1.91 billion, so DocsWithFieldSet iterates no documents and a flushed segment silently drops every value of its sparse doc-values and norms fields (bug LFV-137; [repro](../../repro/LFV-137/))
- (Latent) FixedBitSet.approximateCardinality returns a negative value for bit sets of 2^31-63 bits or more, because its scale factor 64 * numWords overflows int (bug LFV-133; [repro](../../repro/LFV-133/))

## Files

- [fixedbitset-approximatecardinality-overflow/model/overflow_findings.py](fixedbitset-approximatecardinality-overflow/model/overflow_findings.py) (used for LFV-133)
- [fixedbitset-ensurecapacity-numbits-overflow/model/overflow_findings.py](fixedbitset-ensurecapacity-numbits-overflow/model/overflow_findings.py) (used for LFV-137)
- [livedocs-batch-offset-overflow/model/overflow_findings.py](livedocs-batch-offset-overflow/model/overflow_findings.py) (used for LFV-147)
- [reqexcl-windowbits-applymask-offset-overflow/model/overflow_findings.py](reqexcl-windowbits-applymask-offset-overflow/model/overflow_findings.py) (used for LFV-139)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

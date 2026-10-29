# Formal models and scripts: javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- The org.apache.lucene.analysis package overview (the TokenStream API tutorial) has seven code examples that do not compile on any release since 6.0: they call StandardAnalyzer(Version), WhitespaceTokenizer(Version) and FilteringTokenFilter(Version, TokenStream), removed in 5.0, and LengthFilter(boolean, TokenStream, int, int), gone since 4.4, subclass the final StandardAnalyzer, and define a custom AttributeImpl without reflectWith, abstract since 6.0 (documentation defect) (bug LFV-989; [repro](../../repro/LFV-989/))
- analysis-icu overview page: the ICUTokenizer example and the three collation examples (Farsi range, Danish sort, Turkish case) call APIs removed between Lucene 4.0 and 10.0, and on main the Serbian transliteration example is not valid Java (documentation defect) (bug LFV-992; [repro](../../repro/LFV-992/))
- PorterStemFilter's and StempelFilter's class javadocs still tell readers to use LowerCaseTokenizer, which was removed in 8.0.0 (LUCENE-8498), and PorterStemFilter's MyAnalyzer example calls new LowerCaseTokenizer(version, reader), a constructor form gone since 5.0.0, so the example does not compile; the documented replacement is LetterTokenizer followed by LowerCaseFilter (documentation defect) (bug LFV-987; [repro](../../repro/LFV-987/))
- BPIndexReorderer's class javadoc 'Typical usage' example calls reorderer.setForkJoinPool(pool), removed in 10.0.0, and the 2-argument reorder(reader, targetDir), removed in 9.12.0, so it names APIs that no longer exist; #13289 moved the class to an Executor argument, reorder(CodecReader, Directory, Executor), and left the example unchanged (documentation defect) (bug LFV-984; [repro](../../repro/LFV-984/))
- FSTCompiler.compile's javadoc shows how to read back an FST written to a non-FSTReader DataOutput with 'fst = new FST&lt;&gt;(fstMetadata, dataInput, new OffHeapFSTStore)', but #13524 removed both the no-argument OffHeapFSTStore constructor and the 3-argument FST constructor in 9.12.0, so the example names APIs that no longer exist (documentation defect) (bug LFV-988; [repro](../../repro/LFV-988/))
- TermInSetQuery's class javadoc example does not compile: it calls the varargs constructor TermInSetQuery(String, BytesRef...), removed in 10.0 (#12837), and new BooleanQuery / bq.add(...), removed in 6.0 (LUCENE-6570); the equal-scores claim it illustrates still holds (documentation defect) (bug LFV-980; [repro](../../repro/LFV-980/))

## Files

- [model/extract_blocks.py](model/extract_blocks.py) (used for LFV-987, LFV-989, LFV-992)
- [model/gen_generic.py](model/gen_generic.py) (used for LFV-980, LFV-984, LFV-988, LFV-989)
- [model/gen_tests.py](model/gen_tests.py) (used for LFV-987, LFV-992)
- [model/sweep.py](model/sweep.py) (used for LFV-980, LFV-984, LFV-988, LFV-989)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: slow-codec-reader-vector-metadata-answered-past-wrappers

Tools: Alloy.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SlowCodecReaderWrapper's KnnVectorsReader.getVectorCount (new in 10.6.0) returns the vector count of the SegmentReader under every FilterLeafReader/FilterCodecReader layer instead of the wrapped view's, so it disagrees with get*VectorValues(field).size of the same reader (6 instead of 3 for a view that exposes 3 of 6 vectors), and it throws NullPointerException for a vector field that the view adds (bug LFV-911; [repro](../../repro/LFV-911/))

## Files

- [slowcodec-vector-count-answered-from-unwrapped-segment/model/ReaderViews.als](slowcodec-vector-count-answered-from-unwrapped-segment/model/ReaderViews.als) (used for LFV-911)
- [slowcodec-vector-count-answered-from-unwrapped-segment/model/ReaderViews_mutant_nopreserve.als](slowcodec-vector-count-answered-from-unwrapped-segment/model/ReaderViews_mutant_nopreserve.als) (used for LFV-911)

## How to run

- Alloy 6: open the `.als` file and run its commands (an instance found for a bug assertion is the bug).

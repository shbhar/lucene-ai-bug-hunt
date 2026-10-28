-- slow-codec-reader-vector-metadata-answered-past-wrappers: reader-view algebra of SlowCodecReaderWrapper's KnnVectorsReader metadata accessors.
-- Lucene main a9779ef3c193. Model element -> Java (lucene/core/src/java/org/apache/lucene/...):
--   Seg            -> index/SegmentReader
--   Other          -> any LeafReader that is neither a FilterLeafReader nor a CodecReader:
--                     index/ParallelLeafReader, memory/MemoryIndex leaf, index/TermVectorLeafReader
--   FLeaf          -> index/FilterLeafReader subclass; FilterLeafReader.unwrap follows .in
--   FCodec         -> index/FilterCodecReader subclass (incl. index/SortingCodecReader); FilterCodecReader.unwrap
--   Filter.preserving -> the layer's get*VectorValues(f).size() equals its inner's (identity, Exitable, soft
--                     deletes, Mismatched, Asserting, Sorting); not preserving = a view that projects/adds/drops
--                     vectors ("possibly transforming the data along the way", FilterLeafReader class javadoc)
--   size           -> r.get*VectorValues(f).size(); -1 = r exposes no vectors for f
--   wrapsSlow      -> index/SlowCodecReaderWrapper.java:52-58 (a CodecReader is returned as is; otherwise the
--                     anonymous slow CodecReader whose vector reader is readerToVectorReader(reader), :166)
--   viewSize       -> SlowCodecReaderWrapper.java:169-187 forwards get*VectorValues to the wrapped reader
--   unwrapSeg      -> SlowCodecReaderWrapper.java:242-251 unwrapSegmentReader
--   slowCount      -> SlowCodecReaderWrapper.java:224-232 getVectorCount (segment fast path, else
--                     KnnVectorsReader.getVectorCount default = get*VectorValues(f).size(), KnnVectorsReader.java:219-228)
--   offHeapThrows  -> SlowCodecReaderWrapper.java:216-221 + :234-240 (segmentReader throws AssertionError)
--   contract       -> KnnVectorsReader.java:203-204 "same meaning as get*VectorValues(field).size()",
--                     :217 IllegalArgumentException if the field is not vector-indexed
--   ERR            -> any exception (IAE / NPE) from the segment fast path when the segment lacks f
--                     (PerFieldKnnVectorsFormat.java:267-270, :378-384; SegmentReader.getVectorReader() null)

open util/boolean

abstract sig Reader { size: Int }
sig Seg extends Reader {}
sig Other extends Reader {}
abstract sig Filter extends Reader { inner: one Reader, preserving: one Bool }
sig FLeaf extends Filter {}
sig FCodec extends Filter {}

fact wellFormed {
  no r: Reader | r in r.^inner                         -- views are finite chains
  all f: FCodec | f.inner in Seg + FCodec              -- FilterCodecReader(CodecReader in)
  all r: Reader | r.size >= -1
  -- MUTANT: preserving layers no longer constrained
}

pred isCodec[r: Reader] { r in Seg + FCodec }
fun unwrapSeg[r: Reader]: set Seg { r.*inner & Seg }  -- lone by the chain structure

-- outcome of SlowCodecReaderWrapper.wrap(r).getVectorReader().getVectorCount(fi(f)) for a non-codec r:
-- an Int, or -2 standing for "throws" (IAE/NPE)
fun slowCount[r: Reader]: Int {
  some unwrapSeg[r]
    implies (unwrapSeg[r].size >= 0 implies unwrapSeg[r].size else -2)   -- segment fast path
    else (r.size >= 0 implies r.size else -2)                             -- default: open the view's values
}
-- the documented answer: the view's size, or IAE (-2) when the view has no vectors for f
fun documented[r: Reader]: Int { r.size >= 0 implies r.size else -2 }
pred offHeapThrows[r: Reader] { no unwrapSeg[r] }
-- candidate fix: always answer from the wrapped view (KnnVectorsReader default)
fun fixedCount[r: Reader]: Int { r.size >= 0 implies r.size else -2 }

-- A1 (expected: counterexample) the contract holds for every slow view
assert CountMatchesView { all r: Reader | not isCodec[r] implies slowCount[r] = documented[r] }
check CountMatchesView for 5 but 4 Int

-- A2 (expected: valid) characterisation: the count is wrong exactly when the chain ends at a SegmentReader
-- whose stored count differs from the view's size (some non-preserving layer on the chain)
assert WrongIffTransformedSegment {
  all r: Reader | not isCodec[r] implies
    (slowCount[r] != documented[r] iff (some unwrapSeg[r] and unwrapSeg[r].size != r.size))
}
check WrongIffTransformedSegment for 6 but 4 Int

-- A3 (expected: valid) all-preserving chains are correct (codecreader-wrappers's identity-FilterLeafReader sweep, 0 mismatches)
assert PreservingChainsCorrect {
  all r: Reader | (not isCodec[r] and (all f: r.*inner & Filter | f.preserving = True))
    implies slowCount[r] = documented[r]
}
check PreservingChainsCorrect for 6 but 4 Int

-- A4 (expected: valid) a non-segment chain end (ParallelLeafReader, MemoryIndex, ...) gets the right count
-- but getOffHeapByteSize throws
assert NonSegmentEnd {
  all r: Reader | (not isCodec[r] and no unwrapSeg[r]) implies (slowCount[r] = documented[r] and offHeapThrows[r])
}
check NonSegmentEnd for 6 but 4 Int

-- A5 (expected: valid) the one-line fix satisfies the contract
assert FixMatchesView { all r: Reader | not isCodec[r] implies fixedCount[r] = documented[r] }
check FixMatchesView for 6 but 4 Int

-- vacuity probes (expected: instances)
run SubsetViewOverSegment { some r: FLeaf, s: Seg | r.inner = s and r.preserving = False and r.size < s.size and r.size >= 0 } for 3 but 4 Int
run AddedFieldOverSegment { some r: FLeaf, s: Seg | r.inner = s and s.size = -1 and r.size > 0 } for 3 but 4 Int
run DeepChain { some r: FLeaf | not isCodec[r] and #(r.^inner) >= 3 and some (r.^inner & FCodec) } for 5 but 4 Int

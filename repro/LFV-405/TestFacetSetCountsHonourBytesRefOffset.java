/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.facet.verifyv1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.facetset.ExactFacetSetMatcher;
import org.apache.lucene.facet.facetset.FacetSet;
import org.apache.lucene.facet.facetset.FacetSetDecoder;
import org.apache.lucene.facet.facetset.FacetSetMatcher;
import org.apache.lucene.facet.facetset.FacetSetsField;
import org.apache.lucene.facet.facetset.IntFacetSet;
import org.apache.lucene.facet.facetset.LongFacetSet;
import org.apache.lucene.facet.facetset.MatchingFacetSetsCounts;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * MatchingFacetSetsCounts must count a facet-sets binary doc value by the bytes of the BytesRef
 * slice [offset, offset + length) that the reader returns, not by bytes[0..length).
 *
 * <p>Public API only (the class lives outside the facetset package on purpose). The non-zero
 * offset reaches MatchingFacetSetsCounts through the stock MemoryIndex, which hands back the
 * field's BytesRef as given; the field's value is set with the expert field-reuse method
 * Field#setBytesValue(BytesRef), documented as not copying the BytesRef.
 */
public class TestFacetSetCountsHonourBytesRefOffset extends LuceneTestCase {

  private static final String FIELD = "readings";

  /** The encoding FacetSetsField itself produces for the given sets (offset 0). */
  private static BytesRef encode(FacetSet... sets) {
    BytesRef enc = FacetSetsField.create(FIELD, sets).binaryValue();
    assertEquals(0, enc.offset);
    return BytesRef.deepCopyOf(enc);
  }

  /** Returns a slice of a fresh array: prefix bytes, then value, then suffix bytes. */
  private static BytesRef embed(byte[] prefix, BytesRef value, byte[] suffix) {
    byte[] buf = new byte[prefix.length + value.length + suffix.length];
    System.arraycopy(prefix, 0, buf, 0, prefix.length);
    System.arraycopy(value.bytes, value.offset, buf, prefix.length, value.length);
    System.arraycopy(suffix, 0, buf, prefix.length + value.length, suffix.length);
    return new BytesRef(buf, prefix.length, value.length);
  }

  /** A FacetSetsField whose value is reset (not copied) to the given BytesRef. */
  private static FacetSetsField fieldWithValue(FacetSet typeWitness, BytesRef value) {
    FacetSetsField field = FacetSetsField.create(FIELD, typeWitness);
    field.setBytesValue(value);
    assertSame(value, field.binaryValue());
    return field;
  }

  /** label -> count for every matcher, plus "#docs" -> the total doc count. */
  private static String countsOf(FacetResult result, FacetSetMatcher... matchers) {
    TreeMap<String, Integer> m = new TreeMap<>();
    for (FacetSetMatcher matcher : matchers) {
      m.put(matcher.label, 0);
    }
    for (LabelAndValue lv : result.labelValues) {
      m.put(lv.label, lv.value.intValue());
    }
    m.put("#docs", result.value.intValue());
    return m.toString();
  }

  private static String countOverMemoryIndex(
      FacetSetsField field, BytesRef expectedReaderValue, FacetSetDecoder decoder, FacetSetMatcher... matchers)
      throws IOException {
    Document doc = new Document();
    doc.add(field);
    MemoryIndex mi = MemoryIndex.fromDocument(doc, null);
    IndexSearcher searcher = mi.createSearcher();
    // Precondition: the stock reader legally returns the value as a slice (same content).
    LeafReader leaf = searcher.getIndexReader().leaves().get(0).reader();
    BinaryDocValues dv = leaf.getBinaryDocValues(FIELD);
    assertTrue(dv.advanceExact(0));
    BytesRef got = dv.binaryValue();
    assertEquals(expectedReaderValue, got);
    assertEquals(expectedReaderValue.offset, got.offset);

    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    FacetResult r = new MatchingFacetSetsCounts(FIELD, fc, decoder, matchers).getAllChildren(FIELD);
    return countsOf(r, matchers);
  }

  private String countOverIndexWriter(
      FacetSetsField field, FacetSetDecoder decoder, FacetSetMatcher... matchers) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      doc.add(field);
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(r);
        FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        FacetResult res = new MatchingFacetSetsCounts(FIELD, fc, decoder, matchers).getAllChildren(FIELD);
        return countsOf(res, matchers);
      }
    }
  }

  /**
   * One document with two long facet sets (5,6) and (7,8). The value is a slice of a buffer whose
   * first 20 bytes are the encoding of a different 2-dim set (100,200). Same content compact
   * (offset 0) is counted correctly over the same MemoryIndex; the slice is not.
   */
  public void testLongSetsSliceVsCompactOverMemoryIndex() throws IOException {
    BytesRef own = encode(new LongFacetSet(5, 6), new LongFacetSet(7, 8));
    BytesRef other = encode(new LongFacetSet(100, 200));
    BytesRef slice = embed(BytesRef.deepCopyOf(other).bytes, own, new byte[] {9, 9, 9});
    assertEquals(own, slice); // identical content, only the offset differs
    assertEquals(20, slice.offset);

    FacetSetMatcher[] matchers = {
      new ExactFacetSetMatcher("(5,6)", new LongFacetSet(5, 6)),
      new ExactFacetSetMatcher("(7,8)", new LongFacetSet(7, 8)),
      new ExactFacetSetMatcher("(100,200)", new LongFacetSet(100, 200)),
    };
    String expected = "{#docs=1, (100,200)=0, (5,6)=1, (7,8)=1}";

    String compact =
        countOverMemoryIndex(
            fieldWithValue(new LongFacetSet(0, 0), BytesRef.deepCopyOf(slice)),
            own,
            FacetSetDecoder::decodeLongs,
            matchers);
    assertEquals("control: compact copy over MemoryIndex", expected, compact);

    String sliced =
        countOverMemoryIndex(
            fieldWithValue(new LongFacetSet(0, 0), slice),
            slice,
            FacetSetDecoder::decodeLongs,
            matchers);
    assertEquals("slice with offset=" + slice.offset + " over MemoryIndex", expected, sliced);
  }

  /** Same idea with int facet sets and FacetSetDecoder::decodeInts, offset 12. */
  public void testIntSetsSliceOverMemoryIndex() throws IOException {
    BytesRef own = encode(new IntFacetSet(1, 2, 3));
    BytesRef other = encode(new IntFacetSet(-1, -2, -3)); // 16 bytes; use its first 12
    byte[] prefix = new byte[12];
    System.arraycopy(other.bytes, 0, prefix, 0, 12);
    BytesRef slice = embed(prefix, own, new byte[0]);
    assertEquals(own, slice);

    FacetSetMatcher[] matchers = {
      new ExactFacetSetMatcher("(1,2,3)", new IntFacetSet(1, 2, 3)),
      new ExactFacetSetMatcher("(-1,-2,-3)", new IntFacetSet(-1, -2, -3)),
    };
    String expected = "{#docs=1, (-1,-2,-3)=0, (1,2,3)=1}";
    String sliced =
        countOverMemoryIndex(
            fieldWithValue(new IntFacetSet(0, 0, 0), slice),
            slice,
            FacetSetDecoder::decodeInts,
            matchers);
    assertEquals("int slice with offset=" + slice.offset + " over MemoryIndex", expected, sliced);
  }

  /** Value at offset 8 of a zero-filled prefix: must count (5,6), not throw. */
  public void testZeroPrefixSliceOverMemoryIndexDoesNotThrow() throws IOException {
    BytesRef own = encode(new LongFacetSet(5, 6));
    BytesRef slice = embed(new byte[8], own, new byte[0]);
    FacetSetMatcher[] matchers = {new ExactFacetSetMatcher("(5,6)", new LongFacetSet(5, 6))};
    String sliced;
    try {
      sliced =
          countOverMemoryIndex(
              fieldWithValue(new LongFacetSet(0, 0), slice),
              slice,
              FacetSetDecoder::decodeLongs,
              matchers);
    } catch (RuntimeException e) {
      throw new AssertionError("counting a legal slice threw " + e, e);
    }
    assertEquals("{#docs=1, (5,6)=1}", sliced);
  }

  /** Control: the identical slice through IndexWriter (which copies the slice) is counted right. */
  public void testControlSameSliceThroughIndexWriter() throws IOException {
    BytesRef own = encode(new LongFacetSet(5, 6), new LongFacetSet(7, 8));
    BytesRef slice = embed(encode(new LongFacetSet(100, 200)).bytes, own, new byte[] {9});
    FacetSetMatcher[] matchers = {
      new ExactFacetSetMatcher("(5,6)", new LongFacetSet(5, 6)),
      new ExactFacetSetMatcher("(7,8)", new LongFacetSet(7, 8)),
      new ExactFacetSetMatcher("(100,200)", new LongFacetSet(100, 200)),
    };
    assertEquals(
        "{#docs=1, (100,200)=0, (5,6)=1, (7,8)=1}",
        countOverIndexWriter(
            fieldWithValue(new LongFacetSet(0, 0), slice), FacetSetDecoder::decodeLongs, matchers));
  }

  /**
   * Metamorphic sweep: for every offset 1..24 with a prefix made of copies of a valid encoding of
   * a different set, slice and compact copy must give the same counts over MemoryIndex. Collects
   * all mismatches and reports them together.
   */
  public void testSweepOffsetsSliceEqualsCompact() throws IOException {
    BytesRef own = encode(new LongFacetSet(5, 6));
    BytesRef other = encode(new LongFacetSet(100, 200));
    FacetSetMatcher[] matchers = {
      new ExactFacetSetMatcher("(5,6)", new LongFacetSet(5, 6)),
      new ExactFacetSetMatcher("(100,200)", new LongFacetSet(100, 200)),
    };
    List<String> mismatches = new ArrayList<>();
    int checked = 0;
    for (int off = 1; off <= 24; off++) {
      // prefix = the 'other' encoding repeated, truncated to 'off' bytes; this keeps bytes[0..4)
      // a plausible numDims (2) so the failure is a wrong count rather than an exception
      byte[] prefix = new byte[off];
      for (int i = 0; i < off; i++) {
        prefix[i] = other.bytes[i % other.length];
      }
      BytesRef slice = embed(prefix, own, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
      String compact =
          countOverMemoryIndex(
              fieldWithValue(new LongFacetSet(0, 0), BytesRef.deepCopyOf(slice)),
              own,
              FacetSetDecoder::decodeLongs,
              matchers);
      String sliced;
      try {
        sliced =
            countOverMemoryIndex(
                fieldWithValue(new LongFacetSet(0, 0), slice),
                slice,
                FacetSetDecoder::decodeLongs,
                matchers);
      } catch (RuntimeException | AssertionError e) {
        sliced = "threw " + e;
      }
      checked++;
      if (compact.equals(sliced) == false) {
        mismatches.add("offset=" + off + ": compact " + compact + " vs slice " + sliced);
      }
    }
    assertTrue(
        mismatches.size() + "/" + checked + " offsets mismatch: " + String.join("; ", mismatches),
        mismatches.isEmpty());
  }
}

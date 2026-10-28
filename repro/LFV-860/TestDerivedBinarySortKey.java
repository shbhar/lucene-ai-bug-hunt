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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * Independent verifier repro for the BinarySortField trigger. BinarySortField's javadoc allows a
 * subclass to derive the sort key "from the stored bytes and/or other doc-values fields of the
 * segment". The index is sorted by (derived key, f); a range query on f (skip-indexed numeric doc
 * values) is compared against the same range on g, a copy of f without a skip index, and against
 * a brute-force scan.
 *
 * <p>Needs META-INF/services/org.apache.lucene.index.SortFieldProvider (listing the two Provider
 * classes below) on the classpath.
 */
public class TestDerivedBinarySortKey extends LuceneTestCase {

  // (src, f) per document. The derived key orders by src, so f is not monotone in doc-ID order.
  private static final long[][] DOCS = {
    {1, 2}, {0, 6}, {1, 4}, {0, 1}, {0, 3}, {1, 5}, {2, 0}, {2, 7}
  };

  /** Key = sortable bytes of the numeric doc-values field "src"; the sort's own field is absent. */
  public static final class SrcKeySortField extends BinarySortField {
    static final String NAME = "VOneSrcKey";

    public SrcKeySortField(String field) {
      super(field, false, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      return new KeyValues(DocValues.getNumeric(reader, "src"), null);
    }

    /** SPI provider. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new SrcKeySortField(in.readString());
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
      }
    }
  }

  /**
   * Key = (value of the named skip-indexed field, value of the companion field "src"), so the
   * order is by the named field first and by src within equal values.
   */
  public static final class PairKeySortField extends BinarySortField {
    static final String NAME = "VOnePairKey";

    public PairKeySortField(String field) {
      super(field, false, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      return new KeyValues(
          DocValues.getNumeric(reader, getField()), DocValues.getNumeric(reader, "src"));
    }

    /** SPI provider. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new PairKeySortField(in.readString());
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
      }
    }
  }

  /** Binary view over one or two dense numeric fields; forward iteration with nextDoc only. */
  private static final class KeyValues extends BinaryDocValues {
    private final NumericDocValues first;
    private final NumericDocValues second;
    private final BytesRef key;

    KeyValues(NumericDocValues first, NumericDocValues second) {
      this.first = first;
      this.second = second;
      this.key = new BytesRef(new byte[second == null ? Long.BYTES : 2 * Long.BYTES]);
    }

    @Override
    public BytesRef binaryValue() throws IOException {
      NumericUtils.longToSortableBytes(first.longValue(), key.bytes, 0);
      if (second != null) {
        int doc = first.docID();
        while (second.docID() < doc) {
          second.nextDoc();
        }
        if (second.docID() != doc) {
          throw new IllegalStateException("src missing for doc " + doc);
        }
        NumericUtils.longToSortableBytes(second.longValue(), key.bytes, Long.BYTES);
      }
      return key;
    }

    @Override
    public int docID() {
      return first.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return first.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      int doc = first.docID();
      while (doc < target) {
        doc = first.nextDoc();
      }
      return doc;
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      return advance(target) == target;
    }

    @Override
    public long cost() {
      return first.cost();
    }
  }

  private Directory dir;
  private DirectoryReader reader;

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    super.tearDown();
  }

  private interface Extra {
    void add(Document doc);
  }

  private LeafReader buildIndex(Sort indexSort, Extra extra) throws IOException {
    dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setIndexSort(indexSort);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (long[] d : DOCS) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("src", d[0]));
        doc.add(NumericDocValuesField.indexedField("f", d[1]));
        doc.add(new NumericDocValuesField("g", d[1])); // same values, no skip index
        extra.add(doc);
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    LeafReader leaf = reader.leaves().get(0).reader();
    assertEquals(indexSort, leaf.getMetaData().sort());
    return leaf;
  }

  private static long[] values(LeafReader leaf, String field) throws IOException {
    long[] out = new long[leaf.maxDoc()];
    NumericDocValues v = DocValues.getNumeric(leaf, field);
    for (int doc = 0; doc < leaf.maxDoc(); doc++) {
      assertTrue(v.advanceExact(doc));
      out[doc] = v.longValue();
    }
    return out;
  }

  private static Set<Integer> hits(IndexSearcher s, Query q, int maxDoc) throws IOException {
    Set<Integer> got = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, maxDoc + 1).scoreDocs) {
      got.add(sd.doc);
    }
    return got;
  }

  private void runScenario(String name, LeafReader leaf) throws IOException {
    long[] f = values(leaf, "f");
    long[] src = values(leaf, "src");
    System.out.println(
        name + ": src in doc order " + Arrays.toString(src) + ", f in doc order "
            + Arrays.toString(f) + "; getPrimarySortField=" + primary(leaf));
    // The derived key really drives the order: src is non-decreasing in doc-ID order.
    for (int i = 1; i < src.length; i++) {
      assertTrue("index not sorted by the derived key", src[i - 1] <= src[i]);
    }
    IndexSearcher s = new IndexSearcher(leaf);
    s.setQueryCache(null);
    List<String> bad = new ArrayList<>();
    int total = 0;
    for (long lo = -1; lo <= 8; lo++) {
      for (long hi = lo; hi <= 8; hi++) {
        total++;
        Set<Integer> expected = new TreeSet<>();
        for (int doc = 0; doc < f.length; doc++) {
          if (f[doc] >= lo && f[doc] <= hi) {
            expected.add(doc);
          }
        }
        // control: same values without a skip index never take the index-sort shortcut
        Set<Integer> gHits = hits(s, NumericDocValuesField.newSlowRangeQuery("g", lo, hi), f.length);
        assertEquals("control g:[" + lo + " TO " + hi + "]", expected, gHits);
        Query q = NumericDocValuesField.newSlowRangeQuery("f", lo, hi);
        Set<Integer> fHits = hits(s, q, f.length);
        int count = s.count(q);
        if (fHits.equals(expected) == false || count != expected.size()) {
          bad.add(
              "f:[" + lo + " TO " + hi + "] expected " + expected + " got " + fHits + " count="
                  + count);
        }
      }
    }
    System.out.println(name + ": mismatches " + bad.size() + "/" + total);
    for (int i = 0; i < Math.min(5, bad.size()); i++) {
      System.out.println("  " + bad.get(i));
    }
    assertTrue(
        name + ": " + bad.size() + "/" + total + " ranges on f wrong; first: "
            + bad.subList(0, Math.min(3, bad.size())),
        bad.isEmpty());
  }

  /** Diagnostic only (not used by assertions); Sort.getPrimarySortField exists from 10.6 on. */
  private static String primary(LeafReader r) {
    try {
      return String.valueOf(
          Sort.class.getMethod("getPrimarySortField", LeafReader.class).invoke(null, r));
    } catch (NoSuchMethodException e) {
      return "n/a (method absent in this version)";
    } catch (ReflectiveOperationException e) {
      return "error " + e;
    }
  }

  /** Sort-field name "key" has no field of its own; the key is derived from "src". */
  public void testKeyDerivedFromOtherField() throws IOException {
    Sort sort = new Sort(new SrcKeySortField("key"), new SortField("f", SortField.Type.LONG));
    runScenario("src-key", buildIndex(sort, doc -> {}));
  }

  /** Key (a, src) where the skip-indexed field a is constant in the segment. */
  public void testCompositeKeyWithConstantNamedField() throws IOException {
    Sort sort = new Sort(new PairKeySortField("a"), new SortField("f", SortField.Type.LONG));
    runScenario(
        "pair-key", buildIndex(sort, doc -> doc.add(NumericDocValuesField.indexedField("a", 7))));
  }
}

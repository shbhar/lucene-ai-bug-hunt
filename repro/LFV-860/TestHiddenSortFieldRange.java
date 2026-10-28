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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongFunction;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.FieldFilterLeafReader;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verifier repro. A one-segment index is sorted by (c, f). Wrapping its leaf in a
 * FieldFilterLeafReader that hides the unrelated field c must not change which documents a range
 * query on f matches. Every range [lo, hi] over a small value domain is compared against a
 * brute-force scan of f's doc values on the same reader.
 */
public class TestHiddenSortFieldRange extends LuceneTestCase {

  // (c, f) per document, in insertion order. f values are distinct, so after the index sort by
  // (c, f) the doc-ID order of f is 1,3,6 | 2,4,5 | 0,7 (not monotone in f).
  private static final long[][] DOCS = {
    {1, 2}, {0, 6}, {1, 4}, {0, 1}, {0, 3}, {1, 5}, {2, 0}, {2, 7}
  };

  private interface DocBuilder {
    void add(Document doc, long c, long f);
  }

  private static BytesRef term(long v) {
    return new BytesRef(Long.toString(v)); // single digit, so byte order == numeric order
  }

  private Directory dir;
  private DirectoryReader reader;

  private LeafReader buildIndex(Sort indexSort, DocBuilder builder) throws IOException {
    dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setIndexSort(indexSort);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (long[] d : DOCS) {
        Document doc = new Document();
        doc.add(new StringField("z", "x", Field.Store.NO));
        builder.add(doc, d[0], d[1]);
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

  /** f values in doc-ID order, read from the unwrapped segment. */
  private static long[] fValues(LeafReader leaf) throws IOException {
    long[] out = new long[leaf.maxDoc()];
    SortedNumericDocValues sn = DocValues.getSortedNumeric(leaf, "f");
    for (int doc = 0; doc < leaf.maxDoc(); doc++) {
      assertTrue(sn.advanceExact(doc));
      assertEquals(1, sn.docValueCount());
      out[doc] = sn.nextValue();
    }
    return out;
  }

  private static long[] fValuesSorted(LeafReader leaf) throws IOException {
    long[] out = new long[leaf.maxDoc()];
    var sv = DocValues.getSorted(leaf, "f");
    for (int doc = 0; doc < leaf.maxDoc(); doc++) {
      assertTrue(sv.advanceExact(doc));
      out[doc] = Long.parseLong(sv.lookupOrd(sv.ordValue()).utf8ToString());
    }
    return out;
  }

  /** Runs every range on the given reader; returns a description of each mismatch. */
  private static List<String> check(
      String label, LeafReader r, long[] f, LongBinaryQuery qf) throws IOException {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    List<String> bad = new ArrayList<>();
    for (long lo = -1; lo <= 8; lo++) {
      for (long hi = lo; hi <= 8; hi++) {
        Set<Integer> expected = new TreeSet<>();
        for (int doc = 0; doc < f.length; doc++) {
          if (f[doc] >= lo && f[doc] <= hi) {
            expected.add(doc);
          }
        }
        Query q = qf.make(lo, hi);
        try {
          Set<Integer> got = new TreeSet<>();
          for (ScoreDoc sd : s.search(q, r.maxDoc() + 1).scoreDocs) {
            got.add(sd.doc);
          }
          int count = s.count(q);
          if (got.equals(expected) == false || count != expected.size()) {
            bad.add(
                label + " f:[" + lo + " TO " + hi + "] expected " + expected + " got " + got
                    + " count=" + count);
          }
        } catch (RuntimeException e) {
          bad.add(label + " f:[" + lo + " TO " + hi + "] threw " + e);
        }
      }
    }
    return bad;
  }

  private interface LongBinaryQuery {
    Query make(long lo, long hi);
  }

  private void runScenario(
      String name, Sort indexSort, DocBuilder builder, LongBinaryQuery qf, boolean sortedField)
      throws IOException {
    LeafReader leaf = buildIndex(indexSort, builder);
    long[] f = sortedField ? fValuesSorted(leaf) : fValues(leaf);
    LeafReader hideZ = new FieldFilterLeafReader(leaf, Set.of("z"), true);
    LeafReader hideC = new FieldFilterLeafReader(leaf, Set.of("c"), true);
    assertNull(hideC.getFieldInfos().fieldInfo("c"));
    assertNotNull(hideC.getFieldInfos().fieldInfo("f"));
    assertEquals(indexSort, hideC.getMetaData().sort());

    System.out.println(
        name + ": f in doc order " + java.util.Arrays.toString(f)
            + "; getPrimarySortField(plain)=" + primary(leaf)
            + " getPrimarySortField(hide c)=" + primary(hideC));

    // Controls: unwrapped segment and a wrapper that hides only the unrelated field z.
    List<String> plainBad = check(name + " plain", leaf, f, qf);
    List<String> zBad = check(name + " hide-z", hideZ, f, qf);
    assertTrue("control failed: " + plainBad, plainBad.isEmpty());
    assertTrue("control failed: " + zBad, zBad.isEmpty());

    List<String> cBad = check(name + " hide-c", hideC, f, qf);
    int total = 0;
    for (long lo = -1; lo <= 8; lo++) {
      total += (int) (8 - lo + 1);
    }
    System.out.println(name + ": hide-c mismatches " + cBad.size() + "/" + total);
    for (int i = 0; i < Math.min(5, cBad.size()); i++) {
      System.out.println("  " + cBad.get(i));
    }
    assertTrue(
        name + ": hiding c changed the hits of " + cBad.size() + "/" + total + " ranges on f; first: "
            + cBad.subList(0, Math.min(3, cBad.size())),
        cBad.isEmpty());
  }

  /**
   * Diagnostic only (not used by any assertion): the public static Sort.getPrimarySortField exists
   * from 10.6 on; looked up by name so the same source also compiles against older releases.
   */
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

  /** NumericDocValuesField with a skip index on f; LONG sort on (c, f). */
  public void testNumericSkipIndexRange() throws IOException {
    runScenario(
        "numeric",
        new Sort(new SortField("c", SortField.Type.LONG), new SortField("f", SortField.Type.LONG)),
        (doc, c, f) -> {
          doc.add(NumericDocValuesField.indexedField("c", c));
          doc.add(NumericDocValuesField.indexedField("f", f));
        },
        (lo, hi) -> NumericDocValuesField.newSlowRangeQuery("f", lo, hi),
        false);
  }

  /** SortedDocValuesField with a skip index on f; sort on (c LONG, f STRING). */
  public void testSortedSkipIndexRange() throws IOException {
    LongFunction<BytesRef> t = TestHiddenSortFieldRange::term;
    runScenario(
        "sorted",
        new Sort(
            new SortField("c", SortField.Type.LONG), new SortField("f", SortField.Type.STRING)),
        (doc, c, f) -> {
          doc.add(NumericDocValuesField.indexedField("c", c));
          doc.add(SortedDocValuesField.indexedField("f", t.apply(f)));
        },
        (lo, hi) ->
            SortedDocValuesField.newSlowRangeQuery(
                "f", t.apply(Math.max(lo, 0)), t.apply(Math.min(hi, 9)), true, true),
        true);
  }

  /** LongField (points + doc values, no skip index) on f; sort on (c LONG, f SORTED_NUMERIC LONG). */
  public void testLongFieldRange() throws IOException {
    runScenario(
        "longfield",
        new Sort(
            new SortField("c", SortField.Type.LONG),
            new SortedNumericSortField("f", SortField.Type.LONG)),
        (doc, c, f) -> {
          doc.add(new NumericDocValuesField("c", c));
          doc.add(new LongField("f", f, Field.Store.NO));
        },
        (lo, hi) -> LongField.newRangeQuery("f", lo, hi),
        false);
  }
}

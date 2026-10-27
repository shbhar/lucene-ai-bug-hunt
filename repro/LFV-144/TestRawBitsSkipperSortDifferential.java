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
import java.util.Arrays;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Checks for a fix: a FLOAT/DOUBLE sort on raw IEEE bits in a NUMERIC field with a skip index
 * ("skip") must return the same hits as the same values without a skip index ("plain").
 */
public class TestRawBitsSkipperSortDifferential extends LuceneTestCase {

  /** A NaN whose sign bit is set sorts above every number (Float#compare), so it must be found. */
  public void testNegativeSignNaNDescending() throws IOException {
    float negativeNaN = Float.intBitsToFloat(0xFFC00000);
    assertTrue(Float.isNaN(negativeNaN));
    float[] values = new float[10_000];
    for (int i = 0; i < values.length; i++) {
      // docs 0-8191 hold 1000..9191, docs 8192-9999 hold 0..1807, the last doc a NaN
      values[i] = i < 8192 ? 1000 + i : i - 8192;
    }
    values[values.length - 1] = negativeNaN;
    try (Directory dir = newDirectory()) {
      index(dir, values, 1);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newPlainSearcher(reader);
        Sort sortPlain = new Sort(new SortField("plain", SortField.Type.FLOAT, true));
        Sort sortSkip = new Sort(new SortField("skip", SortField.Type.FLOAT, true));
        Query q = new TermQuery(new Term("all", "x"));
        TopFieldDocs plain = searcher.search(q, 3, sortPlain);
        TopFieldDocs skip = searcher.search(q, 3, sortSkip);
        assertEquals("control", 9999, plain.scoreDocs[0].doc);
        assertEquals(
            "skip index: got " + Arrays.toString(docs(skip)),
            Arrays.toString(docs(plain)),
            Arrays.toString(docs(skip)));
      }
    }
  }

  public void testRandomFloat() throws IOException {
    doTestRandom(true);
  }

  public void testRandomDouble() throws IOException {
    doTestRandom(false);
  }

  private void doTestRandom(boolean isFloat) throws IOException {
    Random r = random();
    int iters = atLeast(20);
    for (int iter = 0; iter < iters; iter++) {
      int numDocs = TestUtil.nextInt(r, 2_000, 20_000);
      float[] values = new float[numDocs];
      int shape = r.nextInt(4);
      for (int i = 0; i < numDocs; i++) {
        values[i] =
            switch (shape) {
              case 0 -> -i; // descending run
              case 1 -> i - numDocs / 2; // mixed sign, ascending run
              case 2 -> (float) (r.nextGaussian() * 1000);
              default -> special(r);
            };
      }
      try (Directory dir = newDirectory()) {
        index(dir, values, TestUtil.nextInt(r, 1, 4), isFloat);
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          IndexSearcher searcher = newPlainSearcher(reader);
          Query q = new TermQuery(new Term("all", "x"));
          SortField.Type type = isFloat ? SortField.Type.FLOAT : SortField.Type.DOUBLE;
          for (int s = 0; s < 8; s++) {
            boolean reverse = r.nextBoolean();
            boolean tieBreak = r.nextBoolean();
            int n = TestUtil.nextInt(r, 1, 20);
            Sort sortPlain = sort("plain", type, reverse, tieBreak);
            Sort sortSkip = sort("skip", type, reverse, tieBreak);
            FieldDoc after = null;
            if (r.nextBoolean()) {
              TopFieldDocs all = searcher.search(q, numDocs, sortPlain);
              after = (FieldDoc) all.scoreDocs[r.nextInt(numDocs)];
            }
            TopDocs plain =
                after == null
                    ? searcher.search(q, n, sortPlain)
                    : searcher.searchAfter(after, q, n, sortPlain);
            TopDocs skip =
                after == null
                    ? searcher.search(q, n, sortSkip)
                    : searcher.searchAfter(after, q, n, sortSkip);
            assertEquals(
                "iter="
                    + iter
                    + " shape="
                    + shape
                    + " reverse="
                    + reverse
                    + " tieBreak="
                    + tieBreak
                    + " n="
                    + n
                    + " after="
                    + after,
                Arrays.toString(docs(plain)),
                Arrays.toString(docs(skip)));
          }
        }
      }
    }
  }

  private static float special(Random r) {
    return switch (r.nextInt(10)) {
      case 0 -> -0f;
      case 1 -> 0f;
      case 2 -> Float.NEGATIVE_INFINITY;
      case 3 -> Float.POSITIVE_INFINITY;
      case 4 -> Float.NaN;
      case 5 -> Float.intBitsToFloat(0xFFC00000); // NaN with the sign bit set
      default -> (float) (r.nextGaussian() * 10);
    };
  }

  private static Sort sort(String field, SortField.Type type, boolean reverse, boolean tieBreak) {
    SortField sf = new SortField(field, type, reverse);
    return tieBreak ? new Sort(sf, new SortField(null, SortField.Type.DOC, true)) : new Sort(sf);
  }

  private static IndexSearcher newPlainSearcher(DirectoryReader reader) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    return searcher;
  }

  private static int[] docs(TopDocs td) {
    int[] docs = new int[td.scoreDocs.length];
    for (int i = 0; i < docs.length; i++) {
      docs[i] = td.scoreDocs[i].doc;
    }
    return docs;
  }

  private static void index(Directory dir, float[] values, int segments) throws IOException {
    index(dir, values, segments, true);
  }

  private static void index(Directory dir, float[] values, int segments, boolean isFloat)
      throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int perSegment = (values.length + segments - 1) / segments;
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new StringField("all", "x", Field.Store.NO));
        if (isFloat) {
          doc.add(NumericDocValuesField.indexedField("skip", Float.floatToRawIntBits(values[i])));
          doc.add(new FloatDocValuesField("plain", values[i]));
        } else {
          double v = values[i];
          if (Float.isNaN(values[i]) && Float.floatToRawIntBits(values[i]) < 0) {
            v = Double.longBitsToDouble(0xFFF8000000000000L); // NaN with the sign bit set
          }
          doc.add(NumericDocValuesField.indexedField("skip", Double.doubleToRawLongBits(v)));
          doc.add(new DoubleDocValuesField("plain", v));
        }
        w.addDocument(doc);
        if ((i + 1) % perSegment == 0) {
          w.commit();
        }
      }
    }
  }
}

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
package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Numeric query-time join (JoinUtil.createJoinQuery with a numericType) over float and double join
 * values of either sign. The javadoc says the numeric join supports floats and doubles and does not
 * restrict the sign of the values. Each test indexes "from" docs carrying the join value in doc
 * values (raw IEEE bits, as FloatDocValuesField / DoubleDocValuesField store them and as the
 * upstream TestJoinUtil does) and "to" docs carrying the same value as a point, then checks how
 * many "to" docs the join matches.
 *
 * <p>The positive-value and integral-type tests are controls that are expected to pass.
 */
public class TestNumericJoinSignedFloatOrder extends LuceneTestCase {

  private static final String TYPE = "type";

  // ------------------------------------- tests asserting documented behaviour (two or more values
  // with the sign bit set)

  public void testFloatTwoNegativesNoScore() throws Exception {
    assertEquals(
        2, floatJoin(false, ScoreMode.None, new float[] {-1f, -2f}, new float[] {-1f, -2f, 7f}));
  }

  public void testFloatTwoNegativesMaxScore() throws Exception {
    assertEquals(
        2, floatJoin(false, ScoreMode.Max, new float[] {-1f, -2f}, new float[] {-1f, -2f, 7f}));
  }

  public void testDoubleTwoNegativesNoScore() throws Exception {
    assertEquals(
        2,
        doubleJoin(
            false, ScoreMode.None, new double[] {-10.5, -0.25}, new double[] {-10.5, -0.25, 3}));
  }

  public void testDoubleTwoNegativesTotalScore() throws Exception {
    assertEquals(
        2,
        doubleJoin(
            false, ScoreMode.Total, new double[] {-10.5, -0.25}, new double[] {-10.5, -0.25, 3}));
  }

  /** Negative zero has the sign bit set, so it also counts as a "negative" for the raw-bit sort. */
  public void testFloatNegativeZeroAndNegativeNoScore() throws Exception {
    assertEquals(
        2, floatJoin(false, ScoreMode.None, new float[] {-0f, -3.5f}, new float[] {-0f, -3.5f}));
  }

  /** Multi-valued from side (SortedNumericDocValuesField with raw bits, as TestJoinUtil does). */
  public void testFloatMultiValuedTwoNegativesNoScore() throws Exception {
    assertEquals(
        2, floatJoin(true, ScoreMode.None, new float[] {-1f, -2f}, new float[] {-1f, -2f, 7f}));
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  public void testControlFloatPositivesMaxScore() throws Exception {
    assertEquals(
        2, floatJoin(false, ScoreMode.Max, new float[] {1f, 2f}, new float[] {1f, 2f, 7f}));
  }

  /** Exactly one negative value: signed order of raw bits agrees with numeric order. */
  public void testControlFloatOneNegativeNoScore() throws Exception {
    assertEquals(
        3,
        floatJoin(false, ScoreMode.None, new float[] {-1f, 2f, 3f}, new float[] {-1f, 2f, 3f, 7f}));
  }

  public void testControlIntTwoNegativesNoScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (int v : new int[] {-1, -2}) {
          Document d = new Document();
          d.add(new StringField(TYPE, "from", Field.Store.NO));
          d.add(new NumericDocValuesField("fromV", v));
          w.addDocument(d);
        }
        for (int v : new int[] {-1, -2, 7}) {
          Document d = new Document();
          d.add(new StringField(TYPE, "to", Field.Store.NO));
          d.add(new IntPoint("toV", v));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q =
            JoinUtil.createJoinQuery(
                "fromV", false, "toV", Integer.class, fromQuery(), s, ScoreMode.None);
        assertEquals(2, s.count(q));
      }
    }
  }

  public void testControlLongTwoNegativesNoScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (long v : new long[] {-1L, -2L}) {
          Document d = new Document();
          d.add(new StringField(TYPE, "from", Field.Store.NO));
          d.add(new NumericDocValuesField("fromV", v));
          w.addDocument(d);
        }
        for (long v : new long[] {-1L, -2L, 7L}) {
          Document d = new Document();
          d.add(new StringField(TYPE, "to", Field.Store.NO));
          d.add(new LongPoint("toV", v));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q =
            JoinUtil.createJoinQuery(
                "fromV", false, "toV", Long.class, fromQuery(), s, ScoreMode.Max);
        assertEquals(2, s.count(q));
      }
    }
  }

  // ---------------------------------------------------------------- helpers

  private static Query fromQuery() {
    return new TermQuery(new Term(TYPE, "from"));
  }

  private int floatJoin(boolean multi, ScoreMode mode, float[] fromVals, float[] toVals)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        if (multi) {
          // one from doc carrying all values
          Document d = new Document();
          d.add(new StringField(TYPE, "from", Field.Store.NO));
          for (float v : fromVals) {
            d.add(new SortedNumericDocValuesField("fromV", Float.floatToRawIntBits(v)));
          }
          w.addDocument(d);
        } else {
          for (float v : fromVals) {
            Document d = new Document();
            d.add(new StringField(TYPE, "from", Field.Store.NO));
            d.add(new FloatDocValuesField("fromV", v));
            w.addDocument(d);
          }
        }
        for (float v : toVals) {
          Document d = new Document();
          d.add(new StringField(TYPE, "to", Field.Store.NO));
          d.add(new FloatPoint("toV", v));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q =
            JoinUtil.createJoinQuery("fromV", multi, "toV", Float.class, fromQuery(), s, mode);
        return s.count(q);
      }
    }
  }

  private int doubleJoin(boolean multi, ScoreMode mode, double[] fromVals, double[] toVals)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (double v : fromVals) {
          Document d = new Document();
          d.add(new StringField(TYPE, "from", Field.Store.NO));
          d.add(new DoubleDocValuesField("fromV", v));
          w.addDocument(d);
        }
        for (double v : toVals) {
          Document d = new Document();
          d.add(new StringField(TYPE, "to", Field.Store.NO));
          d.add(new DoublePoint("toV", v));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q =
            JoinUtil.createJoinQuery("fromV", multi, "toV", Double.class, fromQuery(), s, mode);
        return s.count(q);
      }
    }
  }
}

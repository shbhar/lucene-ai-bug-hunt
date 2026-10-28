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
package verify.v1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FunctionRangeQuery over ValueSource.fromDoubleValuesSource(...) versus the same field read through
 * the typed ValueSource (DoubleFieldSource / LongFieldSource) and versus an exact Java reference.
 *
 * <p>Uses only public API, from a package outside org.apache.lucene.
 *
 * <p>Each test asserts the typed path first ("CONTROL"), so a failure of the bridged assertion
 * cannot come from a wrong reference. testControlFloatExactBounds is expected to pass everywhere.
 */
public class TestBridgedRangePrecision extends LuceneTestCase {

  private static final double ONE_UP = Math.nextUp(1.0); // 1.0000000000000002
  private static final double ONE_DOWN = Math.nextDown(1.0); // 0.9999999999999999

  // all values non-negative: FunctionRangeQuery scores are the values, and Lucene requires
  // non-negative scores (the test framework's AssertingScorer checks this)
  private static final double[] DOUBLES = {0.5, ONE_DOWN, 1.0, ONE_UP, 1.1, 3.5e38, 1e301};

  // doubles that lie exactly halfway between two adjacent floats. For these, the bridged path's
  // value rounding ((float) v, ties-to-even) and bound parsing (Float.parseFloat of
  // Double.toString(b)) can round in opposite directions.
  private static final double MID1 = 1.0 + Math.scalb(1.0, -24); // 1.0000000596046448
  private static final double MID3 = 1.0 + 3 * Math.scalb(1.0, -24); // 1.0000001788139343
  private static final double[] MIDPOINTS = {
    1.0, MID1, (double) Math.nextUp(1.0f), MID3, (double) Math.nextUp(Math.nextUp(1.0f))
  };

  // epoch milliseconds: 10 s before, at, 60 s after and 200 s after a cut-off
  private static final long CUT = 1_700_000_000_000L;
  private static final long[] LONGS = {
    16_777_216L, 16_777_217L, 16_777_218L, CUT - 10_000L, CUT, CUT + 60_000L, CUT + 200_000L
  };

  // ---- double field -------------------------------------------------------------------------

  /** [1.0 TO 1.0] must match only 1.0, not its double neighbours. */
  public void testDoubleInclusivePoint() throws Exception {
    checkDouble(DOUBLES, 1.0, 1.0, true, true);
  }

  /** {1.0 TO *] must match 1.0000000000000002. */
  public void testDoubleExclusiveLower() throws Exception {
    checkDouble(DOUBLES, 1.0, null, false, true);
  }

  /** [* TO 1e300] must not match 1e301. */
  public void testDoubleUpperBeyondFloatRange() throws Exception {
    checkDouble(DOUBLES, null, 1e300, true, true);
  }

  /** [1e39 TO *] must not match 3.5e38. */
  public void testDoubleLowerBeyondFloatRange() throws Exception {
    checkDouble(DOUBLES, 1e39, null, true, true);
  }

  /** Bounds exactly representable as float, no value within float rounding of them: must agree. */
  public void testControlFloatExactBounds() throws Exception {
    checkDouble(DOUBLES, 0.5, 2.0, true, true);
  }

  /** [MID1 TO MID1] must match the document whose value is exactly MID1. */
  public void testDoubleInclusivePointOnFloatMidpoint() throws Exception {
    checkDouble(MIDPOINTS, MID1, MID1, true, true);
  }

  /** {MID3 TO *] must not match the document whose value is exactly MID3. */
  public void testDoubleExclusiveLowerOnFloatMidpoint() throws Exception {
    checkDouble(MIDPOINTS, MID3, null, false, true);
  }

  /** The same field through an expression (the expressions module returns DoubleValuesSource). */
  public void testExpressionInclusivePoint() throws Exception {
    Expression expr = JavascriptCompiler.compile("d");
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("d", DoubleValuesSource.fromDoubleField("d"));
    ValueSource bridged = ValueSource.fromDoubleValuesSource(expr.getDoubleValuesSource(bindings));
    try (Directory dir = newDirectory()) {
      indexDoubles(dir, DOUBLES);
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r, false);
        String expected = expectedDoubles(DOUBLES, 1.0, 1.0, true, true);
        assertEquals(
            "CONTROL DoubleFieldSource [1.0 TO 1.0]",
            expected,
            hitsDouble(s, new DoubleFieldSource("d"), 1.0, 1.0, true, true));
        assertEquals(
            "expression 'd' via fromDoubleValuesSource [1.0 TO 1.0]",
            expected,
            hitsDouble(s, bridged, 1.0, 1.0, true, true));
      }
    }
  }

  // ---- long field ---------------------------------------------------------------------------

  /** [16777217 TO 16777217] must match only 16777217 (2^24 + 1). */
  public void testLongInclusivePoint() throws Exception {
    checkLong(16_777_217L, 16_777_217L, true, true);
  }

  /** Epoch millis: [* TO cut] must not match a document 60 seconds after the cut. */
  public void testLongEpochMillisUpper() throws Exception {
    checkLong(null, CUT, true, true);
  }

  // ---- helpers ------------------------------------------------------------------------------

  private void checkDouble(double[] values, Double lo, Double hi, boolean incLo, boolean incHi)
      throws Exception {
    try (Directory dir = newDirectory()) {
      indexDoubles(dir, values);
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r, false);
        String range = rangeString(lo, hi, incLo, incHi);
        String expected = expectedDoubles(values, lo, hi, incLo, incHi);
        assertEquals(
            "CONTROL DoubleFieldSource " + range,
            expected,
            hitsDouble(s, new DoubleFieldSource("d"), lo, hi, incLo, incHi));
        ValueSource bridged =
            ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("d"));
        assertEquals(
            "fromDoubleValuesSource(fromDoubleField) " + range,
            expected,
            hitsDouble(s, bridged, lo, hi, incLo, incHi));
      }
    }
  }

  private void checkLong(Long lo, Long hi, boolean incLo, boolean incHi) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (long v : LONGS) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("l", v));
          w.addDocument(doc);
        }
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r, false);
        String range = rangeString(lo, hi, incLo, incHi);
        TreeSet<Long> exp = new TreeSet<>();
        for (long v : LONGS) {
          boolean okLo = lo == null || (incLo ? v >= lo : v > lo);
          boolean okHi = hi == null || (incHi ? v <= hi : v < hi);
          if (okLo && okHi) exp.add(v);
        }
        String expected = exp.toString();
        assertEquals(
            "CONTROL LongFieldSource " + range,
            expected,
            hitsLong(s, new LongFieldSource("l"), lo, hi, incLo, incHi));
        ValueSource bridged =
            ValueSource.fromDoubleValuesSource(
                LongValuesSource.fromLongField("l").toDoubleValuesSource());
        assertEquals(
            "fromDoubleValuesSource(fromLongField.toDoubleValuesSource) " + range,
            expected,
            hitsLong(s, bridged, lo, hi, incLo, incHi));
      }
    }
  }

  private void indexDoubles(Directory dir, double[] values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (double v : values) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("d", v));
        w.addDocument(doc);
      }
    }
  }

  private static String expectedDoubles(
      double[] values, Double lo, Double hi, boolean incLo, boolean incHi) {
    TreeSet<Double> exp = new TreeSet<>();
    for (double v : values) {
      boolean okLo = lo == null || (incLo ? v >= lo : v > lo);
      boolean okHi = hi == null || (incHi ? v <= hi : v < hi);
      if (okLo && okHi) exp.add(v);
    }
    return exp.toString();
  }

  private static String hitsDouble(
      IndexSearcher s, ValueSource vs, Double lo, Double hi, boolean incLo, boolean incHi)
      throws IOException {
    TreeSet<Double> got = new TreeSet<>();
    for (long raw : hitRawValues(s, "d", new FunctionRangeQuery(vs, lo, hi, incLo, incHi))) {
      got.add(Double.longBitsToDouble(raw));
    }
    return got.toString();
  }

  private static String hitsLong(
      IndexSearcher s, ValueSource vs, Long lo, Long hi, boolean incLo, boolean incHi)
      throws IOException {
    TreeSet<Long> got = new TreeSet<>();
    for (long raw : hitRawValues(s, "l", new FunctionRangeQuery(vs, lo, hi, incLo, incHi))) {
      got.add(raw);
    }
    return got.toString();
  }

  /** Runs the query and returns the raw doc-values long of every hit. */
  private static List<Long> hitRawValues(IndexSearcher s, String field, FunctionRangeQuery q)
      throws IOException {
    List<LeafReaderContext> leaves = s.getIndexReader().leaves();
    List<Long> out = new ArrayList<>();
    for (ScoreDoc sd : s.search(q, 1000).scoreDocs) {
      LeafReaderContext ctx = leaves.get(ReaderUtil.subIndex(sd.doc, leaves));
      NumericDocValues dv = DocValues.getNumeric(ctx.reader(), field);
      assertTrue(dv.advanceExact(sd.doc - ctx.docBase));
      out.add(dv.longValue());
    }
    return out;
  }

  private static String rangeString(Object lo, Object hi, boolean incLo, boolean incHi) {
    return (incLo ? "[" : "{")
        + (lo == null ? "*" : lo)
        + " TO "
        + (hi == null ? "*" : hi)
        + (incHi ? "]" : "}");
  }
}

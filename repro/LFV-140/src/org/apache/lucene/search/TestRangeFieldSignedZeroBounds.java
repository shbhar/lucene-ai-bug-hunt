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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleRange;
import org.apache.lucene.document.DoubleRangeDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatRange;
import org.apache.lucene.document.FloatRangeDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleRange / FloatRange (and their doc-values fields) validate {@code min <= max} with the
 * primitive {@code >} operator, which treats +0.0 and -0.0 as equal, but encode the bounds in
 * sortable order, where -0.0 sorts before +0.0. The range [+0.0, -0.0] is therefore accepted, yet
 * stored as an inverted range: a document indexed with it is WITHIN and CONTAINS the same range
 * but does not INTERSECT it. The javadoc of newIntersectsQuery says it matches "intersecting ranges
 * (overlap, within, or contains)".
 *
 * <p>Each test accepts either outcome that keeps the relations consistent: the field constructor
 * rejecting the range with IllegalArgumentException, or INTERSECTS matching the document.
 */
public class TestRangeFieldSignedZeroBounds extends LuceneTestCase {

  private interface FieldFactory {
    Field create();
  }

  private static IndexReader readerWith(Directory dir, Field... fields) throws IOException {
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    for (Field f : fields) {
      doc.add(f);
    }
    w.addDocument(doc);
    IndexReader reader = w.getReader();
    w.close();
    return reader;
  }

  /** Returns null if the factory rejects the range (a consistent outcome). */
  private static Field tryCreate(FieldFactory f) {
    try {
      return f.create();
    } catch (IllegalArgumentException expected) {
      return null;
    }
  }

  public void testDoubleRangeIntersectsItself() throws IOException {
    double[] min = {0.0};
    double[] max = {-0.0};
    Field field = tryCreate(() -> new DoubleRange("r", min, max));
    if (field == null) {
      return;
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, field)) {
      IndexSearcher s = newSearcher(reader);
      assertEquals("WITHIN(self)", 1, s.count(DoubleRange.newWithinQuery("r", min, max)));
      assertEquals("CONTAINS(self)", 1, s.count(DoubleRange.newContainsQuery("r", min, max)));
      assertEquals(
          "the accepted range [0.0, -0.0] is WITHIN and CONTAINS itself, so it must INTERSECT itself",
          1,
          s.count(DoubleRange.newIntersectsQuery("r", min, max)));
    }
  }

  public void testFloatRangeIntersectsItself() throws IOException {
    float[] min = {0.0f};
    float[] max = {-0.0f};
    Field field = tryCreate(() -> new FloatRange("r", min, max));
    if (field == null) {
      return;
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, field)) {
      IndexSearcher s = newSearcher(reader);
      assertEquals("WITHIN(self)", 1, s.count(FloatRange.newWithinQuery("r", min, max)));
      assertEquals("CONTAINS(self)", 1, s.count(FloatRange.newContainsQuery("r", min, max)));
      assertEquals(
          "the accepted range [0.0, -0.0] is WITHIN and CONTAINS itself, so it must INTERSECT itself",
          1,
          s.count(FloatRange.newIntersectsQuery("r", min, max)));
    }
  }

  public void testDoubleRangeDocValuesIntersectsItself() throws IOException {
    double[] min = {0.0};
    double[] max = {-0.0};
    Field field = tryCreate(() -> new DoubleRangeDocValuesField("r", min, max));
    if (field == null) {
      return;
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, field)) {
      IndexSearcher s = newSearcher(reader);
      assertEquals(
          "a document indexed with the accepted range [0.0, -0.0] must intersect the same range",
          1,
          s.count(DoubleRangeDocValuesField.newSlowIntersectsQuery("r", min, max)));
    }
  }

  public void testFloatRangeDocValuesIntersectsItself() throws IOException {
    float[] min = {0.0f};
    float[] max = {-0.0f};
    Field field = tryCreate(() -> new FloatRangeDocValuesField("r", min, max));
    if (field == null) {
      return;
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, field)) {
      IndexSearcher s = newSearcher(reader);
      assertEquals(
          "a document indexed with the accepted range [0.0, -0.0] must intersect the same range",
          1,
          s.count(FloatRangeDocValuesField.newSlowIntersectsQuery("r", min, max)));
    }
  }

  /**
   * The same input as a query bound: an intersects query [0.0, -0.0] is accepted, but it misses a
   * document whose range is exactly [0.0, 0.0] (the query's own lower bound) while a CONTAINS
   * query with the same bounds matches that document.
   */
  public void testDoubleRangeQueryBoundsZeroToNegativeZero() throws IOException {
    double[] qmin = {0.0};
    double[] qmax = {-0.0};
    Query intersects;
    try {
      intersects = DoubleRange.newIntersectsQuery("r", qmin, qmax);
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the inverted bounds is a consistent outcome
    }
    try (Directory dir = newDirectory();
        IndexReader reader = readerWith(dir, new DoubleRange("r", new double[] {0.0}, new double[] {0.0}))) {
      IndexSearcher s = newSearcher(reader);
      assertEquals("CONTAINS", 1, s.count(DoubleRange.newContainsQuery("r", qmin, qmax)));
      assertEquals(
          "a document matched by CONTAINS must also be matched by INTERSECTS", 1, s.count(intersects));
    }
  }
}

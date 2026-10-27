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
package org.apache.lucene.classification.utils;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * DatasetSplitter(testRatio, crossValidationRatio): both ratios are documented as "the ratio of
 * the original index to be used for the test IDX" (resp. "c.v. IDX") "as a double between 0.0 and
 * 1.0". These tests check the size of each output index for legal ratios (testRatio +
 * crossValidationRatio <= 1). Uses public API only.
 */
@LuceneTestCase.SuppressCodecs("SimpleText")
public class TestSplitterTestRatioV1 extends LuceneTestCase {

  private static final String CLASS_FIELD = "label";
  private static final String TEXT_FIELD = "body";

  /** Returns {test, cv, training} maxDoc of the three output indexes. */
  private int[] split(int[] classSizes, double testRatio, double cvRatio) throws Exception {
    try (Directory src = newDirectory();
        Directory train = newDirectory();
        Directory test = newDirectory();
        Directory cv = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(new MockAnalyzer(random())))) {
        int id = 0;
        for (int c = 0; c < classSizes.length; c++) {
          String label = "c" + c;
          for (int i = 0; i < classSizes[c]; i++) {
            Document d = new Document();
            d.add(new Field(TEXT_FIELD, "doc number " + (id++), TextField.TYPE_STORED));
            d.add(new Field(CLASS_FIELD, label, TextField.TYPE_STORED));
            d.add(new SortedDocValuesField(CLASS_FIELD, new BytesRef(label)));
            w.addDocument(d);
          }
        }
        w.forceMerge(1);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      // split() closes the reader it is given
      new DatasetSplitter(testRatio, cvRatio)
          .split(reader, train, test, cv, new MockAnalyzer(random()), false, CLASS_FIELD);
      int[] out = new int[3];
      try (DirectoryReader r = DirectoryReader.open(test)) {
        out[0] = r.maxDoc();
      }
      try (DirectoryReader r = DirectoryReader.open(cv)) {
        out[1] = r.maxDoc();
      }
      try (DirectoryReader r = DirectoryReader.open(train)) {
        out[2] = r.maxDoc();
      }
      return out;
    }
  }

  private static String fmt(int[] s) {
    return "(test, cv, training) = (" + s[0] + ", " + s[1] + ", " + s[2] + ")";
  }

  /** One class of 20 docs, testRatio 0.8, no cross-validation: 16 test docs expected. */
  public void testTestRatio08SingleClass() throws Exception {
    int[] s = split(new int[] {20}, 0.8, 0.0);
    assertEquals("total " + fmt(s), 20, s[0] + s[1] + s[2]);
    assertEquals("test docs for testRatio=0.8 " + fmt(s), 16, s[0]);
  }

  /** testRatio 1.0: the whole original index is to be used for the test index. */
  public void testTestRatio10SingleClass() throws Exception {
    int[] s = split(new int[] {20}, 1.0, 0.0);
    assertEquals("test docs for testRatio=1.0 " + fmt(s), 20, s[0]);
  }

  /** testRatio 0.6 with crossValidationRatio 0.2: 12 / 4 / 4 expected. */
  public void testTestRatio06WithCv02() throws Exception {
    int[] s = split(new int[] {20}, 0.6, 0.2);
    assertEquals("test docs " + fmt(s), 12, s[0]);
    assertEquals("cv docs " + fmt(s), 4, s[1]);
    assertEquals("training docs " + fmt(s), 4, s[2]);
  }

  /**
   * Same check as the upstream TestDataSplitter.assertSplit oracle (test size == (int)(maxDoc *
   * testRatio) within 20), on 10 classes of 10 docs with testRatio 0.9.
   */
  public void testUpstreamOracleShapeTenClasses() throws Exception {
    int[] sizes = new int[10];
    java.util.Arrays.fill(sizes, 10);
    int[] s = split(sizes, 0.9, 0.0);
    assertEquals("test docs vs (int)(100*0.9) within 20 " + fmt(s), (int) (100 * 0.9), s[0], 20);
  }

  // ---- controls (expected to PASS on the current code) ----

  /** Control: the cross-validation ratio is honoured above one half. */
  public void testControlCvRatio08SingleClass() throws Exception {
    int[] s = split(new int[] {20}, 0.0, 0.8);
    assertEquals("cv docs for crossValidationRatio=0.8 " + fmt(s), 16, s[1]);
    assertEquals("test docs " + fmt(s), 0, s[0]);
  }

  /** Control: testRatio exactly one half on an even-sized class is exact. */
  public void testControlTestRatio05SingleClass() throws Exception {
    int[] s = split(new int[] {20}, 0.5, 0.0);
    assertEquals("test docs for testRatio=0.5 " + fmt(s), 10, s[0]);
  }

  /** Control: a small testRatio is exact. */
  public void testControlTestRatio02SingleClass() throws Exception {
    int[] s = split(new int[] {20}, 0.2, 0.0);
    assertEquals("test docs for testRatio=0.2 " + fmt(s), 4, s[0]);
  }
}

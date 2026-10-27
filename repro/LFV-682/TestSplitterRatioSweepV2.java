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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** Verifier V2: sweep testRatio vs crossValidationRatio, and check per-class test counts. */
public class TestSplitterRatioSweepV2 extends LuceneTestCase {

  /** classSizes[i] documents of class "c" + i, added class by class. Returns test,cv,train,perClassTest... */
  private static int[] split(int[] classSizes, double testRatio, double cvRatio) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory src = new ByteBuffersDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(analyzer))) {
        int id = 0;
        for (int c = 0; c < classSizes.length; c++) {
          for (int i = 0; i < classSizes[c]; i++) {
            Document d = new Document();
            d.add(new StringField("id", "d" + (id++), Field.Store.YES));
            d.add(new StringField("cls", "c" + c, Field.Store.YES));
            d.add(new SortedDocValuesField("cls", new BytesRef("c" + c)));
            w.addDocument(d);
          }
        }
        w.forceMerge(1);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      new DatasetSplitter(testRatio, cvRatio).split(reader, train, test, cv, analyzer, false, "cls");
      int[] out = new int[3 + classSizes.length];
      Directory[] dirs = {test, cv, train};
      for (int i = 0; i < 3; i++) {
        try (DirectoryReader r = DirectoryReader.open(dirs[i])) {
          out[i] = r.numDocs();
          if (i == 0) {
            IndexSearcher s = new IndexSearcher(r);
            for (int c = 0; c < classSizes.length; c++) {
              // split() re-indexes stored fields as analyzed TextField; MockAnalyzer lowercases "c0" -> "c0"
              out[3 + c] = s.count(new TermQuery(new Term("cls", "c" + c)));
            }
          }
        }
      }
      return out;
    }
  }

  private static String fmt(int[] a) {
    StringBuilder sb = new StringBuilder();
    for (int x : a) sb.append(x).append(' ');
    return sb.toString().trim();
  }

  public void testSweep() throws Exception {
    List<String> failures = new ArrayList<>();
    int n = 20;
    double[] ratios = {0.1, 0.3, 0.5, 0.55, 0.6, 0.75, 0.9, 1.0};
    System.out.println("== one class of " + n + " docs: [test cv train]");
    for (double r : ratios) {
      int[] asTest = split(new int[] {n}, r, 0);
      int[] asCv = split(new int[] {n}, 0, r);
      int expected = (int) Math.ceil(n * r);
      System.out.println(
          "ratio="
              + r
              + "  as testRatio -> ["
              + asTest[0] + " " + asTest[1] + " " + asTest[2]
              + "]  as cvRatio -> ["
              + asCv[0] + " " + asCv[1] + " " + asCv[2]
              + "]  ceil(n*r)=" + expected);
      if (asTest[0] != expected) {
        failures.add("testRatio=" + r + " gave " + asTest[0] + " test docs, expected " + expected);
      }
    }
    // global counter b is not reset per class: two classes of 5, testRatio 0.5
    int[] two = split(new int[] {5, 5}, 0.5, 0);
    System.out.println("== classes {5,5}, testRatio 0.5: test cv train c0 c1 = " + fmt(two));
    int[] twoHi = split(new int[] {5, 5}, 0.8, 0);
    System.out.println("== classes {5,5}, testRatio 0.8: test cv train c0 c1 = " + fmt(twoHi));
    // upstream TestDataSplitter oracle shape: 100 docs, 10 classes of 10, testRatio 0.8, tolerance 20
    int[] ten = new int[10];
    java.util.Arrays.fill(ten, 10);
    int[] up = split(ten, 0.8, 0.1);
    System.out.println("== 10 classes x 10 docs, testRatio 0.8, cv 0.1: test cv train = "
        + up[0] + " " + up[1] + " " + up[2] + "  (upstream oracle expects test 80 +-20)");
    if (Math.abs(up[0] - 80) > 20) {
      failures.add("upstream-style oracle: test=" + up[0] + " not within 20 of 80");
    }
    assertTrue(String.join("; ", failures), failures.isEmpty());
  }
}

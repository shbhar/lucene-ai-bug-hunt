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

import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Probe: the upstream TestDataSplitter fixture shape (10 random classes, stored text fields,
 * sorted doc values on the class field) and the upstream assertSplit oracle (each split index has
 * (int) (maxDoc * ratio) documents within 20), applied to ratios above one half. Also prints a
 * sweep so the cap is visible.
 */
public class TestDatasetSplitterUpstreamOracleV3 extends LuceneTestCase {

  private static final int NUM_DOCS = 1000;

  private static Directory buildSource() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    Random rnd = new Random(42);
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new Field("id", "id" + i, ft));
        doc.add(new Field("text", "body " + i, ft));
        String className = Integer.toString(rnd.nextInt(10));
        doc.add(new Field("class", className, ft));
        doc.add(new SortedDocValuesField("class", new BytesRef(className)));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Returns {test, cv, training} maxDoc. */
  private static int[] split(double testRatio, double cvRatio) throws Exception {
    try (Directory src = buildSource();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      DirectoryReader reader = DirectoryReader.open(src);
      new DatasetSplitter(testRatio, cvRatio)
          .split(reader, train, test, cv, new MockAnalyzer(random()), false, "class");
      int[] counts = new int[3];
      Directory[] dirs = {test, cv, train};
      for (int i = 0; i < 3; i++) {
        try (DirectoryReader r = DirectoryReader.open(dirs[i])) {
          counts[i] = r.maxDoc();
        }
      }
      return counts;
    }
  }

  public void testSweep() throws Exception {
    double[][] cases = {
      {0.1, 0.0}, {0.3, 0.0}, {0.5, 0.0}, {0.6, 0.0}, {0.7, 0.1}, {0.8, 0.0}, {0.9, 0.0},
      {1.0, 0.0}, {0.0, 0.8}, {0.2, 0.7}
    };
    for (double[] c : cases) {
      int[] n = split(c[0], c[1]);
      System.out.println(
          "SWEEP testRatio="
              + c[0]
              + " cvRatio="
              + c[1]
              + " -> test="
              + n[0]
              + " cv="
              + n[1]
              + " training="
              + n[2]
              + " (expected test~"
              + (int) (NUM_DOCS * c[0])
              + ", cv~"
              + (int) (NUM_DOCS * c[1])
              + ", training~"
              + (int) (NUM_DOCS * (1d - c[0] - c[1]))
              + ")");
    }
  }

  /** Learning-curve style split: 70% test, 10% cv, 20% training, upstream assertSplit oracle. */
  public void testUpstreamOracleLearningCurveSplit() throws Exception {
    double testRatio = 0.7, cvRatio = 0.1;
    int[] n = split(testRatio, cvRatio);
    String counts = "(test, cv, training = " + n[0] + ", " + n[1] + ", " + n[2] + ")";
    assertEquals("cv " + counts, (int) (NUM_DOCS * cvRatio), n[1], 20);
    assertEquals("test " + counts, (int) (NUM_DOCS * testRatio), n[0], 20);
    assertEquals("training " + counts, (int) (NUM_DOCS * (1d - testRatio - cvRatio)), n[2], 20);
  }
}

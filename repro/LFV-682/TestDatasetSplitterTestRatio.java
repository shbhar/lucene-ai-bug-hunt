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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * DatasetSplitter only puts a document into the test index when the running document counter is
 * even, so the test index never gets more than about half of each class, whatever testRatio is;
 * the cross-validation ratio, documented the same way, is honoured.
 */
public class TestDatasetSplitterTestRatio extends LuceneTestCase {

  private static final int NUM_DOCS = 10;

  /** Returns {test, cv, training} document counts. */
  private static int[] split(double testRatio, double cvRatio) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory src = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document d = new Document();
          d.add(new StringField("id", "d" + i, Field.Store.YES));
          d.add(new StringField("cls", "a", Field.Store.YES));
          d.add(new SortedDocValuesField("cls", new BytesRef("a")));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      new DatasetSplitter(testRatio, cvRatio).split(reader, train, test, cv, analyzer, false, "cls");
      reader.close(); // split closes it already; closing twice is a no-op
      int[] counts = new int[3];
      Directory[] dirs = {test, cv, train};
      for (int i = 0; i < 3; i++) {
        try (DirectoryReader r = DirectoryReader.open(dirs[i])) {
          counts[i] = r.numDocs();
        }
      }
      return counts;
    }
  }

  public void testTestRatioAboveHalf() throws Exception {
    // control: the cross-validation ratio 0.8 gives 8 of 10 documents
    int[] cv = split(0, 0.8);
    assertEquals("cv count for crossValidationRatio=0.8", 8, cv[1]);
    // the test ratio, documented the same way, should do the same
    int[] t = split(0.8, 0);
    assertEquals(
        "test count for testRatio=0.8 (test, cv, training = "
            + t[0]
            + ", "
            + t[1]
            + ", "
            + t[2]
            + ")",
        8,
        t[0]);
  }
}

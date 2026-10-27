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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** DatasetSplitter should honour testRatio over its documented range, as it does the cv ratio. */
public class TestDatasetSplitterRatios extends LuceneTestCase {

  /**
   * Splits an index of 10 documents, all of class "a", and returns the document counts of the test,
   * cross-validation and training indexes as "test/cv/training".
   */
  private String split(double testRatio, double crossValidationRatio) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory source = newDirectory();
        Directory training = newDirectory();
        Directory test = newDirectory();
        Directory cv = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(source, newIndexWriterConfig(analyzer))) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", "doc" + i, Field.Store.YES));
          doc.add(new SortedDocValuesField("class", new BytesRef("a")));
          writer.addDocument(doc);
        }
      }
      // split() closes the reader it is given
      new DatasetSplitter(testRatio, crossValidationRatio)
          .split(DirectoryReader.open(source), training, test, cv, analyzer, false, "class");
      return numDocs(test) + "/" + numDocs(cv) + "/" + numDocs(training);
    }
  }

  private static int numDocs(Directory dir) throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      return reader.numDocs();
    }
  }

  public void testCrossValidationRatioAboveHalf() throws IOException {
    assertEquals("crossValidationRatio=0.8, test/cv/training", "0/8/2", split(0, 0.8));
  }

  public void testTestRatioUpToHalf() throws IOException {
    assertEquals("testRatio=0.2, test/cv/training", "2/0/8", split(0.2, 0));
    assertEquals("testRatio=0.5, test/cv/training", "5/0/5", split(0.5, 0));
  }

  public void testTestRatioAboveHalf() throws IOException {
    assertEquals("testRatio=0.8, test/cv/training", "8/0/2", split(0.8, 0));
  }

  public void testTestRatioOne() throws IOException {
    assertEquals("testRatio=1.0, test/cv/training", "10/0/0", split(1.0, 0));
  }

  public void testTestAndCrossValidationRatios() throws IOException {
    assertEquals(
        "testRatio=0.6, crossValidationRatio=0.2, test/cv/training", "6/2/2", split(0.6, 0.2));
  }
}

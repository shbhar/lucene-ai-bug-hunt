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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleValues.withDefault returns missingValue for documents without a value, but since #16431
 * its getMaxScore forwards the wrapped instance's bound, which does not cover missingValue.
 */
public class TestDoubleValuesWithDefaultMaxScore extends LuceneTestCase {

  public void testMaxScoreCoversMissingValue() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          if (i != 5) { // doc 5 has no value
            doc.add(NumericDocValuesField.indexedField("price", i));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReaderContext leaf = reader.leaves().get(0);
        DoubleValues values =
            DoubleValues.withDefault(
                DoubleValuesSource.fromLongField("price").getValues(leaf, null), 100);
        int upTo = values.advanceShallow(0);
        float maxScore = values.getMaxScore(upTo);
        assertTrue(values.advanceExact(5));
        double value = values.doubleValue();
        assertEquals(100, value, 0d);
        assertTrue(
            "value " + value + " of doc 5 > getMaxScore(" + upTo + ") = " + maxScore,
            value <= maxScore);
      }
    }
  }
}

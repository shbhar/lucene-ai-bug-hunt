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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDoubleValuesWithDefaultMaxScore extends LuceneTestCase {

  public void testMaxScoreCoversMissingValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(new Document()); // doc 0: no price
        Document doc1 = new Document();
        doc1.add(NumericDocValuesField.indexedField("price", 1));
        w.addDocument(doc1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValues values =
            DoubleValues.withDefault(
                DoubleValuesSource.fromLongField("price").getValues(reader.leaves().get(0), null),
                100);
        int upTo = values.advanceShallow(0);
        float maxScore = values.getMaxScore(upTo);
        values.advanceExact(0);
        // expected: getMaxScore(upTo) >= 100, the value withDefault produces for doc 0; actual: 1.0
        assertTrue(
            "doc 0 value " + values.doubleValue() + " > getMaxScore(" + upTo + ") = " + maxScore,
            values.doubleValue() <= maxScore);
      }
    }
  }
}

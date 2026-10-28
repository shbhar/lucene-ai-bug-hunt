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
package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The KNearestFuzzyClassifier constructor documents that textFieldNames "can contain boosting indication e.g.
 * title^10" (the same sentence as on KNearestNeighborClassifier, which parses it). KNearestFuzzyClassifier passes
 * the name unchanged to NearestFuzzyQuery.addTerms, so "text^2" is looked up as a field literally named "text^2",
 * the query matches nothing and assignClass returns null.
 *
 * <p>Oracle: with a single text field a uniform boost cannot change which documents are nearest, so the class must
 * equal the one assigned without the boost (as KNearestNeighborClassifier does for the same input).
 */
public class TestKNearestFuzzyClassifierFieldBoost extends LuceneTestCase {

  public void testFieldBoostSyntax() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        String[][] docs = {
          {"apple banana cherry", "fruit"},
          {"apple pear", "fruit"},
          {"car truck bus", "vehicle"},
          {"bus train", "vehicle"}
        };
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new TextField("text", d[0], Field.Store.YES));
          doc.add(new StringField("cls", d[1], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // the sibling classifier with the same documented javadoc parses the boost
        ClassificationResult<BytesRef> knnBoosted =
            new KNearestNeighborClassifier(reader, null, analyzer, null, 3, 1, 1, "cls", "text^2")
                .assignClass("apple banana");
        assertNotNull(knnBoosted);
        assertEquals("fruit", knnBoosted.assignedClass().utf8ToString());

        ClassificationResult<BytesRef> plain =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, "cls", "text")
                .assignClass("apple banana");
        assertNotNull(plain);
        assertEquals("fruit", plain.assignedClass().utf8ToString());
        ClassificationResult<BytesRef> boosted =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, "cls", "text^2")
                .assignClass("apple banana");
        assertNotNull("documented field^boost syntax: no class assigned", boosted);
        assertEquals(plain.assignedClass(), boosted.assignedClass());
      }
    }
  }
}

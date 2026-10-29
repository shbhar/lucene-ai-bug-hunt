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

import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNearestFuzzyQueryDeletedDocs extends LuceneTestCase {

  public void testDeletedDocsNotMergedAway() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 5; i++) {
        w.addDocument(
            List.of(
                new StringField("id", i == 0 ? "live" : "deleted", Field.Store.NO),
                new TextField("text", "apple", Field.Store.NO)));
      }
      w.deleteDocuments(new Term("id", "deleted"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        NearestFuzzyQuery query = new NearestFuzzyQuery(new StandardAnalyzer());
        query.addTerms("apple", "text");
        try {
          assertEquals(1, new IndexSearcher(reader).count(query));
        } catch (IllegalArgumentException e) {
          fail(
              "expected: 1 hit (numDocs=" + reader.numDocs() + ", docFreq(apple)="
                  + reader.docFreq(new Term("text", "apple")) + "); actual: " + e.getMessage());
        }
      }
    }
  }
}

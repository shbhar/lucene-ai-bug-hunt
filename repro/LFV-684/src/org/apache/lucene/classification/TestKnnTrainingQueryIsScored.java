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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestKnnTrainingQueryIsScored extends LuceneTestCase {

  public void testMatchAllTrainingQueryChangesAssignedClass() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String[] d : new String[][] {{"aa bb", "Y"}, {"aa", "X"}, {"aa", "X"}}) {
          Document doc = new Document();
          doc.add(new TextField("text", d[0], Field.Store.NO));
          doc.add(new StringField("cls", d[1], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // MatchAllDocsQuery filters out nothing, so it must give the null-query class Y; it gives X
        assertEquals(
            assign(reader, analyzer, null), assign(reader, analyzer, MatchAllDocsQuery.INSTANCE));
      }
    }
  }

  private static String assign(DirectoryReader reader, Analyzer analyzer, Query trainingQuery)
      throws Exception {
    return new KNearestNeighborClassifier(
            reader, null, analyzer, trainingQuery, 3, 1, 1, "cls", "text")
        .assignClass("aa bb bb")
        .assignedClass()
        .utf8ToString();
  }
}

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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPatienceSeededQueryUnchangedBySearch extends LuceneTestCase {

  private static Query newQuery() {
    KnnFloatVectorQuery knn = new KnnFloatVectorQuery("v", new float[] {1, 1}, 1);
    TermQuery seed = new TermQuery(new Term("id", "0"));
    return PatienceKnnVectorQuery.fromSeededQuery(SeededKnnVectorQuery.fromFloatQuery(knn, seed));
  }

  public void testSearchDoesNotModifyQuery() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("id", "0", Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", new float[] {1, 1}));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query = newQuery();
        new IndexSearcher(reader).search(query, 1);
        // expected: still equal to an unsearched twin; actual: its delegate now holds a seedWeight
        assertEquals(newQuery(), query);
      }
    }
  }
}

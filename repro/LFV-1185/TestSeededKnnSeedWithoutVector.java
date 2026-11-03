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
import org.apache.lucene.tests.util.TestUtil;

public class TestSeededKnnSeedWithoutVector extends LuceneTestCase {

  public void testSeedHitWithoutVector() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < 2000; i++) {
          Document doc = new Document();
          if (i % 2 == 0) {
            doc.add(new KnnFloatVectorField("v", new float[] {i, 1f}));
          }
          if (i == 1997 || i == 1998) {
            doc.add(new StringField("seed", "x", Field.Store.NO));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query seed = new TermQuery(new Term("seed", "x"));
        Weight seedWeight = searcher.createWeight(seed, ScoreMode.TOP_SCORES, 1f);
        KnnFloatVectorQuery knn = new KnnFloatVectorQuery("v", new float[] {5f, 1f}, 2);
        // expected 2 hits (as fromFloatQuery returns); actual IllegalArgumentException
        TopDocs hits = searcher.search(new SeededKnnVectorQuery(knn, seed, seedWeight), 2);
        assertEquals(2, hits.scoreDocs.length);
      }
    }
  }
}

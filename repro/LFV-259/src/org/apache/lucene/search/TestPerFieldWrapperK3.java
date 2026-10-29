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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPerFieldWrapperK3 extends LuceneTestCase {

  public void testWrappedBM25AppliesK3() throws Exception {
    BM25Similarity bm25 = new BM25Similarity(1.2f, 0.75f, true, 7f);
    Similarity perField =
        new PerFieldSimilarityWrapper() {
          @Override
          public Similarity get(String name) {
            return bm25;
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(bm25))) {
        Document doc = new Document();
        doc.add(new TextField("body", "apple", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "apple")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "apple")), BooleanClause.Occur.SHOULD)
                .build();
        IndexSearcher direct = new IndexSearcher(reader);
        direct.setSimilarity(bm25);
        IndexSearcher wrapped = new IndexSearcher(reader);
        wrapped.setSimilarity(perField);
        // expected the bare BM25(k3=7) score (boost 1.78 for "apple apple"); actual: linear boost 2
        assertEquals(
            direct.search(query, 1).scoreDocs[0].score,
            wrapped.search(query, 1).scoreDocs[0].score,
            0f);
      }
    }
  }
}

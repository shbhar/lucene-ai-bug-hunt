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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BM25Similarity's k3 (query-term frequency saturation) is applied by BooleanQuery's
 * duplicate-clause rewrite through Similarity#computeQueryTermWeight, which takes no field.
 * PerFieldSimilarityWrapper delegates computeNorm and scorer to the per-field similarity but not
 * computeQueryTermWeight, so a configured k3 is silently ignored as soon as the similarity is
 * wrapped per field, even when every field maps to the same BM25Similarity instance.
 */
public class TestPerFieldSimilarityWrapperK3 extends LuceneTestCase {

  public void testWrappedBM25AppliesK3LikeUnwrapped() throws IOException {
    final BM25Similarity bm25 = new BM25Similarity(1.2f, 0.75f, true, 7f);
    Similarity perField =
        new PerFieldSimilarityWrapper() {
          @Override
          public Similarity get(String name) {
            return bm25; // every field uses the same BM25(k3 = 7)
          }
        };
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
      iwc.setSimilarity(perField);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField("body", "apple pie", Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("body", "banana split", Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        // "apple apple": the duplicate the k3 javadoc describes
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "apple")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "apple")), BooleanClause.Occur.SHOULD)
                .build();
        IndexSearcher direct = new IndexSearcher(reader);
        direct.setSimilarity(bm25);
        IndexSearcher wrapped = new IndexSearcher(reader);
        wrapped.setSimilarity(perField);
        float expected = direct.search(q, 1).scoreDocs[0].score;
        float actual = wrapped.search(q, 1).scoreDocs[0].score;
        assertEquals(
            "rewritten (direct): "
                + direct.rewrite(q)
                + " | rewritten (per-field wrapper): "
                + wrapped.rewrite(q),
            expected,
            actual,
            0f);
      }
    }
  }
}

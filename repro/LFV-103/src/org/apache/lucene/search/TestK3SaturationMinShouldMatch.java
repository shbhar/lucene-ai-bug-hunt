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
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestK3SaturationMinShouldMatch extends LuceneTestCase {

  private static Query aab(int minShouldMatch) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "a")), Occur.SHOULD)
        .add(new TermQuery(new Term("body", "a")), Occur.SHOULD)
        .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
        .setMinimumNumberShouldMatch(minShouldMatch)
        .build();
  }

  public void testDuplicateShouldTermSaturatedAtMinShouldMatch2() throws Exception {
    BM25Similarity sim = new BM25Similarity(1.2f, 0.75f, true, 8f);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        Document doc = new Document();
        doc.add(new TextField("body", "a b", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setSimilarity(sim);
        float msm1 = s.search(aab(1), 1).scoreDocs[0].score;
        float msm2 = s.search(aab(2), 1).scoreDocs[0].score;
        // expected: k3 saturates the duplicate "a" at msm=2 as at msm=1; actual: linear, higher
        assertEquals("score of 'a b' at msm=2 vs msm=1", msm1, msm2, 1e-5f);
      }
    }
  }
}

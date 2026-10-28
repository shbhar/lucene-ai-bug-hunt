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
package org.apache.lucene.search.similarities;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Probe: the same mu (2000) gives a different top-1 document through IndexSearcher#search
 * depending on which constructor built the similarity. Corpus: |C| = 35 tokens; term t has cf = 3,
 * so p(t|C) = 3/35 (IndriCollectionModel) vs 4/36 (DefaultCollectionModel), and the crossover of
 * doc 0 (tf 1, length 1) and doc 1 (tf 2, length 11) lies between the two.
 */
public class TestIndriMuCtorRankingV3 extends LuceneTestCase {

  private static String filler(String prefix, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(' ').append(prefix).append(i);
    }
    return sb.toString();
  }

  private static int top1(Directory dir, Similarity sim, Query q, StringBuilder log)
      throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = newSearcher(reader, false, false, false);
      searcher.setSimilarity(sim);
      TopDocs td = searcher.search(q, 3);
      log.append(sim.toString()).append(" (").append(sim.getClass().getSimpleName()).append("): ");
      for (ScoreDoc sd : td.scoreDocs) {
        log.append("doc").append(sd.doc).append('=').append(sd.score).append(' ');
      }
      log.append('\n');
      assertTrue("expected hits under " + sim, td.scoreDocs.length >= 2);
      return td.scoreDocs[0].doc;
    }
  }

  public void testSameMuSameRanking() throws IOException {
    Similarity byDefault = new IndriDirichletSimilarity();
    Similarity explicitMu = new IndriDirichletSimilarity(2000f);
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSimilarity(byDefault).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        String[] docs = {"t", "t t" + filler("a", 9), "z" + filler("b", 22)};
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("body", "t")), BooleanClause.Occur.SHOULD)
              .add(new TermQuery(new Term("body", "z")), BooleanClause.Occur.SHOULD)
              .build();
      StringBuilder log = new StringBuilder();
      int a = top1(dir, byDefault, q, log);
      int b = top1(dir, explicitMu, q, log);
      System.out.println(log);
      assertEquals(
          "top-1 of " + q + " with IndriDirichletSimilarity() vs IndriDirichletSimilarity(2000f)\n"
              + log,
          a,
          b);
    }
  }
}

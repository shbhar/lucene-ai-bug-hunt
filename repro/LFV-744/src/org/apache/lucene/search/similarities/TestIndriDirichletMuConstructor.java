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
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndriDirichletSimilarity() uses IndriCollectionModel, but IndriDirichletSimilarity(float mu)
 * silently uses LMSimilarity's DefaultCollectionModel, so the two score differently with the same
 * mu although the no-argument constructor is documented as "with the default mu value of 2000".
 */
public class TestIndriDirichletMuConstructor extends LuceneTestCase {

  private static final String[] DOCS = {
    "president washington was the first leader",
    "the president is head of the executive branch",
    "george washington was a general",
    "a company can have a president"
  };

  /** Scores of every matching document, through Weight#scorer (COMPLETE). */
  private static Map<Integer, Float> scores(Directory dir, Similarity sim, Query q)
      throws IOException {
    Map<Integer, Float> out = new HashMap<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      Weight w = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE, 1f);
      for (LeafReaderContext ctx : reader.leaves()) {
        Scorer s = w.scorer(ctx);
        if (s == null) {
          continue;
        }
        DocIdSetIterator it = s.iterator();
        for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
          out.put(ctx.docBase + d, s.score());
        }
      }
    }
    return out;
  }

  private static Directory index(Similarity sim) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setSimilarity(sim).setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String text : DOCS) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** "Instantiates the similarity with the default mu value of 2000." */
  public void testExplicitDefaultMuScoresLikeDefaultConstructor() throws IOException {
    Similarity byDefault = new IndriDirichletSimilarity();
    Similarity explicitMu = new IndriDirichletSimilarity(2000f);
    assertEquals(byDefault.toString(), explicitMu.toString()); // both "IndriDirichlet(2000.000000)"
    try (Directory dir = index(byDefault)) {
      for (String term : new String[] {"president", "washington", "george"}) {
        Query q = new TermQuery(new Term("body", term));
        Map<Integer, Float> a = scores(dir, byDefault, q);
        Map<Integer, Float> b = scores(dir, explicitMu, q);
        assertEquals("matches of " + q, a.keySet(), b.keySet());
        for (Integer doc : a.keySet()) {
          assertEquals(
              q + " doc " + doc + ": IndriDirichletSimilarity() vs IndriDirichletSimilarity(2000f)",
              a.get(doc),
              b.get(doc),
              0f);
        }
      }
    }
  }

  /**
   * Indri's Dirichlet estimate log((tf + mu * cf/|C|) / (|D| + mu)), with p(w|C) = cf/|C| as
   * IndriCollectionModel computes it, for a mu other than the default.
   */
  public void testCustomMuUsesIndriCollectionModel() throws IOException {
    float mu = 3f;
    Similarity sim = new IndriDirichletSimilarity(mu);
    try (Directory dir = index(sim)) {
      Query q = new TermQuery(new Term("body", "george"));
      Map<Integer, Float> got = scores(dir, sim, q);
      assertEquals(1, got.size());
      // doc 2: "george washington was a general": tf=1, |D|=5; cf("george")=1, |C|=25 tokens
      int sumTotalTermFreq = 0;
      for (String text : DOCS) {
        sumTotalTermFreq += text.split(" ").length;
      }
      double expected = Math.log((1 + mu * (1.0 / sumTotalTermFreq)) / (5 + mu));
      assertEquals("score of doc 2", (float) expected, got.get(2), 1e-6f);
    }
  }
}

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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A SynonymQuery that holds the same term twice must still pass the similarity statistics within
 * the field's totals: TermStats documents {@code totalTermFreq <= sumTotalTermFreq of the field} as
 * always true, and SimilarityBase asserts it.
 */
public class TestSynonymQueryRepeatedTermStats extends LuceneTestCase {

  /** Records the statistics a weight passes to the similarity, then delegates to BM25. */
  private static final class RecordingSimilarity extends Similarity {
    private final Similarity in = new BM25Similarity();
    final List<TermStats> seen = new ArrayList<>();
    FieldStats field;

    @Override
    public SimScorer scorer(float boost, FieldStats fieldStats, TermStats... termStats) {
      field = fieldStats;
      seen.addAll(List.of(termStats));
      return in.scorer(boost, fieldStats, termStats);
    }
  }

  /** Field "f" holds 4 tokens: doc 0 "a", doc 1 "a a b". Term "a" occurs 3 times. */
  private static Directory index() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String text : new String[] {"a", "a a b"}) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static TermStats statsFor(IndexSearcher searcher, RecordingSimilarity sim, Query q)
      throws IOException {
    sim.seen.clear();
    searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE, 1f);
    assertEquals(1, sim.seen.size());
    return sim.seen.get(0);
  }

  private static Query synonyms(String... terms) {
    SynonymQuery.Builder builder = new SynonymQuery.Builder("f");
    for (String t : terms) {
      builder.addTerm(new Term("f", t));
    }
    return builder.build();
  }

  public void testRepeatedTermStatsStayWithinField() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      RecordingSimilarity sim = new RecordingSimilarity();
      searcher.setSimilarity(sim);

      // controls: one term, and two different terms
      TermStats term = statsFor(searcher, sim, new TermQuery(new Term("f", "a")));
      assertEquals(4, sim.field.sumTotalTermFreq());
      assertEquals(2, term.docFreq());
      assertEquals(3, term.totalTermFreq());
      TermStats distinct = statsFor(searcher, sim, synonyms("a", "b"));
      assertEquals(2, distinct.docFreq());
      assertEquals(4, distinct.totalTermFreq());

      // the same term twice
      Query repeated = synonyms("a", "a");
      TermStats stats = statsFor(searcher, sim, repeated);
      assertTrue(
          repeated
              + ": docFreq="
              + stats.docFreq()
              + " totalTermFreq="
              + stats.totalTermFreq()
              + " exceeds the field's sumTotalTermFreq="
              + sim.field.sumTotalTermFreq(),
          stats.totalTermFreq() <= sim.field.sumTotalTermFreq());
    }
  }

  public void testRepeatedTermLMDirichlet() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(new LMDirichletSimilarity());

      // control: TermQuery(a) uses P(a|C) = (3 + 1) / (4 + 1) = 0.8
      float control = collectionProbability(searcher.explain(new TermQuery(new Term("f", "a")), 0));
      assertEquals(0.8f, control, 1e-6f);

      // with assertions enabled, SimilarityBase.fillBasicStats throws here
      Query repeated = synonyms("a", "a");
      float p = collectionProbability(searcher.explain(repeated, 0));
      assertTrue(repeated + ": collection probability P(t|C) = " + p + " > 1", p <= 1f);
    }
  }

  private static float collectionProbability(Explanation e) {
    if (e.getDescription().startsWith("P, probability that the current term is generated")) {
      return e.getValue().floatValue();
    }
    for (Explanation detail : e.getDetails()) {
      float p = collectionProbability(detail);
      if (Float.isNaN(p) == false) {
        return p;
      }
    }
    return Float.NaN;
  }
}

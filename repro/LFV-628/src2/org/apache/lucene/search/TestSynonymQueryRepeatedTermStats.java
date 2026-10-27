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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * SynonymQuery scores its terms "as if you had indexed them as one term". When the same term is
 * added twice, SynonymWeight takes the max of the docFreqs (one term) but adds the totalTermFreq
 * once per copy, so the pseudo-term has more occurrences than the whole field. SimilarityBase
 * asserts that this cannot happen (termStats.totalTermFreq() <= fieldStats.sumTotalTermFreq()).
 *
 * <p>Release variant: identical to the main repro except that the scorer() signature uses the
 * pre-rename CollectionStatistics/TermStatistics classes.
 */
public class TestSynonymQueryRepeatedTermStats extends LuceneTestCase {

  /** Delegating similarity that checks the invariant SimilarityBase asserts, also with -da. */
  private static final class StatsCheckingSimilarity extends Similarity {
    private final Similarity in = new LMDirichletSimilarity();

    @Override
    public long computeNorm(org.apache.lucene.index.FieldInvertState state) {
      return in.computeNorm(state);
    }

    @Override
    public SimScorer scorer(
        float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
      for (TermStatistics ts : termStats) {
        assertTrue(
            "pseudo-term totalTermFreq="
                + ts.totalTermFreq()
                + " exceeds the field's sumTotalTermFreq="
                + collectionStats.sumTotalTermFreq(),
            ts.totalTermFreq() <= collectionStats.sumTotalTermFreq());
      }
      return in.scorer(boost, collectionStats, termStats);
    }
  }

  private void search(Analyzer analyzer, Similarity sim, Query query, String... docs)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer)) {
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
        try (IndexReader reader = w.getReader()) {
          IndexSearcher searcher = newSearcher(reader);
          searcher.setSimilarity(sim);
          searcher.search(query, 10);
        }
      }
    }
  }

  /** The same term added twice through the public builder. */
  public void testRepeatedTerm() throws Exception {
    Term a = new Term("f", "a");
    Query q = new SynonymQuery.Builder("f").addTerm(a).addTerm(a).build();
    // field: 4 tokens, 3 of them 'a'; the query reports 6 occurrences of its pseudo-term
    search(new WhitespaceAnalyzer(), new StatsCheckingSimilarity(), q, "a", "a a b");
  }

  /** The same with a stock similarity: with -ea SimilarityBase.fillBasicStats trips. */
  public void testRepeatedTermLMDirichlet() throws Exception {
    Term a = new Term("f", "a");
    Query q = new SynonymQuery.Builder("f").addTerm(a).addTerm(a).build();
    search(new WhitespaceAnalyzer(), new LMDirichletSimilarity(), q, "a", "a a b");
  }

  /**
   * QueryBuilder produces such a query from a synonym rule whose entries coincide after the rule
   * analyzer: "Dog, dog, puppy" parsed with lower-casing gives SynonymQuery(dog, dog, puppy).
   */
  public void testQueryBuilderWithCollapsingSynonymRule() throws Exception {
    SolrSynonymParser parser =
        new SolrSynonymParser(
            true,
            true,
            new Analyzer() {
              @Override
              protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer t = new WhitespaceTokenizer();
                return new TokenStreamComponents(t, new LowerCaseFilter(t));
              }
            });
    parser.parse(new StringReader("Dog, dog, puppy\n"));
    SynonymMap map = parser.build();
    Analyzer queryAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new SynonymGraphFilter(new LowerCaseFilter(t), map, true));
          }
        };
    Query q = new QueryBuilder(queryAnalyzer).createBooleanQuery("f", "dog");
    search(new WhitespaceAnalyzer(), new StatsCheckingSimilarity(), q, "dog", "dog dog cat");
  }
}

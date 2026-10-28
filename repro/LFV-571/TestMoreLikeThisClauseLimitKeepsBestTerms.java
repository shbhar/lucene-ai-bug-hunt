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
package org.apache.lucene.queries.mlt;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MoreLikeThis keeps the {@code maxQueryTerms} best terms in a bounded priority queue, then builds the
 * query by popping that queue. The queue pops the LOWEST score first, and createQuery stops at the first
 * {@link IndexSearcher.TooManyClauses}. So when more terms are selected than {@link
 * IndexSearcher#getMaxClauseCount()} allows, the query keeps the lowest-scoring terms and silently
 * drops the most interesting ones.
 */
public class TestMoreLikeThisClauseLimitKeepsBestTerms extends LuceneTestCase {

  private static final int NUM_WORDS = 1100; // more than the default clause limit of 1024
  private static final int NUM_BEST = 76; // words that occur twice in the source text

  /** The term texts of a query (QueryVisitor, so the test compiles on 9.x, 10.x and main). */
  private static Set<String> termsOf(Query q) {
    Set<String> out = new HashSet<>();
    q.visit(
        new QueryVisitor() {
          @Override
          public void consumeTerms(Query query, Term... terms) {
            for (Term t : terms) {
              out.add(t.text());
            }
          }

          @Override
          public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
            return this;
          }
        });
    return out;
  }

  private static String word(int i) {
    return "w" + i;
  }

  /** Default clause limit (1024), maxQueryTerms larger than it. */
  public void testDefaultClauseLimitKeepsBestTerms() throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    StringBuilder all = new StringBuilder();
    for (int i = 0; i < NUM_WORDS; i++) {
      all.append(word(i)).append(' ');
    }
    Document d0 = new Document();
    d0.add(newTextField("text", all.toString(), Field.Store.NO));
    w.addDocument(d0);
    Document d1 = new Document();
    d1.add(newTextField("text", "unrelated", Field.Store.NO));
    w.addDocument(d1);
    IndexReader reader = w.getReader();
    w.close();

    // Source text: every word once, the first NUM_BEST words twice. All words have docFreq 1,
    // so score = tf * idf is twice as high for the NUM_BEST "best" words.
    StringBuilder like = new StringBuilder(all);
    Set<String> best = new HashSet<>();
    for (int i = 0; i < NUM_BEST; i++) {
      like.append(word(i)).append(' ');
      best.add(word(i));
    }

    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setMaxQueryTerms(2000); // all 1100 words are selected, more than the 1024-clause limit

    BooleanQuery q = (BooleanQuery) mlt.like("text", new StringReader(like.toString()));
    assertEquals(1024, q.clauses().size()); // truncated to the clause limit, as intended

    int bestKept = 0;
    for (String t : termsOf(q)) {
      if (best.contains(t)) {
        bestKept++;
      }
    }
    // The query has room for 1024 of the 1100 selected terms: the 76 highest-scoring ones must be in it.
    assertEquals(
        "the highest-scoring terms must survive the clause-limit truncation", NUM_BEST, bestKept);

    reader.close();
    dir.close();
    analyzer.close();
  }

  /** A lowered clause limit with the default maxQueryTerms (25). */
  public void testLoweredClauseLimitKeepsBestTerms() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    Document d0 = new Document();
    d0.add(newTextField("text", "alpha beta gamma delta", Field.Store.NO));
    w.addDocument(d0);
    IndexReader reader = w.getReader();
    w.close();

    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    // "alpha" (tf 4) is the most interesting word, "delta" (tf 1) the least.
    String like = "alpha alpha alpha alpha beta beta beta gamma gamma delta";

    int saved = IndexSearcher.getMaxClauseCount();
    BooleanQuery q;
    BooleanQuery control;
    try {
      IndexSearcher.setMaxClauseCount(2);
      // control: maxQueryTerms within the clause limit keeps alpha and beta
      mlt.setMaxQueryTerms(2);
      control = (BooleanQuery) mlt.like("text", new StringReader(like));
      // default maxQueryTerms (25): 4 selected terms, 2 fit under the clause limit
      mlt.setMaxQueryTerms(MoreLikeThis.DEFAULT_MAX_QUERY_TERMS);
      q = (BooleanQuery) mlt.like("text", new StringReader(like));
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
    assertEquals(Set.of("alpha", "beta"), termsOf(control));
    assertEquals(Set.of("alpha", "beta"), termsOf(q));

    reader.close();
    dir.close();
    analyzer.close();
  }
}

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
package org.apache.lucene.queries.mltverify;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, own package): when the selected terms do not all fit under
 * IndexSearcher.getMaxClauseCount(), MoreLikeThis should keep the highest-scoring ones.
 *
 * <p>Corpus: every indexed document contains every word once, so every word has the same docFreq
 * and the same idf; a word's MoreLikeThis score is then proportional to its frequency in the
 * like-text, which the test controls.
 */
public class TestMltClauseLimitV1 extends LuceneTestCase {

  private static final String FIELD = "body";

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;

  private void index(String[] words) throws Exception {
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      String all = String.join(" ", words);
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, all, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    if (analyzer != null) {
      analyzer.close();
    }
    super.tearDown();
  }

  private MoreLikeThis newMlt(int maxQueryTerms) throws Exception {
    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {FIELD});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setMaxQueryTerms(maxQueryTerms);
    return mlt;
  }

  private static Set<String> queryWords(Query q) {
    // QueryVisitor.termCollector works on every line tested (BooleanClause accessors differ).
    assertTrue(q instanceof BooleanQuery);
    Set<Term> terms = new HashSet<>();
    q.visit(QueryVisitor.termCollector(terms));
    assertEquals("one clause per term", ((BooleanQuery) q).clauses().size(), terms.size());
    Set<String> out = new TreeSet<>();
    for (Term t : terms) {
      assertEquals(FIELD, t.field());
      out.add(t.text());
    }
    return out;
  }

  /**
   * Default clause limit (1024), no global state changed. 1100 eligible words; the 50 "top" words
   * occur 3 times in the like-text, the other 1050 once. maxQueryTerms = 1100 (a legal setter
   * value). Expected: the 1024-clause query contains all 50 top words.
   */
  public void testDefaultClauseLimitKeepsTopWords() throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    final int nWords = 1100;
    final int nTop = 50;
    String[] words = new String[nWords];
    for (int i = 0; i < nWords; i++) {
      words[i] = String.format(java.util.Locale.ROOT, "w%04d", i);
    }
    index(words);

    StringBuilder like = new StringBuilder();
    Set<String> top = new TreeSet<>();
    for (int i = 0; i < nWords; i++) {
      int tf = i < nTop ? 3 : 1;
      if (i < nTop) {
        top.add(words[i]);
      }
      for (int k = 0; k < tf; k++) {
        like.append(words[i]).append(' ');
      }
    }

    // Control: maxQueryTerms == clause limit -> no truncation inside createQuery.
    Set<String> control =
        queryWords(newMlt(1024).like(FIELD, new StringReader(like.toString())));
    assertEquals(1024, control.size());
    assertTrue("control (maxQueryTerms=1024) keeps all top words", control.containsAll(top));

    // Raising maxQueryTerms past the clause limit should not remove the most interesting words.
    Set<String> got = queryWords(newMlt(nWords).like(FIELD, new StringReader(like.toString())));
    assertEquals(1024, got.size());
    Set<String> keptTop = new TreeSet<>(top);
    keptTop.retainAll(got);
    assertEquals(
        "top-scoring words kept in the truncated query (maxQueryTerms=" + nWords + ")",
        nTop,
        keptTop.size());
  }

  /**
   * Lowered global clause limit (3) with the default maxQueryTerms (25). Words a..f occur 6..1
   * times. Expected query words {a, b, c}.
   */
  public void testLoweredClauseLimitKeepsTopWords() throws Exception {
    index(new String[] {"a", "b", "c", "d", "e", "f"});
    String like = "a a a a a a b b b b b c c c c d d d e e f";
    final int saved = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(3);
    try {
      Set<String> control = queryWords(newMlt(3).like(FIELD, new StringReader(like)));
      assertEquals("control (maxQueryTerms=3)", Set.of("a", "b", "c"), control);

      Set<String> got =
          queryWords(
              newMlt(MoreLikeThis.DEFAULT_MAX_QUERY_TERMS).like(FIELD, new StringReader(like)));
      assertEquals(
          "maxQueryTerms=" + MoreLikeThis.DEFAULT_MAX_QUERY_TERMS + ", clause limit 3",
          new TreeSet<>(Set.of("a", "b", "c")),
          got);
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }
}

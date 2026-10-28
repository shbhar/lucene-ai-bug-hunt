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

import java.io.IOException;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When MoreLikeThis selects more terms than {@link IndexSearcher#getMaxClauseCount()} allows, it
 * truncates the query to the clause limit instead of failing. The terms it keeps should be the
 * highest-scoring ones.
 *
 * <p>Every document holds every word once, so all words have the same docFreq and idf, and a word's
 * score is set by how often it occurs in the like-text.
 */
public class TestMoreLikeThisClauseLimit extends LuceneTestCase {

  private static final String FIELD = "body";
  private static final int NUM_WORDS = 1100; // more than the default clause limit of 1024
  private static final int NUM_TOP = 76; // words that occur twice in the like-text, the rest once

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;

  private void index(String[] words) throws Exception {
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, String.join(" ", words), Field.Store.NO));
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

  private MoreLikeThis newMlt(int maxQueryTerms) throws IOException {
    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {FIELD});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setMaxQueryTerms(maxQueryTerms);
    return mlt;
  }

  private static Set<String> queryWords(Query q) {
    Set<Term> terms = new HashSet<>();
    q.visit(QueryVisitor.termCollector(terms));
    Set<String> words = new TreeSet<>();
    for (Term t : terms) {
      words.add(t.text());
    }
    return words;
  }

  /** 1100 words, the 76 top words twice each: returns the like-text and fills {@code top}. */
  private String indexWordsAndBuildLikeText(Set<String> top) throws Exception {
    String[] words = new String[NUM_WORDS];
    StringBuilder like = new StringBuilder();
    for (int i = 0; i < NUM_WORDS; i++) {
      words[i] = "w" + i;
      int tf = i < NUM_TOP ? 2 : 1;
      if (i < NUM_TOP) {
        top.add(words[i]);
      }
      for (int k = 0; k < tf; k++) {
        like.append(words[i]).append(' ');
      }
    }
    index(words);
    return like.toString();
  }

  private static int countTop(Set<String> queryWords, Set<String> top) {
    Set<String> kept = new HashSet<>(top);
    kept.retainAll(queryWords);
    return kept.size();
  }

  /** Default clause limit (1024); only maxQueryTerms is raised above it. */
  public void testDefaultClauseLimitKeepsTopTerms() throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    Set<String> top = new HashSet<>();
    String like = indexWordsAndBuildLikeText(top);

    // control: maxQueryTerms equal to the clause limit, nothing is truncated in createQuery
    Set<String> control = queryWords(newMlt(1024).like(FIELD, new StringReader(like)));
    assertEquals(1024, control.size());
    assertEquals("top words kept, maxQueryTerms=1024", NUM_TOP, countTop(control, top));

    // maxQueryTerms above the clause limit: all 1100 words are selected, 1024 fit in the query,
    // so the 76 lowest-scoring words should be left out
    Set<String> got = queryWords(newMlt(NUM_WORDS).like(FIELD, new StringReader(like)));
    assertEquals(1024, got.size());
    assertEquals("top words kept, maxQueryTerms=" + NUM_WORDS, NUM_TOP, countTop(got, top));
  }

  /** The same through MoreLikeThisQuery, rewritten and run by IndexSearcher. */
  public void testMoreLikeThisQueryKeepsTopTerms() throws Exception {
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    Set<String> top = new HashSet<>();
    String like = indexWordsAndBuildLikeText(top);

    MoreLikeThisQuery mltq = new MoreLikeThisQuery(like, new String[] {FIELD}, analyzer, FIELD);
    mltq.setMinDocFreq(1);
    mltq.setMaxQueryTerms(NUM_WORDS);
    IndexSearcher searcher = newSearcher(reader);
    searcher.search(mltq, 10); // no exception
    Set<String> got = queryWords(searcher.rewrite(mltq));
    assertEquals(1024, got.size());
    assertEquals("top words kept, MoreLikeThisQuery", NUM_TOP, countTop(got, top));
  }

  /** A lowered global clause limit (3) with the default maxQueryTerms (25). */
  public void testLoweredClauseLimitKeepsTopTerms() throws Exception {
    index(new String[] {"a", "b", "c", "d", "e", "f"});
    String like = "a a a a a a b b b b b c c c c d d d e e f"; // tf 6, 5, 4, 3, 2, 1
    int saved = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(3);
    try {
      // control: maxQueryTerms equal to the clause limit
      assertEquals(
          Set.of("a", "b", "c"), queryWords(newMlt(3).like(FIELD, new StringReader(like))));
      Set<String> got =
          queryWords(
              newMlt(MoreLikeThis.DEFAULT_MAX_QUERY_TERMS).like(FIELD, new StringReader(like)));
      assertEquals(new TreeSet<>(Set.of("a", "b", "c")), got);
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }
}

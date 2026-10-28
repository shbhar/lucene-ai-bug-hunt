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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.CorePlusQueriesParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Reachability probe: the clause-limit truncation in MoreLikeThis.createQuery is reached with the
 * default clause limit (1024) through the public MoreLikeThisQuery wrapper (rewritten by
 * IndexSearcher) and through the XML query parser's LikeThisQuery element, whose maxQueryTerms
 * attribute comes from the query text. Each test asserts that the best-scoring terms survive.
 */
public class TestMltClauseLimitReachability extends LuceneTestCase {

  private static final int NUM_WORDS = 1100;
  private static final int NUM_BEST = 76;

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

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;
  private String likeText;
  private Set<String> best;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assertEquals(1024, IndexSearcher.getMaxClauseCount());
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    StringBuilder all = new StringBuilder();
    for (int i = 0; i < NUM_WORDS; i++) {
      all.append('w').append(i).append(' ');
    }
    Document d0 = new Document();
    d0.add(newTextField("text", all.toString(), Field.Store.NO));
    w.addDocument(d0);
    Document d1 = new Document();
    d1.add(newTextField("text", "unrelated", Field.Store.NO));
    w.addDocument(d1);
    reader = w.getReader();
    w.close();
    StringBuilder like = new StringBuilder(all);
    best = new HashSet<>();
    for (int i = 0; i < NUM_BEST; i++) {
      like.append('w').append(i).append(' ');
      best.add("w" + i);
    }
    likeText = like.toString();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private int bestKept(Query rewritten) {
    int n = 0;
    for (String t : termsOf(rewritten)) {
      if (best.contains(t)) {
        n++;
      }
    }
    return n;
  }

  /** Public wrapper, rewritten and executed by IndexSearcher (no exception is raised). */
  public void testMoreLikeThisQueryWrapper() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    MoreLikeThisQuery q = new MoreLikeThisQuery(likeText, new String[] {"text"}, analyzer, "text");
    q.setMaxQueryTerms(2000);
    q.setMinDocFreq(1);
    Query rewritten = searcher.rewrite(q);
    assertEquals(1024, termsOf(rewritten).size());
    searcher.search(q, 10); // runs without TooManyClauses / TooManyNestedClauses
    assertEquals("best terms kept (MoreLikeThisQuery)", NUM_BEST, bestKept(rewritten));
  }

  /** XML query parser: maxQueryTerms is an attribute of the query text. */
  public void testXmlQueryParserLikeThisQuery() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    String xml =
        "<LikeThisQuery fieldNames=\"text\" maxQueryTerms=\"2000\" minDocFreq=\"1\">"
            + likeText
            + "</LikeThisQuery>";
    Query parsed =
        new CorePlusQueriesParser("text", analyzer)
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Query rewritten = searcher.rewrite(parsed);
    assertEquals(1024, termsOf(rewritten).size());
    searcher.search(parsed, 10);
    assertEquals("best terms kept (XML LikeThisQuery)", NUM_BEST, bestKept(rewritten));
  }
}

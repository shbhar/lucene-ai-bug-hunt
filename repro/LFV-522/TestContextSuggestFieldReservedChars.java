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
package org.apache.lucene.search.suggest.document;

import static org.apache.lucene.search.suggest.document.TestSuggestField.iwcWithSuggestField;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.hamcrest.MatcherAssert;

/**
 * The ContextSuggestField constructor documents "@throws IllegalArgumentException ... if value or
 * contexts contains any reserved characters", and SuggestField rejects U+001E and U+001F in the
 * value. A context is only checked for U+001D, so a context containing U+001E or U+001F is accepted
 * and then rewritten by the completion analysis: U+001E is dropped, U+001F is doubled (or dropped
 * with preserveSep=false).
 */
public class TestContextSuggestFieldReservedChars extends LuceneTestCase {

  private static final String FIELD = "suggest_field";
  private static final String HOLE_CTX = "a\u001Eb"; // CompletionAnalyzer.HOLE_CHARACTER
  private static final String SEP_CTX = "a\u001Fb"; // ConcatenateGraphFilter.SEP_LABEL
  private static final String REJECTED = "rejected at addDocument";

  public void testReservedCharsInContextAreRejected() throws Exception {
    // control: the same two characters are rejected in the value
    expectThrows(
        IllegalArgumentException.class, () -> new ContextSuggestField(FIELD, HOLE_CTX, 1, "c"));
    expectThrows(
        IllegalArgumentException.class, () -> new ContextSuggestField(FIELD, SEP_CTX, 1, "c"));
    // controls: an ordinary context is accepted, CONTEXT_SEPARATOR U+001D in a context is rejected
    assertEquals("ab: accepted", "ab: " + index("ab"));
    assertEquals("a<1D>b: " + REJECTED, "a<1D>b: " + index("a\u001Db"));
    // the documented contract for U+001E and U+001F in a context
    assertEquals(
        "a<1E>b: " + REJECTED + ", a<1F>b: " + REJECTED,
        "a<1E>b: " + index(HOLE_CTX) + ", a<1F>b: " + index(SEP_CTX));
  }

  public void testHoleCharContextIsMergedIntoAnotherContext() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    // control: two ordinary contexts stay apart
    assertEquals(
        "exact ab -> [sugg0:ab]; exact axb -> [sugg1:axb]",
        suggest(analyzer, new String[] {"ab", "axb"}, "ab", "axb"));
    // a context with U+001E must be rejected (documented) or else stay apart from "ab"
    MatcherAssert.assertThat(
        suggest(analyzer, new String[] {"ab", HOLE_CTX}, "ab", HOLE_CTX),
        anyOf(
            equalTo(REJECTED), equalTo("exact ab -> [sugg0:ab]; exact a<1E>b -> [sugg1:a<1E>b]")));
  }

  public void testSepLabelContextNeverMatchesItself() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    // control: an ordinary context matches itself and is reported unchanged
    assertEquals(
        "exact ab -> [sugg0:ab]; all -> [sugg0:ab]",
        suggest(analyzer, new String[] {"ab"}, "ab", null));
    // a context with U+001F must be rejected (documented) or else match itself unchanged
    MatcherAssert.assertThat(
        suggest(analyzer, new String[] {SEP_CTX}, SEP_CTX, null),
        anyOf(equalTo(REJECTED), equalTo("exact a<1F>b -> [sugg0:a<1F>b]; all -> [sugg0:a<1F>b]")));
  }

  public void testSepLabelContextIsMergedWithoutPreserveSep() throws Exception {
    Analyzer analyzer = new CompletionAnalyzer(new MockAnalyzer(random()), false, true);
    // control: two ordinary contexts stay apart with preserveSep=false
    assertEquals(
        "exact ab -> [sugg0:ab]; exact axb -> [sugg1:axb]",
        suggest(analyzer, new String[] {"ab", "axb"}, "ab", "axb"));
    // with preserveSep=false a context with U+001F must be rejected or else stay apart from "ab"
    MatcherAssert.assertThat(
        suggest(analyzer, new String[] {"ab", SEP_CTX}, "ab", SEP_CTX),
        anyOf(
            equalTo(REJECTED), equalTo("exact ab -> [sugg0:ab]; exact a<1F>b -> [sugg1:a<1F>b]")));
  }

  /**
   * Indexes one suggestion with one context; returns "accepted" or {@link #REJECTED}. Uses a fresh
   * analyzer each time: MockTokenizer refuses reuse after a context was rejected inside
   * tokenStream().
   */
  private String index(String context) throws Exception {
    String result = suggest(new MockAnalyzer(random()), new String[] {context});
    return result.equals(REJECTED) ? REJECTED : "accepted";
  }

  /**
   * Indexes document i with the suggestion "sugg" + i (weight 10 - i) under contexts[i], then runs
   * a prefix query "sugg" once per query context (exact match; null = all contexts) and reports the
   * hits as key:context. Returns {@link #REJECTED} if addDocument rejected a context as reserved.
   */
  private String suggest(Analyzer analyzer, String[] contexts, String... queryContexts)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter iw = new IndexWriter(dir, iwcWithSuggestField(analyzer, FIELD))) {
        for (int i = 0; i < contexts.length; i++) {
          Document document = new Document();
          document.add(new ContextSuggestField(FIELD, "sugg" + i, 10 - i, contexts[i]));
          try {
            iw.addDocument(document);
          } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("reserved character"));
            return REJECTED;
          }
        }
        iw.commit();
      }
      List<String> results = new ArrayList<>();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        for (String queryContext : queryContexts) {
          ContextQuery query =
              new ContextQuery(new PrefixCompletionQuery(analyzer, new Term(FIELD, "sugg")));
          if (queryContext == null) {
            query.addAllContexts();
          } else {
            query.addContext(queryContext);
          }
          List<String> hits = new ArrayList<>();
          for (TopSuggestDocs.SuggestScoreDoc hit :
              searcher.suggest(query, 10, false).scoreLookupDocs()) {
            hits.add(hit.key + ":" + show(hit.context));
          }
          results.add(
              (queryContext == null ? "all" : "exact " + show(queryContext)) + " -> " + hits);
        }
      }
      return String.join("; ", results);
    }
  }

  private static String show(CharSequence s) {
    return s == null
        ? "null"
        : s.toString()
            .replace("\u001D", "<1D>")
            .replace("\u001E", "<1E>")
            .replace("\u001F", "<1F>");
  }
}

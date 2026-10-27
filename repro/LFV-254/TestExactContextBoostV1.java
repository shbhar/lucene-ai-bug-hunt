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
package suggestverify;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.ContextQuery;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.apache.lucene.search.suggest.document.PrefixCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check: an exact query context (addContext(ctx, boost) / exact=true) must only boost
 * suggestions whose indexed context equals ctx. It must not boost a suggestion whose indexed
 * context merely starts with ctx and that reaches the result set through another query context (a
 * prefix context or addAllContexts).
 *
 * <p>Index (one segment): apple w=3 ctx "fruitcake"; apricot w=2 ctx "fruit"; apron w=1 ctx
 * "frugal". Score = weight * boost, or weight when no query context matched (boost 0).
 *
 * <p>Uses only public API, from a package outside Lucene.
 */
public class TestExactContextBoostV1 extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private SuggestIndexSearcher searcher;

  /** The completion postings format that matches the default postings format of this version. */
  private static PostingsFormat completionPostingsFormat() {
    String name = TestUtil.getDefaultPostingsFormat().getName(); // e.g. "Lucene104", "Lucene912"
    assertTrue(name, name.startsWith("Lucene"));
    return PostingsFormat.forName("Completion" + name.substring("Lucene".length()));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random());
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(completionPostingsFormat()));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document d = new Document();
      d.add(new ContextSuggestField(FIELD, "apple", 3, "fruitcake"));
      w.addDocument(d);
      d = new Document();
      d.add(new ContextSuggestField(FIELD, "apricot", 2, "fruit"));
      w.addDocument(d);
      d = new Document();
      d.add(new ContextSuggestField(FIELD, "apron", 1, "frugal"));
      w.addDocument(d);
      w.commit();
    }
    reader = DirectoryReader.open(dir);
    searcher = new SuggestIndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private ContextQuery newQuery() {
    return new ContextQuery(new PrefixCompletionQuery(analyzer, new Term(FIELD, "ap")));
  }

  /** "key|context" -> score, plus the result order as a string. */
  private Map<String, Float> run(ContextQuery q, StringBuilder order) throws Exception {
    TopSuggestDocs top = searcher.suggest(q, 10, false);
    Map<String, Float> scores = new HashMap<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : top.scoreLookupDocs()) {
      String k = sd.key + "|" + sd.context;
      assertNull("duplicate " + k, scores.put(k, sd.score));
      order.append(k).append('=').append(sd.score).append(' ');
    }
    return scores;
  }

  /**
   * prefix "fru" (boost 1) + exact "fruit" (boost 7). "fruitcake" is matched only by the prefix
   * context, so apple must score 3 * 1 = 3, not 3 * 7 = 21.
   */
  public void testPrefixContextPlusExactContext() throws Exception {
    ContextQuery q = newQuery();
    q.addContext("fru", 1f, false);
    q.addContext("fruit", 7f, true);
    StringBuilder order = new StringBuilder();
    Map<String, Float> s = run(q, order);
    assertEquals("results: " + order, 3, s.size());
    // matched by exact "fruit" (and by the shorter prefix "fru"; longer context first) -> 2*7
    assertEquals("apricot/fruit; results: " + order, 14f, s.get("apricot|fruit"), 0f);
    // matched by prefix "fru" only -> 1*1
    assertEquals("apron/frugal; results: " + order, 1f, s.get("apron|frugal"), 0f);
    // matched by prefix "fru" only; exact "fruit" != "fruitcake" -> 3*1
    assertEquals("apple/fruitcake; results: " + order, 3f, s.get("apple|fruitcake"), 0f);
  }

  /**
   * addAllContexts + exact "fruit" (boost 7). "fruitcake" has no boosted context, so apple must
   * score its weight 3, not 21.
   */
  public void testAllContextsPlusExactContext() throws Exception {
    ContextQuery q = newQuery();
    q.addContext("fruit", 7f);
    q.addAllContexts();
    StringBuilder order = new StringBuilder();
    Map<String, Float> s = run(q, order);
    assertEquals("results: " + order, 3, s.size());
    assertEquals("apricot/fruit; results: " + order, 14f, s.get("apricot|fruit"), 0f);
    assertEquals("apron/frugal; results: " + order, 1f, s.get("apron|frugal"), 0f);
    assertEquals("apple/fruitcake; results: " + order, 3f, s.get("apple|fruitcake"), 0f);
  }

  /** Control: exact "fruit" alone filters correctly (apple/fruitcake is not returned). */
  public void testControlExactOnlyFilters() throws Exception {
    ContextQuery q = newQuery();
    q.addContext("fruit", 7f);
    StringBuilder order = new StringBuilder();
    Map<String, Float> s = run(q, order);
    assertEquals("results: " + order, 1, s.size());
    assertEquals("apricot/fruit; results: " + order, 14f, s.get("apricot|fruit"), 0f);
  }

  /**
   * Control: an exact context that is NOT a string prefix of "fruitcake" ("fruits") does not leak;
   * apple gets the prefix boost 1 -> 3.
   */
  public void testControlExactNotAStringPrefixDoesNotLeak() throws Exception {
    ContextQuery q = newQuery();
    q.addContext("fru", 1f, false);
    q.addContext("fruits", 7f, true);
    StringBuilder order = new StringBuilder();
    Map<String, Float> s = run(q, order);
    assertEquals("results: " + order, 3, s.size());
    assertEquals("apple/fruitcake; results: " + order, 3f, s.get("apple|fruitcake"), 0f);
    assertEquals("apricot/fruit; results: " + order, 2f, s.get("apricot|fruit"), 0f);
    assertEquals("apron/frugal; results: " + order, 1f, s.get("apron|frugal"), 0f);
  }
}

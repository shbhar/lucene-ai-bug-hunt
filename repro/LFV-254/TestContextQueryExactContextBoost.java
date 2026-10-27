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

import static org.apache.lucene.search.suggest.document.TestSuggestField.Entry;
import static org.apache.lucene.search.suggest.document.TestSuggestField.assertSuggestions;
import static org.apache.lucene.search.suggest.document.TestSuggestField.iwcWithSuggestField;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * An exact query context must boost only the suggestions whose indexed context equals it, also when
 * a longer indexed context that starts with it ("redwood" for "red") is matched through another
 * query context: a shorter prefix context or addAllContexts().
 */
public class TestContextQueryExactContextBoost extends LuceneTestCase {

  private Directory dir;
  private Analyzer analyzer;
  private RandomIndexWriter iw;
  private DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random());
    iw = new RandomIndexWriter(random(), dir, iwcWithSuggestField(analyzer, "suggest_field"));
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, iw, analyzer, dir);
    super.tearDown();
  }

  private void add(String key, int weight, CharSequence... contexts) throws IOException {
    Document document = new Document();
    document.add(new ContextSuggestField("suggest_field", key, weight, contexts));
    iw.addDocument(document);
  }

  /** "table lamp" (weight 5) in context "redwood", "table cloth" (weight 4) in context "red". */
  private void addRedAndRedwood() throws IOException {
    add("table lamp", 5, "redwood");
    add("table cloth", 4, "red");
  }

  private ContextQuery newQuery() {
    return new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest_field", "tab")));
  }

  private TopSuggestDocs suggest(ContextQuery query) throws IOException {
    reader = iw.getReader();
    return new SuggestIndexSearcher(reader).suggest(query, 5, false);
  }

  /** Control: exact "red" alone does not match "redwood" (passes today). */
  public void testExactContextOnly() throws Exception {
    addRedAndRedwood();
    ContextQuery query = newQuery();
    query.addContext("red", 10);
    assertSuggestions(suggest(query), new Entry("table cloth", "red", 4 * 10));
  }

  /** Control: among matching contexts the longer one supplies the boost (passes today). */
  public void testLongerMatchingContextTakesPrecedence() throws Exception {
    addRedAndRedwood();
    ContextQuery query = newQuery();
    query.addContext("re", 2, false);
    query.addContext("redwood", 3);
    assertSuggestions(
        suggest(query),
        new Entry("table lamp", "redwood", 5 * 3),
        new Entry("table cloth", "red", 4 * 2));
  }

  /** "redwood" is matched only by the prefix context "re", so its boost is 1. */
  public void testExactContextWithShorterPrefixContext() throws Exception {
    addRedAndRedwood();
    ContextQuery query = newQuery();
    query.addContext("re", 1, false);
    query.addContext("red", 10);
    assertSuggestions(
        suggest(query),
        new Entry("table cloth", "red", 4 * 10),
        new Entry("table lamp", "redwood", 5 * 1));
  }

  /** "redwood" is matched only by addAllContexts(), so it scores its plain weight. */
  public void testExactContextWithAllContexts() throws Exception {
    addRedAndRedwood();
    ContextQuery query = newQuery();
    query.addContext("red", 10);
    query.addAllContexts();
    assertSuggestions(
        suggest(query),
        new Entry("table cloth", "red", 4 * 10),
        new Entry("table lamp", "redwood", 5));
  }

  /** The exact empty context matches only suggestions indexed without a context. */
  public void testEmptyExactContextWithAllContexts() throws Exception {
    add("table mat", 3);
    add("table lamp", 5, "redwood");
    ContextQuery query = newQuery();
    query.addContext("", 10);
    query.addAllContexts();
    assertSuggestions(
        suggest(query),
        new Entry("table mat", null, 3 * 10),
        new Entry("table lamp", "redwood", 5));
  }
}

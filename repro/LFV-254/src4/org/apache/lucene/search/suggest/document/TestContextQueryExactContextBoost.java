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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/**
 * ContextQuery looks up the boost of a matched path by testing whether the first N labels of the
 * path equal a query context, longest first, without checking that the context ends there. An
 * exact context "red" therefore lends its boost to an indexed context "redwood" that was matched
 * through another query context (a prefix context, or addAllContexts()).
 */
public class TestContextQueryExactContextBoost extends LuceneTestCase {

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat pf = new Completion50PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return field.equals("suggest_field")
                    ? pf
                    : ((PerFieldPostingsFormat) delegate.postingsFormat()).getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  private DirectoryReader index(Directory dir, Analyzer analyzer) throws Exception {
    IndexWriter w = new IndexWriter(dir, iwc(analyzer));
    Document d1 = new Document();
    d1.add(new ContextSuggestField("suggest_field", "table lamp", 5, "redwood"));
    w.addDocument(d1);
    Document d2 = new Document();
    d2.add(new ContextSuggestField("suggest_field", "table cloth", 4, "red"));
    w.addDocument(d2);
    w.close();
    return DirectoryReader.open(dir);
  }

  private static float scoreOf(TopSuggestDocs td, String key) {
    for (TopSuggestDocs.SuggestScoreDoc sd : td.scoreLookupDocs()) {
      if (sd.key.toString().equals(key)) {
        return sd.score;
      }
    }
    throw new AssertionError("no hit " + key);
  }

  /** prefix context "re" (boost 1) and exact context "red" (boost 10) */
  public void testExactBoostLeaksToLongerContextMatchedByPrefix() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    ContextQuery q = new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest_field", "tab")));
    q.addContext("re", 1, false);
    q.addContext("red", 10, true);
    TopSuggestDocs td = s.suggest(q, 2, false);
    assertEquals(2, td.scoreLookupDocs().length);
    // "red" is exact: it matches the context "red" only; "redwood" matches the prefix context "re"
    assertEquals("table cloth (context red, weight 4 * boost 10)", 40f, scoreOf(td, "table cloth"), 0f);
    assertEquals("table lamp (context redwood, weight 5 * boost 1)", 5f, scoreOf(td, "table lamp"), 0f);
    assertEquals("table cloth", td.scoreLookupDocs()[0].key.toString());
    r.close();
    dir.close();
  }

  /** addAllContexts() plus an exact context "red" (boost 10) */
  public void testExactBoostLeaksToLongerContextMatchedByAllContexts() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    ContextQuery q = new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest_field", "tab")));
    q.addAllContexts();
    q.addContext("red", 10, true);
    TopSuggestDocs td = s.suggest(q, 2, false);
    assertEquals(2, td.scoreLookupDocs().length);
    assertEquals(40f, scoreOf(td, "table cloth"), 0f);
    assertEquals("redwood has no boosted context: score = weight", 5f, scoreOf(td, "table lamp"), 0f);
    r.close();
    dir.close();
  }

  /** Control: with only the exact context, "redwood" is (correctly) not matched at all. */
  public void testExactOnlyControl() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    ContextQuery q = new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest_field", "tab")));
    q.addContext("red", 10, true);
    TopSuggestDocs td = s.suggest(q, 2, false);
    assertEquals(1, td.scoreLookupDocs().length);
    assertEquals(40f, scoreOf(td, "table cloth"), 0f);
    r.close();
    dir.close();
  }
}

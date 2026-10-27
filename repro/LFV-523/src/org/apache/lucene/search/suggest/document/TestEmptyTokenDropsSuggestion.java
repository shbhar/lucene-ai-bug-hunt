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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ConcatenateGraphFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * A zero-length token in the analyzed stream makes TokenStreamToAutomaton lose every path through it, so
 * ConcatenateGraphFilter emits no token and the completion suggesters index nothing for that suggestion.
 * Stock trigger: ArabicAnalyzer (also PersianAnalyzer, SoraniAnalyzer) turns a standalone tatweel (U+0640)
 * into an empty token.
 */
public class TestEmptyTokenDropsSuggestion extends LuceneTestCase {

  private static final String TATWEEL = "ـ";

  /** ConcatenateGraphFilter: "one output token for every path through the token stream". */
  public void testConcatenateGraphFilterKeepsThePathThroughAnEmptyToken() throws Exception {
    TokenStream in =
        new CannedTokenStream(new Token("a", 1, 0, 1), new Token("", 1, 2, 2), new Token("b", 1, 3, 4));
    List<String> out = new ArrayList<>();
    try (ConcatenateGraphFilter f = new ConcatenateGraphFilter(in)) {
      TermToBytesRefAttribute att = f.addAttribute(TermToBytesRefAttribute.class);
      f.reset();
      while (f.incrementToken()) {
        out.add(att.getBytesRef().utf8ToString());
      }
      f.end();
    }
    // the graph a -> "" -> b has exactly one path; joined with the separator it is a SEP SEP b
    assertEquals(List.of("a\u001F\u001Fb"), out);
  }

  /** NRT document suggester (SuggestField + PrefixCompletionQuery) with a stock analyzer. */
  public void testSuggestFieldWithArabicAnalyzer() throws Exception {
    Analyzer analyzer = new ArabicAnalyzer();
    assertEquals(List.of("foo", "", "bar"), tokens(analyzer, "foo " + TATWEEL + " bar")); // precondition

    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d1 = new Document();
        d1.add(new SuggestField("suggest", "foo bar", 1));
        w.addDocument(d1);
        Document d2 = new Document();
        d2.add(new SuggestField("suggest", "foo " + TATWEEL + " bar", 2));
        w.addDocument(d2);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        PrefixCompletionQuery query = new PrefixCompletionQuery(analyzer, new Term("suggest", "foo"));
        TopSuggestDocs hits = searcher.suggest(query, 10, false);
        List<String> keys = new ArrayList<>();
        for (TopSuggestDocs.SuggestScoreDoc hit : hits.scoreLookupDocs()) {
          keys.add(hit.key.toString());
        }
        assertEquals(
            "both suggestions start with 'foo'", List.of("foo " + TATWEEL + " bar", "foo bar"), keys);
      }
    }
  }

  /** AnalyzingSuggester goes through the same TokenStreamToAutomaton. */
  public void testAnalyzingSuggesterWithArabicAnalyzer() throws Exception {
    Analyzer analyzer = new ArabicAnalyzer();
    try (Directory dir = newDirectory()) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", analyzer);
      suggester.build(inputs(new String[] {"foo bar", "foo " + TATWEEL + " bar"}, new long[] {1, 2}));
      List<String> keys = new ArrayList<>();
      for (LookupResult r : suggester.lookup("foo", false, 10)) {
        keys.add(r.key.toString());
      }
      assertEquals(List.of("foo " + TATWEEL + " bar", "foo bar"), keys);
      assertEquals(2, suggester.getCount());
    }
  }

  private static Codec completionCodec() {
    // the completion format that wraps this release's default postings format (Completion104 on main)
    String delegate = TestUtil.getDefaultPostingsFormat().getName();
    final PostingsFormat completion =
        PostingsFormat.forName("Completion" + delegate.substring("Lucene".length()));
    final PostingsFormat perField =
        new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return completion;
          }
        };
    Codec base = TestUtil.getDefaultCodec();
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        return perField;
      }
    };
  }

  private static List<String> tokens(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  private static InputIterator inputs(String[] keys, long[] weights) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < keys.length ? new BytesRef(keys[i]) : null;
      }

      @Override
      public long weight() {
        return weights[i];
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }
}

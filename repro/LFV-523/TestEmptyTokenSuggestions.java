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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenStreamToAutomaton;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ConcatenateGraphFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.Input;
import org.apache.lucene.search.suggest.InputArrayIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.automaton.AutomatonTestUtil;
import org.apache.lucene.util.automaton.Automaton;

/**
 * A token whose term is empty must not remove the paths through it. ArabicAnalyzer (and
 * PersianAnalyzer, SoraniAnalyzer) turn a standalone tatweel (U+0640) into such a token, and
 * PatternReplaceFilter can blank a punctuation-only token.
 */
public class TestEmptyTokenSuggestions extends LuceneTestCase {

  private static final String TATWEEL = "\u0640";

  /** TokenStreamToAutomaton: one accepted string for every path through the token graph. */
  public void testTokenStreamToAutomaton() throws IOException {
    // control: a, x, b has one path
    assertEquals(1, paths(token("a", 1), token("x", 1), token("b", 1)));
    // a, "", b has one path too
    assertEquals(1, paths(token("a", 1), token("", 1), token("b", 1)));
  }

  /** The same with the empty token stacked on a non-empty one: a, {y | ""}, b has two paths. */
  public void testTokenStreamToAutomatonStacked() throws IOException {
    assertEquals(2, paths(token("a", 1), token("y", 1), token("", 0), token("b", 1)));
  }

  /** ConcatenateGraphFilter: "one output token for every path through the token stream". */
  public void testConcatenateGraphFilter() throws IOException {
    // control
    assertEquals(1, concatenate(token("a", 1), token("x", 1), token("b", 1)).size());
    List<String> out = concatenate(token("a", 1), token("", 1), token("b", 1));
    assertEquals("one output token expected, got " + out, 1, out.size());
  }

  /** The document suggester (SuggestField + PrefixCompletionQuery) with a stock analyzer. */
  public void testSuggestFieldWithArabicAnalyzer() throws IOException {
    Analyzer analyzer = new ArabicAnalyzer();
    assertEquals(List.of("foo", "", "bar"), terms(analyzer, "foo " + TATWEEL + " bar"));
    try (Directory dir = newDirectory();
        RandomIndexWriter iw =
            new RandomIndexWriter(random(), dir, iwcWithSuggestField(analyzer, "suggest"))) {
      Document doc = new Document();
      doc.add(new SuggestField("suggest", "foo bar", 1));
      iw.addDocument(doc);
      doc = new Document();
      doc.add(new SuggestField("suggest", "foo " + TATWEEL + " bar", 2));
      iw.addDocument(doc);
      try (DirectoryReader reader = iw.getReader()) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        PrefixCompletionQuery query =
            new PrefixCompletionQuery(analyzer, new Term("suggest", "foo"));
        List<String> keys = new ArrayList<>();
        for (TopSuggestDocs.SuggestScoreDoc hit :
            searcher.suggest(query, 10, false).scoreLookupDocs()) {
          keys.add(hit.key.toString());
        }
        assertEquals(List.of("foo " + TATWEEL + " bar", "foo bar"), keys);
      }
    }
  }

  /** AnalyzingSuggester and FuzzySuggester with the same stock analyzer. */
  public void testAnalyzingAndFuzzySuggesterWithArabicAnalyzer() throws IOException {
    Analyzer analyzer = new ArabicAnalyzer();
    Input[] inputs = {new Input("foo bar", 1), new Input("foo " + TATWEEL + " bar", 2)};
    List<String> expected = List.of("foo " + TATWEEL + " bar", "foo bar");
    try (Directory dir = newDirectory()) {
      AnalyzingSuggester analyzing = new AnalyzingSuggester(dir, "analyzing", analyzer);
      analyzing.build(new InputArrayIterator(inputs));
      assertEquals(expected, keys(analyzing, "foo"));
      FuzzySuggester fuzzy = new FuzzySuggester(dir, "fuzzy", analyzer);
      fuzzy.build(new InputArrayIterator(inputs));
      assertEquals(expected, keys(fuzzy, "foo"));
    }
  }

  /** A custom chain that strips punctuation, so the token "-" becomes "". */
  public void testAnalyzingSuggesterWithPunctuationStripped() throws IOException {
    Analyzer analyzer =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("lowercase")
            .addTokenFilter("patternReplace", "pattern", "[^\\p{L}\\p{N}]", "replacement", "")
            .build();
    assertEquals(
        List.of("spider", "man", "", "homecoming"), terms(analyzer, "Spider Man - Homecoming"));
    Input[] inputs = {
      new Input("Spider Man Far From Home", 1), new Input("Spider Man - Homecoming", 2)
    };
    try (Directory dir = newDirectory()) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "punct", analyzer);
      suggester.build(new InputArrayIterator(inputs));
      assertEquals(
          List.of("Spider Man - Homecoming", "Spider Man Far From Home"),
          keys(suggester, "spider"));
    }
  }

  private static Token token(String term, int posInc) {
    Token t = new Token(term, 0, 1);
    t.setPositionIncrement(posInc);
    return t;
  }

  private static int paths(Token... tokens) throws IOException {
    Automaton a = new TokenStreamToAutomaton().toAutomaton(new CannedTokenStream(tokens));
    return AutomatonTestUtil.getFiniteStringsRecursive(a, -1).size();
  }

  private static List<String> concatenate(Token... tokens) throws IOException {
    List<String> out = new ArrayList<>();
    try (ConcatenateGraphFilter f = new ConcatenateGraphFilter(new CannedTokenStream(tokens))) {
      TermToBytesRefAttribute att = f.addAttribute(TermToBytesRefAttribute.class);
      f.reset();
      while (f.incrementToken()) {
        out.add(att.getBytesRef().utf8ToString());
      }
      f.end();
    }
    return out;
  }

  private static List<String> terms(Analyzer analyzer, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static List<String> keys(Lookup suggester, String key) throws IOException {
    List<String> out = new ArrayList<>();
    for (LookupResult r : suggester.lookup(key, false, 10)) {
      out.add(r.key.toString());
    }
    return out;
  }
}

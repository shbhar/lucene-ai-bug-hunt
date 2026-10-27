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
package org.apache.lucene.analysis.sinks;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockAnalyzer;

/**
 * TeeSinkTokenFilter javadoc: the sinks "get tokens from source1 ... and will further do additional
 * token filtering" (its example wraps each sink in a further filter). A sink is the source of its
 * own chain, but SinkTokenStream.incrementToken() only restores the tee's captured state and never
 * calls clearAttributes(). An attribute that exists only on the sink's chain (here the
 * KeywordAttribute of SetKeywordMarkerFilter, which only ever sets it to true and relies on the
 * source to clear it, as the package docs require of every tokenizer) is therefore never reset:
 * once one token is marked as a keyword, every later token stays a keyword and is not stemmed. Only
 * public API is used.
 */
public class TestTeeSinkClearsSinkAttributes extends BaseTokenStreamTestCase {

  private static final String TEXT = "The quick foxes jumped";

  private static List<String> terms(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  private static TeeSinkTokenFilter tee() {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(TEXT));
    return new TeeSinkTokenFilter(new LowerCaseFilter(tokenizer));
  }

  /** protect "quick" from stemming, stem the rest */
  private static TokenStream protectAndStem(TokenStream in) {
    return new PorterStemFilter(
        new SetKeywordMarkerFilter(in, new CharArraySet(Arrays.asList("quick"), false)));
  }

  public void testKeywordMarkerOnSinkStemsLaterTokens() throws IOException {
    // control: the same chain directly on the tokenizer
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(TEXT));
    assertEquals(
        Arrays.asList("the", "quick", "fox", "jump"),
        terms(protectAndStem(new LowerCaseFilter(tokenizer))));

    TeeSinkTokenFilter tee = tee();
    TokenStream sinkChain = protectAndStem(tee.newSinkTokenStream());
    assertEquals(Arrays.asList("the", "quick", "foxes", "jumped"), terms(tee)); // tee first
    assertEquals(Arrays.asList("the", "quick", "fox", "jump"), terms(sinkChain));
  }

  public void testIndexedSinkFieldIsStemmed() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      TeeSinkTokenFilter tee = tee();
      Document doc = new Document();
      doc.add(new TextField("plain", tee)); // the tee's field comes first, as the javadoc requires
      doc.add(new TextField("stemmed", protectAndStem(tee.newSinkTokenStream())));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("control", 1, s.count(new TermQuery(new Term("plain", "foxes"))));
        assertEquals(1, s.count(new TermQuery(new Term("stemmed", "fox"))));
        assertEquals(1, s.count(new TermQuery(new Term("stemmed", "jump"))));
      }
    }
  }

  /** the test framework's own check that the source of a chain calls clearAttributes() */
  public void testSinkPassesClearAttributesCheck() throws IOException {
    TeeSinkTokenFilter tee = tee();
    TokenStream sink = tee.newSinkTokenStream();
    terms(tee);
    assertTokenStreamContents(sink, new String[] {"the", "quick", "foxes", "jumped"});
  }
}

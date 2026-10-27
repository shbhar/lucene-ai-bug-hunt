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
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A filter wrapped around a sink of {@link TeeSinkTokenFilter} may add an attribute that the tee
 * does not have (here the {@link KeywordAttribute} of {@link SetKeywordMarkerFilter}). Such an
 * attribute is not part of the states the tee captures, and the sink never clears it, so a value
 * set on one token stays on every later token. The same chain directly on the tokenizer is correct.
 * Public API only.
 */
public class TestTeeSinkSinkOnlyAttributes extends BaseTokenStreamTestCase {

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

  private static TokenStream lowercased() {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(TEXT));
    return new LowerCaseFilter(tokenizer);
  }

  /** Protect "quick" from stemming, stem the rest. */
  private static TokenStream protectAndStem(TokenStream in) {
    return new PorterStemFilter(
        new SetKeywordMarkerFilter(in, new CharArraySet(Arrays.asList("quick"), false)));
  }

  /** Control: the chain directly on the tokenizer. */
  public void testControlSameChainOnTokenizer() throws IOException {
    assertEquals(Arrays.asList("the", "quick", "fox", "jump"), terms(protectAndStem(lowercased())));
  }

  /** The same chain on a sink. */
  public void testKeywordMarkerOnSink() throws IOException {
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(lowercased());
    TokenStream sinkChain = protectAndStem(tee.newSinkTokenStream());
    assertEquals(Arrays.asList("the", "quick", "foxes", "jumped"), terms(tee)); // tee first
    assertEquals(Arrays.asList("the", "quick", "fox", "jump"), terms(sinkChain));
  }

  /** Control: KeywordAttribute added to the tee before the sink is created. */
  public void testControlAttributeAddedToTeeFirst() throws IOException {
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(lowercased());
    tee.addAttribute(KeywordAttribute.class);
    TokenStream sinkChain = protectAndStem(tee.newSinkTokenStream());
    terms(tee);
    assertEquals(Arrays.asList("the", "quick", "fox", "jump"), terms(sinkChain));
  }

  /** Index the tee as "plain" and the sink chain as "stemmed", in the documented order. */
  public void testIndexedSinkField() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      TeeSinkTokenFilter tee = new TeeSinkTokenFilter(lowercased());
      Document doc = new Document();
      doc.add(new TextField("plain", tee));
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

  /** The test framework's own check that the source of a chain clears attributes. */
  public void testSinkPassesClearAttributesCheck() throws IOException {
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(lowercased());
    TokenStream sink = tee.newSinkTokenStream();
    terms(tee);
    assertTokenStreamContents(sink, new String[] {"the", "quick", "foxes", "jumped"});
  }
}

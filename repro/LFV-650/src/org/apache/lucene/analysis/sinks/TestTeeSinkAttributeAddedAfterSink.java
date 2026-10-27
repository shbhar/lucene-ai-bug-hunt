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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
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
 * TeeSinkTokenFilter javadoc: "This is useful in situations where multiple fields share many common
 * analysis steps and then go their separate ways"; its example creates the sinks first and then
 * wraps the tee in a further filter ({@code new LowerCaseFilter(source1)}); newSinkTokenStream()
 * "Returns a new SinkTokenStream that receives all tokens consumed by this stream."
 *
 * <p>Here the tee is followed by a stemmer (one field stemmed, one not: the typical "separate
 * ways"). The stemmer adds a KeywordAttribute to the tee's attribute source after the sink was
 * created, so every captured state contains an attribute the sink does not have and the sink throws
 * IllegalArgumentException instead of replaying the tokens. Only public API is used.
 */
public class TestTeeSinkAttributeAddedAfterSink extends BaseTokenStreamTestCase {

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

  public void testSinkAfterStemmerOnTee() throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("The quick foxes jumped"));
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(new LowerCaseFilter(tokenizer));
    TokenStream sink = tee.newSinkTokenStream(); // sinks first, as in the class javadoc
    TokenStream stemmed = new PorterStemFilter(tee); // then the tee's own chain

    assertEquals(Arrays.asList("the", "quick", "fox", "jump"), terms(stemmed)); // tee consumed first
    assertEquals(Arrays.asList("the", "quick", "foxes", "jumped"), terms(sink));
  }

  public void testIndexStemmedAndUnstemmedFields() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Tokenizer tokenizer = new WhitespaceTokenizer();
      tokenizer.setReader(new StringReader("The quick foxes jumped"));
      TeeSinkTokenFilter tee = new TeeSinkTokenFilter(new LowerCaseFilter(tokenizer));
      TokenStream sink = tee.newSinkTokenStream();
      Document doc = new Document();
      doc.add(new TextField("stemmed", new PorterStemFilter(tee))); // the tee's field comes first
      doc.add(new TextField("plain", sink));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(1, s.count(new TermQuery(new Term("stemmed", "fox"))));
        assertEquals(1, s.count(new TermQuery(new Term("plain", "foxes"))));
      }
    }
  }
}

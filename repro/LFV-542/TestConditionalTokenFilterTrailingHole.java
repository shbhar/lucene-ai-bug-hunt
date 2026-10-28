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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A ConditionalTokenFilter must keep the trailing hole of its input (here: a stop word removed at
 * the end of the value), also when the last token went through the wrapped filters.
 */
public class TestConditionalTokenFilterTrailingHole extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP_THE = new CharArraySet(List.of("the"), false);
  private static final CharArraySet STOP_A = new CharArraySet(List.of("a"), false);

  /** WhitespaceTokenizer + StopFilter("the"): "abc the" ends with a hole of 1 position. */
  private static TokenStream stopThe(String text) {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    return new StopFilter(tokenizer, STOP_THE);
  }

  /** A condition that is true for every token. */
  private static TokenStream always(TokenStream in, Function<TokenStream, TokenStream> wrapped) {
    return new ConditionalTokenFilter(in, wrapped) {
      @Override
      protected boolean shouldFilter() {
        return true;
      }
    };
  }

  private static void assertEnd(TokenStream ts, String term, int finalOffset, int finalPosInc)
      throws IOException {
    assertTokenStreamContents(
        ts,
        new String[] {term},
        null,
        null,
        null,
        new int[] {1},
        null,
        finalOffset,
        finalPosInc,
        null,
        true,
        null);
  }

  /** Wrapping a filter that does nothing must not change the stream. */
  public void testIdentityDelegate() throws IOException {
    assertEnd(stopThe("abc the"), "abc", 7, 1); // control: the plain chain
    assertEnd(always(stopThe("abc the"), in -> in), "abc", 7, 1);
  }

  /** ProtectedTermFilter: the hole must not depend on whether the last token is protected. */
  public void testProtectedTermFilter() throws IOException {
    CharArraySet protectX = new CharArraySet(List.of("x"), false);
    CharArraySet nothing = CharArraySet.EMPTY_SET;
    assertEnd(new LowerCaseFilter(stopThe("ABC the")), "abc", 7, 1); // control
    assertEnd(new ProtectedTermFilter(protectX, stopThe("x the"), LowerCaseFilter::new), "x", 5, 1);
    assertEnd(
        new ProtectedTermFilter(protectX, stopThe("ABC the"), LowerCaseFilter::new), "abc", 7, 1);
    assertEnd(
        new ProtectedTermFilter(nothing, stopThe("ABC the"), LowerCaseFilter::new), "abc", 7, 1);
  }

  /** The same through CustomAnalyzer's whenTerm() ... endwhen(). */
  public void testCustomAnalyzerWhenTerm() throws IOException {
    try (Analyzer plain =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("stop")
                .addTokenFilter("lowercase")
                .build();
        Analyzer conditional =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("stop")
                .whenTerm(t -> t.length() > 0) // every token
                .addTokenFilter("lowercase")
                .endwhen()
                .build()) {
      assertEnd(plain.tokenStream("f", "ABC the"), "abc", 7, 1); // control
      assertEnd(conditional.tokenStream("f", "ABC the"), "abc", 7, 1);
    }
  }

  /** A hole made by the wrapped filters is added to the input's hole, as in the plain chain. */
  public void testHolesAddUp() throws IOException {
    assertEnd(new StopFilter(stopThe("abc a the"), STOP_A), "abc", 9, 2); // control
    assertEnd(always(stopThe("abc a the"), in -> new StopFilter(in, STOP_A)), "abc", 9, 2);
  }

  /** Index time: the next value of a multi-valued field starts after the hole (gap 0). */
  public void testNextValueOfMultiValuedField() throws IOException {
    for (boolean conditional : new boolean[] {false, true}) {
      Analyzer analyzer =
          new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
              Tokenizer tokenizer = new WhitespaceTokenizer();
              TokenStream ts = new StopFilter(tokenizer, STOP_THE);
              if (conditional) {
                ts = new ProtectedTermFilter(CharArraySet.EMPTY_SET, ts, LowerCaseFilter::new);
              }
              return new TokenStreamComponents(tokenizer, ts);
            }
          };
      String chain = conditional ? "ProtectedTermFilter(LowerCaseFilter)" : "control";
      try (Directory dir = newDirectory();
          IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("f", "abc the", Field.Store.NO));
        doc.add(new TextField("f", "def", Field.Store.NO));
        w.addDocument(doc);
        try (IndexReader r = DirectoryReader.open(w)) {
          PostingsEnum def =
              getOnlyLeafReader(r).postings(new Term("f", "def"), PostingsEnum.POSITIONS);
          def.nextDoc();
          int position = def.nextPosition();
          int hits = newSearcher(r).count(new PhraseQuery("f", "abc", "def"));
          assertEquals(
              chain,
              "def at position 2, \"abc def\" 0 hits",
              "def at position " + position + ", \"abc def\" " + hits + " hits");
        }
      } finally {
        analyzer.close();
      }
    }
  }
}

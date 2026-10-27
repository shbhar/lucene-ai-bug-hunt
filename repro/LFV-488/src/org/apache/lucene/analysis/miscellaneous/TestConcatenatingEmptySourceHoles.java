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
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

/**
 * ConcatenatingTokenStream carries the final position increment of an exhausted source over to
 * the first token of the next source (LUCENE-8650: "was not propagating final position increments
 * from its child streams correctly"). When a source in between produces no tokens, its own final
 * increment overwrites the one still pending from the source before it, and when the last source
 * produces no tokens end() reports only that source's increment. Either way the trailing hole of
 * the earlier source (for example a removed stop word) is lost.
 */
public class TestConcatenatingEmptySourceHoles extends BaseTokenStreamTestCase {

  /** source "foo" followed by a hole (a removed trailing stop word): final posInc 1 */
  private static TokenStream fooThenHole() {
    return new CannedTokenStream(1, 7, new Token("foo", 0, 3));
  }

  private static TokenStream empty() {
    return new CannedTokenStream(0, 0);
  }

  private static TokenStream bar() {
    return new CannedTokenStream(0, 3, new Token("bar", 0, 3));
  }

  /** Control: without the empty source the hole is kept (bar has position increment 2). */
  public void testControlNoEmptySource() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), bar()),
        new String[] {"foo", "bar"},
        new int[] {0, 7},
        new int[] {3, 10},
        null,
        new int[] {1, 2},
        null,
        10,
        0,
        null,
        false,
        null);
  }

  /** An empty source in the middle must not change the positions of the other tokens. */
  public void testEmptySourceInTheMiddleKeepsHole() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), empty(), bar()),
        new String[] {"foo", "bar"},
        new int[] {0, 7},
        new int[] {3, 10},
        null,
        new int[] {1, 2},
        null,
        10,
        0,
        null,
        false,
        null);
  }

  /** An empty last source must not drop the trailing hole of the source before it. */
  public void testEmptyLastSourceKeepsFinalPositionIncrement() throws IOException {
    assertTokenStreamContents(
        new ConcatenatingTokenStream(fooThenHole(), empty()),
        new String[] {"foo"},
        new int[] {0},
        new int[] {3},
        null,
        new int[] {1},
        null,
        7,
        1,
        null,
        false,
        null);
  }

  /**
   * Index-level effect with real analysis: the values "foo the", "the" and "bar" (stop word "the")
   * concatenated. Analysed as the single text "foo the the bar", "bar" is three positions after
   * "foo", so the phrase "foo bar" needs slop 2; the concatenation must give the same positions.
   */
  public void testPhraseAcrossStopWordOnlySource() throws IOException {
    CharArraySet stop = new CharArraySet(List.of("the"), false);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(
          new TextField(
              "concat",
              new ConcatenatingTokenStream(ws("foo the", stop), ws("the", stop), ws("bar", stop))));
      doc.add(new TextField("single", ws("foo the the bar", stop)));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("single source, \"foo bar\"~1", 0, s.count(new PhraseQuery(1, "single", "foo", "bar")));
        assertEquals("single source, \"foo bar\"~2", 1, s.count(new PhraseQuery(2, "single", "foo", "bar")));
        assertEquals("concatenated, \"foo bar\"~1", 0, s.count(new PhraseQuery(1, "concat", "foo", "bar")));
      }
    }
  }

  private static TokenStream ws(String text, CharArraySet stop) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return new StopFilter(t, stop);
  }
}

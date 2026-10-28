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

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.util.QueryBuilder;

/**
 * The LUCENE-3849 scenario (a phrase query across two values of a multi-valued field, position
 * increment gap 0, the first value ending in a removed token) with WordDelimiterGraphFilter in
 * place of StopFilter. The phrase query is built by QueryBuilder from the same analyzer, and the
 * single-valued document is the reference.
 */
public class TestWdgfV3CrossValuePhrase extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  private static Analyzer analyzer(boolean stop, boolean wdgf) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream ts = t;
        if (stop) {
          ts = new StopFilter(ts, STOP);
        }
        if (wdgf) {
          ts = new WordDelimiterGraphFilter(ts, GENERATE_WORD_PARTS, null);
        }
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private int count(Analyzer a, String[] values, String phrase) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        Document doc = new Document();
        for (String v : values) {
          doc.add(new Field("body", v, TextField.TYPE_NOT_STORED));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query q = new QueryBuilder(a).createPhraseQuery("body", phrase);
        int c = s.count(q);
        System.out.println(
            "V3XV values=" + String.join("|", values) + " query=" + q + " count=" + c);
        return c;
      }
    }
  }

  /** Control (LUCENE-3849): trailing stop word, phrase across values matches. */
  public void testStopFilterControl() throws Exception {
    try (Analyzer a = analyzer(true, false)) {
      assertEquals(1, count(a, new String[] {"foo the bar"}, "foo the bar"));
      assertEquals(1, count(a, new String[] {"foo the", "bar"}, "foo the bar"));
    }
  }

  /** Same scenario with a trailing all-delimiter token swallowed by WDGF. */
  public void testWdgfTrailingDelimiter() throws Exception {
    try (Analyzer a = analyzer(false, true)) {
      assertEquals(1, count(a, new String[] {"foo -- bar"}, "foo -- bar"));
      assertEquals(1, count(a, new String[] {"foo --", "bar"}, "foo -- bar"));
    }
  }

  /** StopFilter + WDGF: the stop word's own hole is lost when "--" follows it at the end. */
  public void testStopThenWdgf() throws Exception {
    try (Analyzer a = analyzer(true, true)) {
      assertEquals(1, count(a, new String[] {"foo the -- bar"}, "foo the -- bar"));
      assertEquals(1, count(a, new String[] {"foo the", "bar"}, "foo the bar"));
      assertEquals(1, count(a, new String[] {"foo the --", "bar"}, "foo the -- bar"));
    }
  }
}

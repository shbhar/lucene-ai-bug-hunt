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
package org.apache.lucene.analysis.te;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * The default TeluguAnalyzer must remove the words of its own default stop list. Its StopFilter
 * runs after IndicNormalizationFilter and TeluguNormalizationFilter, which shorten long vowel
 * signs, while the list holds only the unnormalized spellings.
 */
public class TestTeluguDefaultStopwordsRemoved extends BaseTokenStreamTestCase {

  private static List<String> terms(TeluguAnalyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** కూడా ("also") is in the default stop set, so analyzing it must give no token. */
  public void testAlsoIsRemoved() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer()) {
      assertTrue(TeluguAnalyzer.getDefaultStopSet().contains("కూడా"));
      assertEquals(List.of(), terms(a, "కూడా"));
    }
  }

  /** Every single-word entry of the default stop set must give no token. */
  public void testEverySingleWordDefaultIsRemoved() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer()) {
      int singleWord = 0;
      List<String> kept = new ArrayList<>();
      for (Object o : TeluguAnalyzer.getDefaultStopSet()) {
        String w = new String((char[]) o);
        if (w.indexOf(' ') >= 0) {
          continue; // two entries are two words each and can never match a single token
        }
        singleWord++;
        List<String> t = terms(a, w);
        if (t.isEmpty() == false) {
          kept.add(w + " -> " + t);
        }
      }
      assertEquals(
          kept.size() + " of " + singleWord + " single-word default stop words kept: " + kept,
          0,
          kept.size());
    }
  }

  /**
   * With the stop word dropped, the AND query "రాముడు కూడా" must match both documents with రాముడు.
   */
  public void testAndQueryIgnoresStopWord() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String text : new String[] {"రాముడు కూడా వచ్చాడు", "రాముడు వచ్చాడు", "కుడి చేయి"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q =
            new QueryBuilder(a).createBooleanQuery("f", "రాముడు కూడా", BooleanClause.Occur.MUST);
        assertEquals("hits for " + q, 2, new IndexSearcher(r).count(q));
      }
    }
  }

  /**
   * Control: గురించి has no long vowel sign, so the normalizers leave it alone and it is removed.
   */
  public void testControlEntryWithoutLongVowelSignIsRemoved() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer()) {
      assertTrue(TeluguAnalyzer.getDefaultStopSet().contains("గురించి"));
      assertEquals(List.of(), terms(a, "గురించి"));
    }
  }
}

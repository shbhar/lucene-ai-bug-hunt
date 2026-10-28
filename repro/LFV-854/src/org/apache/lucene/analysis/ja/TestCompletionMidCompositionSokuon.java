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
package org.apache.lucene.analysis.ja;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.CompletionAnalyzer;
import org.apache.lucene.search.suggest.document.PrefixCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestField;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Mid-IME-composition queries (QUERY mode) with a small tsu followed by a pending c, j, f or v find no suggestion.
 * romaji_map.txt maps a small tsu followed by a not-yet-converted letter to the doubled letter only for k, g, s, z, t,
 * d, h, b, p, m, y, r and w (rows "ッk,kk" .. "ッw,ww"; upstream test: "サッｋ" -> "sakk"), although the full rows
 * double c, j, f and v too (ッチャ -> ccha, ッジ -> jji, ッフ -> ffu, ッヴ -> vvu). So while the user types "maccha"
 * the IME shows "まっｃ", which is romanized "maxtuc" and matches nothing, although 抹茶 is indexed as "maccha".
 */
public class TestCompletionMidCompositionSokuon extends LuceneTestCase {

  private static final String FIELD = "suggest";

  public void testPendingLetterAfterSmallTsu() throws Exception {
    List<String> report = new ArrayList<>();
    try (Analyzer indexAnalyzer =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer queryAnalyzer =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY);
        Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new CompletionAnalyzer(indexAnalyzer));
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String word : new String[] {"サッカー", "抹茶", "バッジ", "バッファ"}) {
          Document doc = new Document();
          doc.add(new SuggestField(FIELD, word, 1));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        // control: the same composition state with a pending k works (and is asserted upstream)
        assertTrue(
            "control: 'さっｋ' must suggest サッカー",
            suggest(searcher, queryAnalyzer, "さっｋ").contains("サッカー"));
        // IME composition states while typing the index-side spellings maccha / bajji / baffa
        String[][] cases = {{"抹茶", "まっｃ"}, {"バッジ", "ばっｊ"}, {"バッファ", "ばっｆ"}};
        for (String[] c : cases) {
          List<String> got = suggest(searcher, queryAnalyzer, c[1]);
          if (got.contains(c[0]) == false) {
            report.add(
                "composition '"
                    + c[1]
                    + "' (query romaji "
                    + terms(queryAnalyzer, c[1])
                    + ") -> suggestions "
                    + got
                    + ", expected "
                    + c[0]
                    + " (index-side romaji: "
                    + terms(indexAnalyzer, c[0])
                    + ")");
          }
        }
      }
    }
    assertTrue(
        "mid-composition small tsu + pending letter finds no suggestion:\n  " + String.join("\n  ", report),
        report.isEmpty());
  }

  private static List<String> suggest(SuggestIndexSearcher searcher, Analyzer queryAnalyzer, String typed)
      throws Exception {
    PrefixCompletionQuery q =
        new PrefixCompletionQuery(new CompletionAnalyzer(queryAnalyzer), new Term(FIELD, typed));
    TopSuggestDocs docs = searcher.suggest(q, 10, true);
    List<String> keys = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : docs.scoreLookupDocs()) {
      keys.add(sd.key.toString());
    }
    return keys;
  }

  private static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream(FIELD, text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  private static Codec completionCodec() {
    String[] names = {"Completion104", "Completion101", "Completion912", "Completion99", "Completion90"};
    PostingsFormat pf = null;
    for (String n : names) {
      if (PostingsFormat.availablePostingsFormats().contains(n)) {
        pf = PostingsFormat.forName(n);
        break;
      }
    }
    final PostingsFormat completion = pf;
    final Codec delegate = TestUtil.getDefaultCodec();
    return new FilterCodec(delegate.getName(), delegate) {
      @Override
      public PostingsFormat postingsFormat() {
        return new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            if (FIELD.equals(field)) {
              return completion;
            }
            return ((PerFieldPostingsFormat) delegate.postingsFormat()).getPostingsFormatForField(field);
          }
        };
      }
    };
  }
}

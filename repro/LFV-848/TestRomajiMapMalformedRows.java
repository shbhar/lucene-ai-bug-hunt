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
import org.apache.lucene.analysis.ja.completion.KatakanaRomanizer;
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
import org.apache.lucene.util.CharsRef;

/**
 * Three rows of romaji_map.txt are malformed, so their katakana never get the romanization the row was written for:
 *
 * <ul>
 *   <li>the row "ッへ,hhe" has the HIRAGANA letter he (U+3078) in its key, while romanize() only ever receives
 *       katakana (readings, or hiragana surfaces converted by CharSequenceUtils.toKatakana); so katakana ッヘ (U+30C3
 *       U+30D8) has no row and is romanized "xtuhe" instead of "hhe", and ヨッヘン is not suggested for "yohhen";
 *   <li>"ッスォ,sswo,suxo": "suxo" lacks the doubled consonant (the sibling rows ッスァ..ッスェ give ssuxa..ssuxe);
 *   <li>"ッドゥ,ddwu,ddoxi": "ddoxi" is the spelling of ッドィ (row above), not of ッドゥ (siblings give ddoxa, ddoxe,
 *       ddoxo).
 * </ul>
 */
public class TestRomajiMapMalformedRows extends LuceneTestCase {

  private static final String FIELD = "suggest";

  private static List<String> romanize(String katakana) {
    List<String> out = new ArrayList<>();
    for (CharsRef r : KatakanaRomanizer.getInstance().romanize(new CharsRef(katakana))) {
      out.add(r.toString());
    }
    return out;
  }

  public void testRowsAreWellFormed() throws Exception {
    List<String> report = new ArrayList<>();
    // controls: the sibling rows behave as written
    assertTrue(romanize("ッハ").contains("hha"));
    assertTrue(romanize("ッスェ").contains("ssuxe"));
    assertTrue(romanize("ッドォ").contains("ddoxo"));

    List<String> he = romanize("ッヘ"); // KATAKANA SMALL TU + KATAKANA LETTER HE
    if (he.contains("hhe") == false) {
      report.add("romanize(ッヘ) = " + he + ", expected to contain hhe (row 'ッへ,hhe' has a hiragana key)");
    }
    List<String> suo = romanize("ッスォ");
    if (suo.contains("ssuxo") == false || suo.contains("suxo")) {
      report.add("romanize(ッスォ) = " + suo + ", expected ssuxo (doubled), not suxo (= スォ without the small tsu)");
    }
    List<String> dwu = romanize("ッドゥ");
    if (dwu.contains("ddoxu") == false || dwu.contains("ddoxi")) {
      report.add("romanize(ッドゥ) = " + dwu + ", expected ddoxu, not ddoxi (= the spelling of ッドィ)");
    }

    // end to end through the document suggester: ヨッハン works, ヨッヘン does not
    try (Analyzer indexAnalyzer =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.INDEX);
        Analyzer queryAnalyzer =
            new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY);
        Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new CompletionAnalyzer(indexAnalyzer));
      iwc.setCodec(completionCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String word : new String[] {"ヨッハン", "ヨッヘン"}) {
          Document doc = new Document();
          doc.add(new SuggestField(FIELD, word, 1));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        assertTrue(
            "control: 'yohhan' must suggest ヨッハン",
            suggest(searcher, queryAnalyzer, "yohhan").contains("ヨッハン"));
        List<String> got = suggest(searcher, queryAnalyzer, "yohhen");
        if (got.contains("ヨッヘン") == false) {
          report.add(
              "typed 'yohhen' -> suggestions "
                  + got
                  + ", expected ヨッヘン (index-side romaji: "
                  + terms(indexAnalyzer, "ヨッヘン")
                  + ")");
        }
      }
    }
    assertTrue("malformed romaji_map.txt rows:\n  " + String.join("\n  ", report), report.isEmpty());
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

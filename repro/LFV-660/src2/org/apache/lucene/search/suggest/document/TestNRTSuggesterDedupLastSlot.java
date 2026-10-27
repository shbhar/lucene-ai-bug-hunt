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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.CharsRef;

/**
 * The document suggester with skipDuplicates=true, a synonym at index time and one suggestion per
 * analyzed form: asking for 2 suggestions returns only 1 although a second distinct suggestion
 * exists (asking for 3 returns both). Public API only.
 */
public class TestNRTSuggesterDedupLastSlot extends LuceneTestCase {

  private static Analyzer synonymAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        try {
          SynonymMap.Builder b = new SynonymMap.Builder(true);
          b.add(new CharsRef("b"), new CharsRef("xb"), true); // keep "b", add "xb"
          return new TokenStreamComponents(t, new SynonymGraphFilter(t, b.build(), false));
        } catch (java.io.IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private static Analyzer plainAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MockTokenizer(MockTokenizer.WHITESPACE, false));
      }
    };
  }

  private static IndexWriterConfig config(Analyzer a) {
    IndexWriterConfig iwc = new IndexWriterConfig(a);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat pf = new Completion912PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return field.equals("suggest")
                    ? pf
                    : ((PerFieldPostingsFormat) delegate.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  private static List<String> suggest(int num, boolean skipDuplicates) throws Exception {
    Analyzer index = new CompletionAnalyzer(synonymAnalyzer());
    Analyzer query = new CompletionAnalyzer(plainAnalyzer());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(index))) {
      Document d1 = new Document();
      d1.add(new SuggestField("suggest", "a b", 10)); // analyzed as "a b" and "a xb"
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(new SuggestField("suggest", "a xc", 3));
      w.addDocument(d2);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        SuggestIndexSearcher s = new SuggestIndexSearcher(r);
        PrefixCompletionQuery q = new PrefixCompletionQuery(query, new Term("suggest", "a"));
        TopSuggestDocs td = s.suggest(q, num, skipDuplicates);
        List<String> out = new ArrayList<>();
        for (TopSuggestDocs.SuggestScoreDoc sd : td.scoreLookupDocs()) {
          out.add(sd.key + "/" + (int) sd.score);
        }
        return out;
      }
    } finally {
      index.close();
      query.close();
    }
  }

  /** Control: with room for 3, both distinct suggestions come back. */
  public void testControlNum3() throws Exception {
    assertEquals(List.of("a b/10", "a xc/3"), suggest(3, true));
  }

  /** num=2: the two distinct suggestions should be returned. */
  public void testNum2SkipDuplicates() throws Exception {
    assertEquals(List.of("a b/10", "a xc/3"), suggest(2, true));
  }
}

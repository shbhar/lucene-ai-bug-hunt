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
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * RegexCompletionQuery "takes a regular expression as the prefix of the query term": a suggestion
 * matches when one of its prefixes matches the regular expression. The suggester's FST stores every
 * analyzed form followed by an internal end byte (0x00) and dedup bytes, and the regular expression
 * automaton is run over those bytes too, so "." or a negated class such as [^x] can match the end
 * byte: "foo" is returned for "foo." although no prefix of "foo" matches "foo.".
 */
public class TestRegexCompletionEndByte extends LuceneTestCase {

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = newIndexWriterConfig(random(), analyzer);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return completion;
              }
            };
          }
        });
    return iwc;
  }

  private List<String> suggest(String regex) throws Exception {
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> out = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(analyzer))) {
      String[] values = {"foo", "food", "fox"};
      int[] weights = {5, 1, 2};
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new SuggestField("suggest_field", values[i], weights[i]));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        RegexCompletionQuery query = new RegexCompletionQuery(new Term("suggest_field", regex));
        for (SuggestScoreDoc hit : searcher.suggest(query, 10, false).scoreLookupDocs()) {
          out.add(hit.key.toString());
        }
      }
    }
    return out;
  }

  public void testControl() throws Exception {
    // "fo." matches the prefixes "foo", "foo" (of "food") and "fox"
    assertEquals(List.of("foo", "fox", "food"), suggest("fo."));
  }

  public void testDotAfterWholeSuggestion() throws Exception {
    // only "food" has a prefix of four characters starting with "foo"
    assertEquals(List.of("food"), suggest("foo."));
  }

  public void testNegatedClassAfterWholeSuggestion() throws Exception {
    // only "food" continues "foo" with a character other than 'x'
    assertEquals(List.of("food"), suggest("foo[^x]"));
  }

  public void testTwoDotsAfterShorterSuggestion() throws Exception {
    // no suggestion has five characters
    assertEquals(List.of(), suggest("foo.."));
  }
}

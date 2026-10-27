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
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * RegexCompletionQuery "takes a regular expression as the prefix of the query term", so a
 * suggestion matches when some prefix of it matches the regular expression. A '.' or a negated
 * class must therefore match a real character of the suggestion, never run past its end.
 */
public class TestRegexCompletionPastEndOfSuggestion extends LuceneTestCase {

  private static final String[] FOO = {"foo", "food", "fox"};
  private static final int[] FOO_WEIGHTS = {5, 1, 2};

  /** Indexes one document per value into one segment and returns the suggested keys. */
  private List<String> suggest(String[] values, int[] weights, String regex, int num)
      throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec codec = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(codec.getName(), codec) {
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
    List<String> keys = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new SuggestField("suggest_field", values[i], weights[i]));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        RegexCompletionQuery query = new RegexCompletionQuery(new Term("suggest_field", regex));
        for (SuggestScoreDoc hit : searcher.suggest(query, num, false).scoreLookupDocs()) {
          keys.add(hit.key.toString());
        }
      }
    }
    analyzer.close();
    return keys;
  }

  // controls: these pass with and without the bug

  public void testControlDotInsideSuggestion() throws Exception {
    assertEquals(List.of("foo", "fox", "food"), suggest(FOO, FOO_WEIGHTS, "fo.", 10));
  }

  public void testControlZeroWidthTail() throws Exception {
    assertEquals(List.of("foo", "food"), suggest(FOO, FOO_WEIGHTS, "foo.*", 10));
  }

  public void testControlThreeCopies() throws Exception {
    String[] values = {"foo", "foo", "foo"};
    assertEquals(List.of(), suggest(values, new int[] {3, 2, 1}, "foo..", 10));
  }

  // the bug: the pattern runs past the end of a shorter suggestion

  public void testDotNeedsARealCharacter() throws Exception {
    // only "food" has four characters starting with "foo"
    assertEquals(List.of("food"), suggest(FOO, FOO_WEIGHTS, "foo.", 10));
  }

  public void testNegatedClassNeedsARealCharacter() throws Exception {
    // only "food" continues "foo" with a character other than 'x'
    assertEquals(List.of("food"), suggest(FOO, FOO_WEIGHTS, "foo[^x]", 10));
  }

  public void testTopOneIsTheOnlyMatch() throws Exception {
    // "foo" (weight 5) must not take the only slot from "food" (weight 1)
    assertEquals(List.of("food"), suggest(FOO, FOO_WEIGHTS, "foo.", 1));
  }

  public void testPatternLongerThanEverySuggestion() throws Exception {
    // no suggestion has five characters
    assertEquals(List.of(), suggest(FOO, FOO_WEIGHTS, "foo..", 10));
  }

  public void testManyCopiesOfShortSuggestion() throws Exception {
    // five entries with the same text in one segment: still no suggestion has five characters
    String[] values = {"foo", "foo", "foo", "foo", "foo"};
    assertEquals(List.of(), suggest(values, new int[] {5, 4, 3, 2, 1}, "foo..", 10));
  }
}

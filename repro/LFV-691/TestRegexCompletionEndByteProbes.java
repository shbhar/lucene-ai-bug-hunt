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
import org.apache.lucene.document.Field;
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
 * Probes beyond the minimal repro: the same overrun inside a ContextQuery, and two checks of what
 * a fix must keep (a '.' still matches the separator between the tokens of a multi-token
 * suggestion; a prefix query is not affected).
 */
public class TestRegexCompletionEndByteProbes extends LuceneTestCase {

  private interface FieldMaker {
    Field make(String value, int weight);
  }

  private List<String> suggest(
      String[] values, int[] weights, FieldMaker maker, CompletionQuery query, int num)
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
        doc.add(maker.make(values[i], weights[i]));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        for (SuggestScoreDoc hit : searcher.suggest(query, num, false).scoreLookupDocs()) {
          keys.add(hit.key.toString());
        }
      }
    }
    analyzer.close();
    return keys;
  }

  private static final String[] FOO = {"foo", "food", "fox"};
  private static final int[] FOO_WEIGHTS = {5, 1, 2};

  private static Term term(String regex) {
    return new Term("suggest_field", regex);
  }

  public void testContextQueryOneContext() throws Exception {
    ContextQuery query = new ContextQuery(new RegexCompletionQuery(term("foo.")));
    query.addContext("c");
    List<String> keys =
        suggest(
            FOO, FOO_WEIGHTS, (v, wt) -> new ContextSuggestField("suggest_field", v, wt, "c"), query,
            10);
    assertEquals(List.of("food"), keys);
  }

  public void testContextQueryAllContexts() throws Exception {
    ContextQuery query = new ContextQuery(new RegexCompletionQuery(term("foo.")));
    List<String> keys =
        suggest(
            FOO, FOO_WEIGHTS, (v, wt) -> new ContextSuggestField("suggest_field", v, wt, "c"), query,
            10);
    assertEquals(List.of("food"), keys);
  }

  public void testKeepDotOverTokenSeparator() throws Exception {
    // "foo bar" has a fourth character (the space, indexed as the token separator): it matches
    String[] values = {"foo", "foo bar"};
    List<String> keys =
        suggest(
            values,
            new int[] {5, 3},
            (v, wt) -> new SuggestField("suggest_field", v, wt),
            new RegexCompletionQuery(term("foo.")),
            10);
    assertEquals(List.of("foo bar"), keys);
  }

  public void testKeepPrefixQuery() throws Exception {
    List<String> keys =
        suggest(
            FOO,
            FOO_WEIGHTS,
            (v, wt) -> new SuggestField("suggest_field", v, wt),
            new PrefixCompletionQuery(new MockAnalyzer(random()), term("foo")),
            10);
    assertEquals(List.of("foo", "food"), keys);
  }
}

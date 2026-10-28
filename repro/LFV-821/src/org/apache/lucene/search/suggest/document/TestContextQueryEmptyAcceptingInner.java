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
import org.apache.lucene.analysis.core.KeywordAnalyzer;
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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * A completion query whose automaton accepts the empty string (the regex "z*", or a fuzzy prefix
 * whose edit budget covers the whole query) matches every suggestion of a plain SuggestField. Run
 * against a ContextSuggestField (where it is rewritten into a ContextQuery over all contexts), the
 * same query fails with "AssertionError: input should not end with the context separator" when
 * assertions are enabled (the LUCENE-8287 assertion; that fix only made an empty regex text match
 * nothing).
 */
public class TestContextQueryEmptyAcceptingInner extends LuceneTestCase {

  private static PostingsFormat completionFormat() {
    for (String name :
        new String[] {
          "Completion104", "Completion101", "Completion912", "Completion99", "Completion90"
        }) {
      try {
        return PostingsFormat.forName(name);
      } catch (IllegalArgumentException _) {
        // not in this version
      }
    }
    throw new AssertionError("no completion postings format found");
  }

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    final PostingsFormat completion = completionFormat();
    final Codec delegate = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(delegate.getName(), delegate) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (field.equals("suggest_field")) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  private static List<String> keys(Directory dir, CompletionQuery query) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      List<String> keys = new ArrayList<>();
      for (TopSuggestDocs.SuggestScoreDoc sd :
          new SuggestIndexSearcher(reader).suggest(query, 10, false).scoreLookupDocs()) {
        keys.add(sd.key.toString());
      }
      return keys;
    }
  }

  private void check(CompletionQuery onPlain, CompletionQuery onContext) throws Exception {
    Analyzer analyzer = new KeywordAnalyzer();
    try (Directory plain = newDirectory();
        Directory withContexts = newDirectory()) {
      try (IndexWriter w = new IndexWriter(plain, iwc(analyzer))) {
        for (String[] s : new String[][] {{"abc", "3"}, {"xyz", "2"}}) {
          Document doc = new Document();
          doc.add(new SuggestField("suggest_field", s[0], Integer.parseInt(s[1])));
          w.addDocument(doc);
        }
      }
      try (IndexWriter w = new IndexWriter(withContexts, iwc(analyzer))) {
        for (String[] s : new String[][] {{"abc", "3"}, {"xyz", "2"}}) {
          Document doc = new Document();
          doc.add(new ContextSuggestField("suggest_field", s[0], Integer.parseInt(s[1]), "c"));
          w.addDocument(doc);
        }
      }
      List<String> expected = keys(plain, onPlain);
      assertEquals(List.of("abc", "xyz"), expected);
      List<String> actual;
      try {
        actual = keys(withContexts, onContext);
      } catch (AssertionError e) {
        throw new AssertionError(
            "the same query on a context-enabled field must not trip an assertion; plain field"
                + " returned "
                + expected,
            e);
      }
      assertEquals(expected, actual);
    }
  }

  public void testRegexThatAcceptsTheEmptyString() throws Exception {
    // sanity: the pattern is non-empty text but its automaton accepts ""
    assertTrue(
        Operations.run(
            Operations.determinize(
                new RegExp("z*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT),
            ""));
    check(
        new RegexCompletionQuery(new Term("suggest_field", "z*")),
        new RegexCompletionQuery(new Term("suggest_field", "z*")));
  }

  public void testFuzzyPrefixWithinTheEditBudget() throws Exception {
    // "zz" with maxEdits 2, no non-fuzzy prefix and no minimum length: "" is 2 edits away
    check(
        new FuzzyCompletionQuery(
            new KeywordAnalyzer(), new Term("suggest_field", "zz"), null, 2, true, 0, 0, false,
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT),
        new FuzzyCompletionQuery(
            new KeywordAnalyzer(), new Term("suggest_field", "zz"), null, 2, true, 0, 0, false,
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
  }
}

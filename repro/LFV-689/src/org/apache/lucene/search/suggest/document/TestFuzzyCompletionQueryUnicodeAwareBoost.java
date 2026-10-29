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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;

public class TestFuzzyCompletionQueryUnicodeAwareBoost extends LuceneTestCase {

  public void testOneNonAsciiLetter() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(List.of(new SuggestField("suggest", "aécd", 3)));
      w.addDocument(List.of(new SuggestField("suggest", "axcd", 4)));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        boolean unicodeAware = true;
        FuzzyCompletionQuery query =
            new FuzzyCompletionQuery(
                analyzer,
                new Term("suggest", "aéc"),
                null,
                FuzzyCompletionQuery.DEFAULT_MAX_EDITS,
                FuzzyCompletionQuery.DEFAULT_TRANSPOSITIONS,
                FuzzyCompletionQuery.DEFAULT_NON_FUZZY_PREFIX,
                FuzzyCompletionQuery.DEFAULT_MIN_FUZZY_LENGTH,
                unicodeAware,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        TopSuggestDocs hits = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        // expected as for "abc" over abcd/3, axcd/4: [aécd/6.0, axcd/4.0]; actual: aécd/3.0 last
        assertEquals(
            List.of("aécd/6.0", "axcd/4.0"),
            Arrays.stream(hits.scoreLookupDocs()).map(d -> d.key + "/" + d.score).toList());
      }
    }
  }
}

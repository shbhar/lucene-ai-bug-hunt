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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;

public class TestFuzzyCompletionQueryMaxEdits extends LuceneTestCase {

  // Expected: maxEdits=3 is rejected with IllegalArgumentException (like
  // FuzzySuggester/FuzzyQuery); actual: suggest() throws NullPointerException.
  public void testMaxEditsAboveSupportedDistance() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = newIndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
    try (Directory dir = newDirectory();
        IndexWriter iw = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new SuggestField("suggest", "lucene", 1));
      iw.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(iw)) {
        FuzzyCompletionQuery query;
        try {
          query =
              new FuzzyCompletionQuery(
                  analyzer,
                  new Term("suggest", "lucene"),
                  null,
                  3,
                  true,
                  1,
                  3,
                  false,
                  Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        } catch (IllegalArgumentException _) {
          return;
        }
        new SuggestIndexSearcher(reader).suggest(query, 1, false);
      }
    }
  }
}

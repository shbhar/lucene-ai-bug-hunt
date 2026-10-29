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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestContextSuggestFieldHoleCharContext extends LuceneTestCase {

  public void testHoleCharContextIsNotMergedIntoAnotherContext() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
    try (Directory dir = newDirectory();
        IndexWriter iw = new IndexWriter(dir, iwc)) {
      String[] contexts = {"ab", "a\u001Eb"};
      for (int i = 0; i < contexts.length; i++) {
        Document doc = new Document();
        doc.add(new ContextSuggestField("suggest", "s" + i, 2 - i, contexts[i]));
        try {
          iw.addDocument(doc);
        } catch (IllegalArgumentException documentedRejectionOfReservedChar) {
          return;
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(iw)) {
        ContextQuery query =
            new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest", "s")));
        query.addContext("ab");
        List<String> keys = new ArrayList<>();
        for (TopSuggestDocs.SuggestScoreDoc hit :
            new SuggestIndexSearcher(reader).suggest(query, 10, false).scoreLookupDocs()) {
          keys.add(hit.key + "@" + hit.context);
        }
        // expected: "a\u001Eb" rejected (@throws) or only [s0@ab]; actual: [s0@ab, s1@ab]
        assertEquals(List.of("s0@ab"), keys);
      }
    }
  }
}

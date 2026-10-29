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
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNRTSuggesterOneDeletion extends LuceneTestCase {

  public void testDeletedTopCompletionIsReplaced() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(
        new Lucene104Codec() {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return completion;
          }
        });
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new IntPoint("id", i));
          doc.add(new SuggestField("suggest", "abc_" + i, 100 - i));
          w.addDocument(doc);
        }
        w.deleteDocuments(IntPoint.newExactQuery("id", 0));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        PrefixCompletionQuery query =
            new PrefixCompletionQuery(analyzer, new Term("suggest", "abc_"));
        List<String> keys = new ArrayList<>();
        for (TopSuggestDocs.SuggestScoreDoc hit :
            new SuggestIndexSearcher(reader).suggest(query, 1, false).scoreLookupDocs()) {
          keys.add(hit.key.toString());
        }
        // one segment, abc_0 (best of 10) deleted: expected [abc_1], actual []
        assertEquals(List.of("abc_1"), keys);
      }
    }
  }
}

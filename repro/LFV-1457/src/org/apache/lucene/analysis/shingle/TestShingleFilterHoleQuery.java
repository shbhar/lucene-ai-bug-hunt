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
package org.apache.lucene.analysis.shingle;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

public class TestShingleFilterHoleQuery extends LuceneTestCase {

  public void testQueryStartingWithStopWordFindsItsContentWord() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String field) {
            Tokenizer t = new WhitespaceTokenizer();
            CharArraySet stop = new CharArraySet(List.of("the"), false);
            return new TokenStreamComponents(t, new ShingleFilter(new StopFilter(t, stop)));
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "lord", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new QueryBuilder(a).createBooleanQuery("body", "the lord");
        // expected: "the lord" matches the document "lord"; actual: query is body:_ lord, 0 hits
        assertEquals(q.toString(), 1, newSearcher(r).count(q));
      }
    }
    a.close();
  }
}

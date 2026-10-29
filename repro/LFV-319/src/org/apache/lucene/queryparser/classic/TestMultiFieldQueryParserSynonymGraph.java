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
package org.apache.lucene.queryparser.classic;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiFieldQueryParserSynonymGraph extends LuceneTestCase {

  public void testMultiWordSynonymUnderAnd() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
            return new TokenStreamComponents(tokenizer, new MockSynonymFilter(tokenizer));
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("body", "guinea pig", Field.Store.NO));
        w.addDocument(doc);
      }
      QueryParser single = new QueryParser("body", analyzer);
      single.setDefaultOperator(QueryParser.Operator.AND);
      single.setSplitOnWhitespace(true);
      single.setAutoGeneratePhraseQueries(true);
      MultiFieldQueryParser multi = new MultiFieldQueryParser(new String[] {"body"}, analyzer);
      multi.setDefaultOperator(QueryParser.Operator.AND);
      multi.setSplitOnWhitespace(true);
      multi.setAutoGeneratePhraseQueries(true);
      Query expected = single.parse("guinea-pig");
      Query actual = multi.parse("guinea-pig");
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // expected: MultiFieldQueryParser{body} finds the 1 doc QueryParser(body) finds; actual: 0
        assertEquals(
            expected + " vs " + actual, searcher.count(expected), searcher.count(actual));
      }
    }
  }
}

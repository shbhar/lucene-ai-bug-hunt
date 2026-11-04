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
package org.apache.lucene.queryparser.complexPhrase;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestComplexPhraseQueryEqualsIgnoresAnalysis extends LuceneTestCase {

  public void testDisMaxOfSamePhraseParsedWithTwoAnalyzers() throws Exception {
    Analyzer caseSensitive =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new StandardTokenizer());
          }
        };
    Query exact = new ComplexPhraseQueryParser("body", caseSensitive).parse("\"Foo Bar\"");
    Query folded = new ComplexPhraseQueryParser("body", new StandardAnalyzer()).parse("\"Foo Bar\"");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("body", "foo bar", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(1, searcher.count(folded));
        // expected 1 (folded matches the doc); actual 0: the two queries compare equal, so dis_max runs only 'exact'
        assertEquals(1, searcher.count(new DisjunctionMaxQuery(List.of(exact, folded), 0f)));
      }
    }
  }
}

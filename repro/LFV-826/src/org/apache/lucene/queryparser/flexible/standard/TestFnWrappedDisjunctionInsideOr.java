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
package org.apache.lucene.queryparser.flexible.standard;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** The wrapped-disjunction rewrite gap reached from query text. */
public class TestFnWrappedDisjunctionInsideOr extends LuceneTestCase {

  public void testFnMaxWidthAlternativeInsideOr() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("f", "x b c d", Field.Store.NO));
      w.addDocument(doc);
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        StandardQueryParser qp = new StandardQueryParser(analyzer);
        assertEquals(
            1, searcher.count(qp.parse("fn:phrase(x fn:maxwidth(5 fn:or(\"b c\" b)) d)", "f")));
        assertEquals(
            "adding the alternative 'y' (in no document) must not remove the hit",
            1,
            searcher.count(qp.parse("fn:phrase(x fn:or(fn:maxwidth(5 fn:or(\"b c\" b)) y) d)", "f")));
      }
    }
  }
}

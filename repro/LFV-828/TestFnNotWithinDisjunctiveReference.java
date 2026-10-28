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

/** The same contradiction reached from query text (fn:within / fn:notWithin of the flexible parser). */
public class TestFnNotWithinDisjunctiveReference extends LuceneTestCase {

  public void testFnWithinAndFnNotWithinBothMatch() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("f", "visit new york", Field.Store.NO));
      w.addDocument(doc);
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        StandardQueryParser qp = new StandardQueryParser(analyzer);
        // controls
        assertEquals(1, searcher.count(qp.parse("fn:within(visit 1 \"new york\")", "f")));
        assertEquals(0, searcher.count(qp.parse("fn:notWithin(visit 1 \"new york\")", "f")));
        assertEquals(1, searcher.count(qp.parse("fn:within(visit 1 fn:or(york \"new york\"))", "f")));
        // fn:notWithin is documented as "Matches intervals of the source that do not appear within
        // the provided number of positions from the intervals of the reference"
        assertEquals(
            "fn:notWithin(visit 1 fn:or(york \"new york\")) must not match a document that"
                + " fn:within(visit 1 fn:or(york \"new york\")) matches through the same 'visit'",
            0,
            searcher.count(qp.parse("fn:notWithin(visit 1 fn:or(york \"new york\"))", "f")));
      }
    }
  }
  public void testFnContainedByButNotFnOverlapping() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("f", "new york", Field.Store.NO));
      w.addDocument(doc);
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        StandardQueryParser qp = new StandardQueryParser(analyzer);
        assertEquals(1, searcher.count(qp.parse("fn:overlapping(new \"new york\")", "f")));
        assertEquals(1, searcher.count(qp.parse("fn:containedBy(new fn:or(york \"new york\"))", "f")));
        assertEquals(
            "fn:containedBy(new fn:or(york \"new york\")) matches, so fn:overlapping(new"
                + " fn:or(york \"new york\")) must match too",
            1,
            searcher.count(qp.parse("fn:overlapping(new fn:or(york \"new york\"))", "f")));
      }
    }
  }
}

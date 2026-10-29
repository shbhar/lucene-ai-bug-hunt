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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPositiveScoresOnlyCollectorWrapsNoScoreCollector extends LuceneTestCase {

  /** Indexes one document (f:x, n=0) and searches it through PositiveScoresOnlyCollector(in). */
  private static void searchOneDoc(Query query, Collector in) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "x", Field.Store.NO));
        doc.add(new NumericDocValuesField("n", 0));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        new IndexSearcher(reader).search(query, new PositiveScoresOnlyCollector(in));
      }
    }
  }

  public void testWrappedSortByFieldCollector() throws IOException {
    Sort byField = new Sort(new SortField("n", SortField.Type.LONG));
    TopFieldCollector sorted = new TopFieldCollectorManager(byField, 1, 1000).newCollector();
    searchOneDoc(new TermQuery(new Term("f", "x")), sorted);
    assertEquals(
        "hits (expected: the one match, its score is > 0)", 1, sorted.topDocs().scoreDocs.length);
  }

  public void testWrappedTotalHitCountCollector() throws IOException {
    TotalHitCountCollector counter = new TotalHitCountCollector();
    searchOneDoc(new BoostQuery(new TermQuery(new Term("f", "x")), 0f), counter);
    assertEquals("count (expected: none, the one match scores 0)", 0, counter.getTotalHits());
  }
}

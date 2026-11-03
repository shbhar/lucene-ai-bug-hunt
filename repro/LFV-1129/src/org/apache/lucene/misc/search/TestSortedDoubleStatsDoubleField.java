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
package org.apache.lucene.misc.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortedDoubleStatsDoubleField extends LuceneTestCase {

  public void testMinOfNegativeDoubleField() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (double v : new double[] {-5.0, 3.0}) {
        Document doc = new Document();
        doc.add(new DoubleField("f", v, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        DocValuesStats.SortedDoubleDocValuesStats stats =
            new IndexSearcher(reader)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new DocValuesStatsCollectorManager<>(
                        () -> new DocValuesStats.SortedDoubleDocValuesStats("f")));
        // expected min -5.0; actual -0.8749999999999999 (sortable bits decoded as raw IEEE bits)
        assertEquals(-5.0, stats.min(), 0.0);
      }
    }
  }
}

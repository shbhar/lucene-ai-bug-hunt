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
package org.apache.lucene.sandbox.facet.utils;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.DrillSideways;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDrillSidewaysOrchestratorMainResult extends LuceneTestCase {

  public void testCollectReturnsTheMainCollectorResult() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("price", 1));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FacetsConfig config = new FacetsConfig();
        DrillDownQuery query = new DrillDownQuery(config);
        query.add("price", NumericDocValuesField.newSlowExactQuery("price", 1));

        // expected: the TopDocs of the collector manager passed in; actual: ClassCastException
        TopDocs hits =
            new DrillSidewaysFacetOrchestrator()
                .addDrillSidewaysBuilder("price", new LongValueFacetBuilder("price"))
                .collect(
                    query,
                    new DrillSideways(new IndexSearcher(reader), config, null, null),
                    new TopScoreDocCollectorManager(1, 1));

        assertEquals(1, hits.totalHits.value());
      }
    }
  }
}

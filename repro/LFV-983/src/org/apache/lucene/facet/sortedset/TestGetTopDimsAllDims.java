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
package org.apache.lucene.facet.sortedset;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGetTopDimsAllDims extends LuceneTestCase {

  public void testGetTopDimsMaxValueReturnsAllDims() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("a", "x"));
      doc.add(new SortedSetDocValuesFacetField("b", "y"));
      w.addDocument(config.build(doc));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        FacetsCollector fc =
            new IndexSearcher(reader).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(reader, config), fc);
        List<FacetResult> allDims = facets.getAllDims(10);
        try {
          assertEquals(allDims, facets.getTopDims(Integer.MAX_VALUE, 10));
        } catch (IllegalArgumentException e) {
          // expected: the 2 dims of getAllDims(10); actual: IllegalArgumentException from the queue
          throw new AssertionError("expected getAllDims(10) = " + allDims + ", but: " + e, e);
        }
      }
    }
  }
}

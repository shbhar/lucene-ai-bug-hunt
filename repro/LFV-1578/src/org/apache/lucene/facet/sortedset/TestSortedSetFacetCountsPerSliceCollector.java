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

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortedSetFacetCountsPerSliceCollector extends LuceneTestCase {

  // Six one-document segments (a=x five times, then a=z), two default slices (five segments, then
  // one), each slice's FacetsCollector counted by itself: expected x=5 z=1, actual x=6 z=0.
  public void testCountsOfPerSliceCollectorsAddUp() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = newDirectory();
        IndexWriter writer =
            new IndexWriter(
                dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 6; i++) {
        Document doc = new Document();
        doc.add(new SortedSetDocValuesFacetField("a", i < 5 ? "x" : "z"));
        writer.addDocument(config.build(doc));
        writer.commit();
      }

      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = new IndexSearcher(reader, Runnable::run);
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);

        int[] counts =
            searcher.search(
                MatchAllDocsQuery.INSTANCE,
                new CollectorManager<FacetsCollector, int[]>() {
                  @Override
                  public FacetsCollector newCollector() {
                    return new FacetsCollector();
                  }

                  @Override
                  public int[] reduce(Collection<FacetsCollector> collectors) throws IOException {
                    int[] sum = new int[2];
                    for (FacetsCollector hits : collectors) {
                      Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
                      sum[0] += facets.getSpecificValue("a", "x").intValue();
                      sum[1] += facets.getSpecificValue("a", "z").intValue();
                    }
                    return sum;
                  }
                });

        assertEquals("x=5 z=1", "x=" + counts[0] + " z=" + counts[1]);
      }
    }
  }
}

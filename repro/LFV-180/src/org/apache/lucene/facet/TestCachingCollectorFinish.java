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
package org.apache.lucene.facet;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.CachingCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCachingCollectorFinish extends LuceneTestCase {

  public void testFacetsCollectorBehindScoreCache() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CachingCollectorManager<FacetsCollector, FacetsCollector> caching =
            new CachingCollectorManager<>(new FacetsCollectorManager(), true, null, 1000);
        FacetsCollector firstPass =
            new IndexSearcher(reader).search(new MatchAllDocsQuery(), caching);
        FacetsCollector replayed = caching.replay(new FacetsCollectorManager());
        // Expected: first pass and scored replay each see the 1 hit; actual: [0, 0] (no finish()).
        assertEquals(List.of(1, 1), List.of(hits(firstPass), hits(replayed)));
      }
    }
  }

  private static int hits(FacetsCollector fc) {
    return fc.getMatchingDocs().stream().mapToInt(FacetsCollector.MatchingDocs::totalHits).sum();
  }
}

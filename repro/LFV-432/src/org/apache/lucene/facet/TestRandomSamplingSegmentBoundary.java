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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRandomSamplingSegmentBoundary extends LuceneTestCase {

  public void testBinCrossingSegmentBoundaryIsSampled() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(new Document());
        w.commit();
        for (int i = 0; i < 3; i++) {
          w.addDocument(new Document());
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        RandomSamplingFacetsCollector c =
            new IndexSearcher(r)
                .search(
                    MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(2, 1));
        int sampled = 0;
        for (MatchingDocs md : c.getMatchingDocs()) {
          DocIdSetIterator it = md.bits().iterator();
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            sampled++;
          }
        }
        // bins {0,1} {2,3}; seed 1 picks hit 1, the first of segment 2: expected 2, actual 1
        assertEquals("sampled hits", 2, sampled);
      }
    }
  }
}

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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RandomSamplingFacetsCollector samples one hit per bin of binSize consecutive hits and carries a
 * bin that straddles a segment boundary over to the next segment ("the document to sample is in
 * the next bin"). When the hit to sample is exactly the first hit of the next segment (randomIndex
 * == counter at the end of the segment), the carry-over is skipped and that bin gets no sample.
 */
public class TestRandomSamplingSegmentBoundary extends LuceneTestCase {

  public void testEveryBinIsSampledAcrossSegments() throws Exception {
    try (Directory dir = newDirectory()) {
      // 4 hits in two segments [doc 0 | docs 1, 2, 3]; sampleSize 2 -> binSize 2, bins {0,1} {2,3}
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < 4; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          w.addDocument(doc);
          if (i == 0) {
            w.commit();
          }
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.leaves().size());
        IndexSearcher searcher = new IndexSearcher(r);
        StringBuilder bad = new StringBuilder();
        for (long seed = 1; seed <= 20; seed++) {
          RandomSamplingFacetsCollector c =
              searcher.search(
                  MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(2, seed));
          boolean[] sampled = new boolean[4];
          for (MatchingDocs md : c.getMatchingDocs()) {
            DocIdSetIterator it = md.bits().iterator();
            for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
              sampled[md.context().docBase + d] = true;
            }
          }
          for (int bin = 0; bin < 2; bin++) {
            int k = (sampled[2 * bin] ? 1 : 0) + (sampled[2 * bin + 1] ? 1 : 0);
            if (k != 1) {
              bad.append("seed=").append(seed).append(" bin ").append(bin).append(" has ")
                  .append(k).append(" sampled hits; ");
            }
          }
        }
        assertEquals("every bin of 2 hits must contribute exactly one sampled hit", "", bad.toString());
      }
    }
  }
}

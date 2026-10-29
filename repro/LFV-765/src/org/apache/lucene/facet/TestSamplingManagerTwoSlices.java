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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSamplingManagerTwoSlices extends LuceneTestCase {

  public void testSampleSizeWithTwoSlices() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int segment = 0; segment < 2; segment++) {
        for (int i = 0; i < 1024; i++) {
          w.addDocument(new Document());
        }
        w.flush();
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher =
            new IndexSearcher(reader, Runnable::run) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, false);
              }
            };
        RandomSamplingFacetsCollector fc =
            searcher.search(
                MatchAllDocsQuery.INSTANCE, RandomSamplingFacetsCollector.createManager(128, 42));
        int sampled = 0;
        for (FacetsCollector.MatchingDocs md : fc.getMatchingDocs()) {
          for (DocIdSetIterator it = md.bits().iterator();
              it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS; ) {
            sampled++;
          }
        }
        // expected 128 sampled hits (2048 hits at the reported rate 1/16, as with one slice); actual 16
        assertEquals("sampled hits at rate " + fc.getSamplingRate(), 128, sampled);
      }
    }
  }
}

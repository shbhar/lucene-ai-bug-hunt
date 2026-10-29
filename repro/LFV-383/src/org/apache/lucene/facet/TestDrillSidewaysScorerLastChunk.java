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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FilterDocIdSetIterator;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDrillSidewaysScorerLastChunk extends LuceneTestCase {

  private static LeafCollector recorder(List<Integer> docs) {
    return new LeafCollector() {
      @Override
      public void setScorer(Scorable scorer) {}

      @Override
      public void collect(int doc) {
        docs.add(doc);
      }
    };
  }

  public void testUnionScoringLastChunk() throws Exception {
    int maxDoc = IndexWriter.MAX_DOCS;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // DrillSidewaysScorer reads only maxDoc() from the segment
        LeafReader segment =
            new FilterLeafReader(r.leaves().get(0).reader()) {
              @Override
              public int maxDoc() {
                return maxDoc;
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        List<Integer> hits = new ArrayList<>();
        long chunks = maxDoc / 2048 + 1;
        DocIdSetIterator base =
            new FilterDocIdSetIterator(DocIdSetIterator.range(maxDoc - 3, maxDoc)) {
              long calls;

              @Override
              public int docID() {
                if (++calls > 2 * chunks) {
                  throw new AssertionError(
                      "expected score() to return the last 3 docs after "
                          + chunks
                          + " chunks; actual: still looping after "
                          + calls
                          + " docID() calls, collected "
                          + hits);
                }
                return in.docID();
              }
            };
        DrillSidewaysScorer.DocsAndCost dim =
            new DrillSidewaysScorer.DocsAndCost(
                new ConstantScoreScorer(
                    1f, ScoreMode.COMPLETE, DocIdSetIterator.range(maxDoc - 3, maxDoc)),
                recorder(new ArrayList<>()));
        new DrillSidewaysScorer(
                segment.getContext(),
                new ConstantScoreScorer(1f, ScoreMode.COMPLETE, base),
                new DrillSidewaysScorer.DocsAndCost[] {dim},
                false)
            .score(recorder(hits), null, 0, DocIdSetIterator.NO_MORE_DOCS);
        assertEquals(List.of(maxDoc - 3, maxDoc - 2, maxDoc - 1), hits);
      }
    }
  }
}

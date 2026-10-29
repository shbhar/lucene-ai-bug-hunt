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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRelevanceSortMinCompetitiveScore extends LuceneTestCase {

  static class RecordingScorable extends Scorable {
    float score;
    float minCompetitiveScore;

    @Override
    public float score() {
      return score;
    }

    @Override
    public void setMinCompetitiveScore(float minScore) {
      minCompetitiveScore = minScore;
    }
  }

  public void testMinCompetitiveScoreSetOnceThresholdIsExceeded() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafCollector leaf =
            new TopFieldCollectorManager(Sort.RELEVANCE, 1, 1)
                .newCollector()
                .getLeafCollector(reader.leaves().get(0));
        RecordingScorable scorer = new RecordingScorable();
        leaf.setScorer(scorer);
        scorer.score = 2;
        leaf.collect(0);
        scorer.score = 1;
        leaf.collect(1);
        // 2 hits > totalHitsThreshold 1, queue full: expected min score >= 2 (bottom), actual 0
        assertTrue(
            "expected min competitive score >= 2, got " + scorer.minCompetitiveScore,
            scorer.minCompetitiveScore >= 2f);
      }
    }
  }
}

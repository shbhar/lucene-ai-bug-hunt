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
package org.apache.lucene.search.join;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.RamUsageTester;
import org.apache.lucene.util.Accountable;

/**
 * A numeric join with scores (PointInSetIncludingScoreQuery) keeps one float per join value in
 * aggregatedJoinScores, but its ramBytesUsed() (what LRUQueryCache charges for it) leaves that
 * list out. The string-field join (TermsIncludingScoreQuery) counts its score array.
 */
public class TestNumericScoreJoinRamBytesUsed extends LuceneTestCase {

  private static long retained(Object o) {
    return RamUsageTester.ramUsed(
        o,
        new RamUsageTester.Accumulator() {
          @Override
          public long accumulateObject(
              Object obj, long shallowSize, Map<Field, Object> fieldValues, Collection<Object> queue) {
            for (Object v : fieldValues.values()) {
              if (v != null && v instanceof IndexReader == false) {
                queue.add(v);
              }
            }
            return shallowSize;
          }
        });
  }

  public void testRamBytesUsedCountsTheScores() throws Exception {
    final int n = 100_000;
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < n; i++) {
          Document d = new Document();
          d.add(new NumericDocValuesField("fromId", i));
          d.add(new IntPoint("toId", i));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query scoring =
            JoinUtil.createJoinQuery(
                "fromId", false, "toId", Integer.class, new MatchAllDocsQuery(), searcher, ScoreMode.Max);
        Query nonScoring =
            JoinUtil.createJoinQuery(
                "fromId", false, "toId", Integer.class, new MatchAllDocsQuery(), searcher, ScoreMode.None);
        assertEquals(n, searcher.count(scoring));
        long scoringReported = ((Accountable) scoring).ramBytesUsed();
        long scoringRetained = retained(scoring);
        long nonScoringReported = ((Accountable) nonScoring).ramBytesUsed();
        long nonScoringRetained = retained(nonScoring);
        String msg =
            n
                + " join values; ScoreMode.Max: ramBytesUsed="
                + scoringReported
                + " retained="
                + scoringRetained
                + "; ScoreMode.None: ramBytesUsed="
                + nonScoringReported
                + " retained="
                + nonScoringRetained;
        // Accountable#ramBytesUsed is an estimate of the heap this object uses: allow a factor of 2
        assertTrue(msg, 2 * scoringReported >= scoringRetained);
      }
    }
  }
}

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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBooleanScorerLastWindow extends LuceneTestCase {

  public void testTwoClausesInLastWindow() throws Exception {
    int doc = IndexWriter.MAX_DOCS - 100;
    List<Scorer> clauses = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      DocIdSetIterator it = DocIdSetIterator.range(doc, doc + 1);
      clauses.add(new ConstantScoreScorer(1f, ScoreMode.COMPLETE_NO_SCORES, it));
    }
    List<Integer> collected = new ArrayList<>();
    LeafCollector collector =
        new LeafCollector() {
          int setScorerCalls;

          @Override
          public void setScorer(Scorable scorer) {
            assertTrue(
                "score() makes no progress: expected to collect [" + doc + "], got " + collected,
                ++setScorerCalls < 100);
          }

          @Override
          public void collect(int d) {
            collected.add(d);
          }
        };
    // expected: collects [doc] and returns; actual: loops forever, its window end 2^31 overflows
    new BooleanScorer(clauses, 1, false)
        .score(collector, null, 0, DocIdSetIterator.NO_MORE_DOCS);
    assertEquals(List.of(doc), collected);
  }
}

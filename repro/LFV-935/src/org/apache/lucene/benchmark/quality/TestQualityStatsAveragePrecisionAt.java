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
package org.apache.lucene.benchmark.quality;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQualityStatsAveragePrecisionAt extends LuceneTestCase {

  private static QualityStats allRelevant(int hits) {
    QualityStats stats = new QualityStats(hits, 0);
    for (int rank = 1; rank <= hits; rank++) {
      stats.addResult(rank, true, 0);
    }
    return stats;
  }

  public void testAveragePrecisionAtIsMeanOfPerQueryValues() {
    QualityStats avg = QualityStats.average(new QualityStats[] {allRelevant(10), allRelevant(2)});
    // expected the mean of the per-query P@10 values (1.0 + 0.2) / 2 = 0.6; actual 0.4
    assertEquals(0.6, avg.getPrecisionAt(10), 1e-9);
  }
}

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
import java.util.List;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReqExclLastWindow extends LuceneTestCase {

  /** Required: the last 100 doc IDs of a MAX_DOCS segment. Excluded: one of them. */
  public void testLastDocsOfMaxDocsSegment() throws IOException {
    int maxDoc = IndexWriter.MAX_DOCS; // 2,147,483,519
    int base = maxDoc - 100;
    BulkScorer required =
        new DenseConjunctionBulkScorer(
            List.of(DocIdSetIterator.range(base, maxDoc)), List.of(), maxDoc, 1f);
    BulkScorer reqExcl =
        new ReqExclBulkScorer(required, DocIdSetIterator.range(base + 50, base + 51));
    int[] hits = new int[1];
    reqExcl.score(
        new LeafCollector() {
          @Override
          public void setScorer(Scorable scorer) {}

          @Override
          public void collect(int doc) {
            hits[0]++;
          }
        },
        null,
        0,
        maxDoc);
    assertEquals(99, hits[0]);
  }
}

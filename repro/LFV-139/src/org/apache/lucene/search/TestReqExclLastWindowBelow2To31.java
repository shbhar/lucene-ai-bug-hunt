/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ReqExclBulkScorer passes a WindowBits instance as acceptDocs to the required clause's bulk
 * scorer. WindowBits#applyMask computes {@code Math.min(NO_MORE_DOCS, offset + bitSet.length())} in
 * int arithmetic, so when the bulk scorer masks a 4096-bit window whose base is above 2^31 - 4097
 * the sum wraps negative and the whole window is cleared. Package-private classes, driven from a
 * test in the same package as Lucene's own TestReqExclBulkScorer does.
 */
public class TestReqExclLastWindowBelow2To31 extends LuceneTestCase {

  private static List<Integer> collect(BulkScorer scorer, int maxDoc) throws IOException {
    List<Integer> hits = new ArrayList<>();
    scorer.score(
        new LeafCollector() {
          @Override
          public void setScorer(Scorable scorer) {}

          @Override
          public void collect(int doc) {
            hits.add(doc);
          }
        },
        null,
        0,
        maxDoc);
    return hits;
  }

  /**
   * Required: 100 consecutive docs [base, base + 100); excluded: base + 50. Expect 99 hits. The
   * required clause is a ConstantScoreBulkScorer (what a sparse constant-score clause such as a
   * TermQuery gets when scores are not needed).
   */
  private static void checkConstantScore(int base, int maxDoc) throws IOException {
    BulkScorer req =
        new ConstantScoreBulkScorer(
            1f, ScoreMode.COMPLETE_NO_SCORES, DocIdSetIterator.range(base, base + 100));
    BulkScorer reqExcl =
        new ReqExclBulkScorer(req, DocIdSetIterator.range(base + 50, base + 51));
    List<Integer> hits = collect(reqExcl, maxDoc);
    assertEquals("hits=" + hits, 99, hits.size());
  }

  /** Same with a DenseConjunctionBulkScorer as the required clause (dense constant-score clause). */
  private static void checkDense(int base, int maxDoc) throws IOException {
    BulkScorer req =
        new DenseConjunctionBulkScorer(
            List.of(DocIdSetIterator.range(base, base + 100)), List.of(), maxDoc, 1f);
    BulkScorer reqExcl =
        new ReqExclBulkScorer(req, DocIdSetIterator.range(base + 50, base + 51));
    List<Integer> hits = collect(reqExcl, maxDoc);
    assertEquals("hits=" + hits, 99, hits.size());
  }

  private static final int MAX_DOCS = IndexWriter.MAX_DOCS; // 2^31 - 129

  /** Control: the same docs one window lower. Passes. */
  public void testControlConstantScore() throws IOException {
    checkConstantScore(MAX_DOCS - 100 - 8192, MAX_DOCS);
  }

  /** Last docs of a MAX_DOCS segment. Fails on main: no hit is collected. */
  public void testLastDocsConstantScore() throws IOException {
    checkConstantScore(MAX_DOCS - 100, MAX_DOCS);
  }

  public void testControlDense() throws IOException {
    checkDense(MAX_DOCS - 100 - 8192, MAX_DOCS);
  }

  public void testLastDocsDense() throws IOException {
    checkDense(MAX_DOCS - 100, MAX_DOCS);
  }
}

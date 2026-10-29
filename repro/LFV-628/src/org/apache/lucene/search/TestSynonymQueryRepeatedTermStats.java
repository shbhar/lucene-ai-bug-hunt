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

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSynonymQueryRepeatedTermStats extends LuceneTestCase {

  public void testRepeatedTermStatsStayWithinFieldTotals() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(List.of(new TextField("f", "a", Field.Store.NO)));
        w.addDocument(List.of(new TextField("f", "a a b", Field.Store.NO)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        long[] seen = new long[2];
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(
            new Similarity() {
              @Override
              public SimScorer scorer(float boost, FieldStats fieldStats, TermStats... termStats) {
                seen[0] = termStats[0].totalTermFreq();
                seen[1] = fieldStats.sumTotalTermFreq();
                return new SimScorer() {
                  @Override
                  public float score(float freq, long norm) {
                    return freq;
                  }
                };
              }
            });
        Term a = new Term("f", "a");
        searcher.search(new SynonymQuery.Builder("f").addTerm(a).addTerm(a).build(), 10);
        // Expected (TermStats javadoc): totalTermFreq <= the field's sumTotalTermFreq (4); actual: 6.
        assertTrue(
            "pseudo-term totalTermFreq " + seen[0] + " > field sumTotalTermFreq " + seen[1],
            seen[0] <= seen[1]);
      }
    }
  }
}

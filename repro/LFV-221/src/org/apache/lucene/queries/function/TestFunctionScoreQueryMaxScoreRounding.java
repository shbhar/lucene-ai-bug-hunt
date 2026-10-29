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
package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFunctionScoreQueryMaxScoreRounding extends LuceneTestCase {

  public void testBoostedLongScoreWithinMaxScore() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(NumericDocValuesField.indexedField("count", (1L << 24) + 1));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query =
            new BoostQuery(
                new FunctionScoreQuery(
                    MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromLongField("count")),
                1.5f);
        Scorer scorer =
            searcher
                .createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
                .scorer(reader.leaves().get(0));
        float maxScore = scorer.getMaxScore(scorer.advanceShallow(0));
        assertEquals(0, scorer.iterator().nextDoc());
        float score = scorer.score();
        // expected: score <= getMaxScore; actual: 2.5165826E7 > 2.5165824E7
        assertTrue("score " + score + " > getMaxScore " + maxScore, score <= maxScore);
      }
    }
  }
}

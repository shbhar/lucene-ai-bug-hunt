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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMaxScoreRequiredClauseRounding extends LuceneTestCase {

  private static Query clause(String term, float boost) {
    return new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", term))), boost);
  }

  /** Scores of the docs that the query's bulk scorer collects under a fixed min score. */
  private static List<Float> collect(IndexSearcher searcher, Query query, float minScore)
      throws IOException {
    List<Float> scores = new ArrayList<>();
    Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f);
    BulkScorer bulkScorer = weight.bulkScorer(searcher.getIndexReader().leaves().get(0));
    bulkScorer.score(
        new LeafCollector() {
          Scorable scorer;

          @Override
          public void setScorer(Scorable scorer) throws IOException {
            this.scorer = scorer;
            scorer.setMinCompetitiveScore(minScore);
          }

          @Override
          public void collect(int doc) throws IOException {
            scores.add(scorer.score());
          }
        },
        null,
        0,
        DocIdSetIterator.NO_MORE_DOCS);
    return scores;
  }

  public void testDocScoringMinCompetitiveScoreIsCollected() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc0 = new Document();
      doc0.add(new StringField("f", "e", Field.Store.NO));
      doc0.add(new StringField("f", "a", Field.Store.NO));
      doc0.add(new StringField("f", "t", Field.Store.NO));
      w.addDocument(doc0);
      Document doc1 = new Document();
      doc1.add(new StringField("f", "j", Field.Store.NO));
      w.addDocument(doc1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query query =
            new BooleanQuery.Builder()
                .add(clause("e", 3f), Occur.SHOULD)
                .add(clause("j", 2f), Occur.SHOULD)
                .add(clause("a", 1f + 0x1p-22f), Occur.SHOULD)
                .add(clause("t", 0x1p-51f + 0x1p-70f), Occur.SHOULD)
                .build();
        List<Float> doc0Score = collect(searcher, query, 4f);
        // expected: doc 0 (score nextUp(4f)) is still collected at minScore = its score; actual: []
        assertEquals(
            "doc 0 must be collected at minCompetitiveScore = its own score",
            doc0Score,
            collect(searcher, query, doc0Score.get(0)));
      }
    }
  }
}

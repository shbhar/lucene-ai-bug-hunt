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

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PositiveScoresOnlyCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The other direction of the same bug: PositiveScoresOnlyCollector lets hits whose score is 0
 * through when the wrapped collector needs no scores. Without scores, FunctionScoreQuery uses the
 * plain MatchAllDocsQuery weight, which reports a constant score of 1 for every document.
 */
public class TestPositiveScoresOnlyCollectorFunctionScore extends LuceneTestCase {

  /** Records the global doc ids it is given; reports the ScoreMode passed to the constructor. */
  private static final class DocSetCollector extends SimpleCollector {
    final TreeSet<Integer> docs = new TreeSet<>();
    private final ScoreMode scoreMode;
    private int docBase;

    DocSetCollector(ScoreMode scoreMode) {
      this.scoreMode = scoreMode;
    }

    @Override
    protected void doSetNextReader(LeafReaderContext context) {
      docBase = context.docBase;
    }

    @Override
    public void collect(int doc) {
      docs.add(docBase + doc);
    }

    @Override
    public ScoreMode scoreMode() {
      return scoreMode;
    }
  }

  public void testZeroScoreHitsDoNotReachNoScoreCollector() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long v : new long[] {0, 1, 0, 2, 0, 3}) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("v", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        // the score of each document is its value of v: 0, 1, 0, 2, 0, 3
        Query query =
            new FunctionScoreQuery(new MatchAllDocsQuery(), DoubleValuesSource.fromLongField("v"));

        DocSetCollector scoring = new DocSetCollector(ScoreMode.COMPLETE);
        searcher.search(query, new PositiveScoresOnlyCollector(scoring));
        assertEquals("scoring collector", new TreeSet<>(List.of(1, 3, 5)), scoring.docs);

        DocSetCollector noScores = new DocSetCollector(ScoreMode.COMPLETE_NO_SCORES);
        searcher.search(query, new PositiveScoresOnlyCollector(noScores));
        assertEquals("collector that needs no scores", scoring.docs, noScores.docs);
      }
    }
  }
}

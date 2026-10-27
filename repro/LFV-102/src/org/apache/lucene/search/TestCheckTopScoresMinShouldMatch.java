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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * CheckHits.checkTopScores on a plain minimum-should-match disjunction (f:a f:b f:c)~2.
 *
 * <p>The query is correct in both score modes (top hits and every score agree), yet the checker
 * fails: doCheckMaxScores walks the two-phase approximations of the COMPLETE and the TOP_SCORES
 * scorer in lockstep and asserts they stop on the same doc ids. Approximations are only required
 * to be supersets of the matches, and WANDScorer's legitimately differ between the two modes here
 * (doc 0, which only contains "c", is on the TOP_SCORES approximation but not on the COMPLETE one).
 */
public class TestCheckTopScoresMinShouldMatch extends LuceneTestCase {

  public void testMinShouldMatchDisjunction() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        String[][] docs = {{"c"}, {"a", "b"}, {"c"}, {"a", "c"}, {"b"}, {"a", "b", "c"}};
        for (int rep = 0; rep < 50; ++rep) {
          for (String[] terms : docs) {
            Document d = new Document();
            for (String t : terms) d.add(new StringField("f", t, Field.Store.NO));
            w.addDocument(d);
          }
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("f", "b")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("f", "c")), BooleanClause.Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build();

        // The query itself is fine: same matches and scores in both score modes.
        LeafReaderContext ctx = r.leaves().get(0);
        Weight complete = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE, 1);
        ScorerSupplier ss = s.createWeight(s.rewrite(q), ScoreMode.TOP_SCORES, 1).scorerSupplier(ctx);
        ss.setTopLevelScoringClause();
        Scorer s1 = complete.scorer(ctx);
        Scorer s2 = ss.get(Long.MAX_VALUE);
        DocIdSetIterator it1 = s1.iterator(), it2 = s2.iterator();
        for (int d = it1.nextDoc(); ; d = it1.nextDoc()) {
          assertEquals(d, it2.nextDoc());
          if (d == DocIdSetIterator.NO_MORE_DOCS) break;
          assertEquals(s1.score(), s2.score(), 0f);
        }

        // ... but the upstream checker rejects it.
        CheckHits.checkTopScores(random(), q, s);
      }
    }
  }
}

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
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Two query positions with the same offset inside one repetition group of SloppyPhraseMatcher are
 * forced onto different document positions, although gatherRptGroups states that PPs with the same
 * offset are not repetitions of each other and ExactPhraseMatcher lets them share a position. For
 * MultiPhraseQuery the outcome of such a collision depends on HashSet iteration order (identity hash
 * codes), so the same query on the same index gives different results from one evaluation to the next.
 */
public class TestSloppyPhraseSameOffsetRepeats extends LuceneTestCase {

  private static Token tok(String term, int posInc) {
    Token t = new Token(term, 0, 1);
    t.setPositionIncrement(posInc);
    return t;
  }

  private static Directory index(Token... tokens) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document d = new Document();
      d.add(new TextField("f", new CannedTokenStream(tokens)));
      w.addDocument(d);
    }
    return dir;
  }

  /** PhraseQuery a@0 a@1 a@1 (the term at position 1 added twice) on the document "a a". */
  public void testPhraseQuerySameTermTwiceAtOnePosition() throws IOException {
    try (Directory dir = index(tok("a", 1), tok("a", 1));
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      PhraseQuery.Builder b = new PhraseQuery.Builder();
      b.add(new Term("f", "a"), 0).add(new Term("f", "a"), 1).add(new Term("f", "a"), 1);
      PhraseQuery exact = b.build();
      PhraseQuery sloppy = b.setSlop(1).build();
      assertEquals("slop 0 matches \"a a\"", 1, s.count(exact));
      // PhraseQuery#getSlop: slop is the maximum edit distance; slop 1 must match what slop 0 matches
      assertEquals("slop 1 must match too", 1, s.count(sloppy));
    }
  }

  private static float score(IndexSearcher s, Query q) throws IOException {
    Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE, 1f);
    LeafReaderContext ctx = s.getIndexReader().leaves().get(0);
    Scorer sc = w.scorer(ctx);
    if (sc == null || sc.iterator().nextDoc() != 0) return 0f;
    return sc.score();
  }

  /**
   * MultiPhraseQuery (a|b)@0, b@2, a@2 with slop 2 on a document a@0, a@1 b@1 (stacked), a@2. The
   * same query, evaluated repeatedly on the same reader, must give the same result.
   */
  public void testMultiPhraseSameOffsetIsDeterministic() throws IOException {
    try (Directory dir = index(tok("a", 1), tok("a", 1), tok("b", 0), tok("a", 1));
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Term a = new Term("f", "a"), bt = new Term("f", "b");
      Set<Float> scores = new TreeSet<>();
      for (int i = 0; i < 50; i++) {
        MultiPhraseQuery q =
            new MultiPhraseQuery.Builder()
                .add(new Term[] {a, bt}, 0)
                .add(new Term[] {bt}, 2)
                .add(new Term[] {a}, 2)
                .setSlop(2)
                .build();
        scores.add(score(s, q));
      }
      assertEquals("distinct scores over 50 evaluations of one query: " + scores, 1, scores.size());
    }
  }
}

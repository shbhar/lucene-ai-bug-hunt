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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockSynonymAnalyzer;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * A token stacked at one position (position increment 0) must not be dropped by {@link
 * Intervals#analyzedText} just because another word of the same text has a multi-word synonym.
 */
public class TestAnalyzedTextGraphStackedTokens extends LuceneTestCase {

  // MockSynonymAnalyzer: "guinea pig" -> "cavy" (spans two positions), "dogs" -> "dog" (stacked)
  private static final String[] DOCS = {
    "guinea pig dogs", "guinea pig dog", "cavy dogs", "cavy dog"
  };

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, new MockAnalyzer(random()));
    for (String text : DOCS) {
      Document doc = new Document();
      doc.add(newTextField("f", text, Field.Store.NO));
      w.addDocument(doc);
    }
    reader = w.getReader();
    w.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int count(IntervalsSource source) throws IOException {
    return searcher.count(new IntervalQuery("f", source));
  }

  public void testStackedSynonymNextToMultiWordSynonym() throws IOException {
    try (Analyzer analyzer = new MockSynonymAnalyzer()) {
      // control: QueryBuilder keeps both synonyms and finds all 4 documents
      Query phrase = new QueryBuilder(analyzer).createPhraseQuery("f", "guinea pig dogs");
      assertEquals(phrase.toString(), 4, searcher.count(phrase));
      // control: without a multi-word synonym in the text, analyzedText keeps or(dog, dogs)
      IntervalsSource noGraph = Intervals.analyzedText("pig dogs", analyzer, "f", 0, true);
      assertEquals(noGraph.toString(), 2, count(noGraph));

      IntervalsSource source = Intervals.analyzedText("guinea pig dogs", analyzer, "f", 0, true);
      assertEquals(source.toString(), 4, count(source));
    }
  }

  public void testStackedTokenInEveryMode() throws IOException {
    for (int maxGaps : new int[] {0, 1, -1}) {
      for (boolean ordered : new boolean[] {true, false}) {
        CannedTokenStream ts =
            new CannedTokenStream(
                token("guinea", 1, 1),
                token("cavy", 0, 2),
                token("pig", 1, 1),
                token("dogs", 1, 1),
                token("dog", 0, 1));
        IntervalsSource source = Intervals.analyzedText(ts, maxGaps, ordered);
        assertEquals("maxGaps=" + maxGaps + " ordered=" + ordered + " " + source, 4, count(source));
      }
    }
  }

  private static Token token(String term, int posInc, int posLength) {
    Token t = new Token(term, 0, term.length());
    t.setPositionIncrement(posInc);
    t.setPositionLength(posLength);
    return t;
  }
}

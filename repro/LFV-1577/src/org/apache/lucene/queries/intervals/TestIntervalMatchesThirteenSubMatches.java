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
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIntervalMatchesThirteenSubMatches extends LuceneTestCase {

  public void testOrderedOverThirteenTermPhrase() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        w.addDocument(List.of(new TextField("f", "a b c d e f g h i j k l m n", Field.Store.NO)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        IntervalsSource phrase =
            Intervals.phrase("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m");
        Query query = new IntervalQuery("f", Intervals.ordered(phrase, Intervals.term("n")));
        Weight weight =
            searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
        // expected: one match, positions 0 to 13; actual: ArrayIndexOutOfBoundsException
        MatchesIterator it = weight.matches(reader.leaves().get(0), 0).getMatches("f");
        assertTrue(it.next());
        assertEquals(0, it.startPosition());
        assertEquals(13, it.endPosition());
      }
    }
  }
}

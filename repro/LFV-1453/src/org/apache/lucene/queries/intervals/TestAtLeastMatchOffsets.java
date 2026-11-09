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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAtLeastMatchOffsets extends LuceneTestCase {

  public void testOffsetsFollowPositions() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new Field("f", "the quick brown", ft));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        IntervalsSource src =
            Intervals.atLeast(
                2,
                Intervals.term("the"),
                Intervals.phrase("quick", "brown"),
                Intervals.term("brown"));
        MatchesIterator mi =
            s.createWeight(new IntervalQuery("f", src), ScoreMode.COMPLETE_NO_SCORES, 1f)
                .matches(r.leaves().get(0), 0)
                .getMatches("f");
        assertTrue(mi.next());
        // expected: match "quick brown" = positions [1,2], offsets [4,15); actual: startOffset 0 ("the")
        assertEquals(1, mi.startPosition());
        assertEquals(2, mi.endPosition());
        assertEquals(4, mi.startOffset());
        assertEquals(15, mi.endOffset());
      }
    }
  }
}

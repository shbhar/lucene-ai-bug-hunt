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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Intervals.prefix(p, maxExpansions) "@throws IllegalStateException if the prefix expands to more
 * than maxExpansions terms". The scoring path does; the Weight#matches path lets maxExpansions + 1
 * terms through (post-increment in the check).
 */
public class TestMultiTermMatchesExpansionLimit extends LuceneTestCase {

  public void testMatchesHonoursMaxExpansions() throws IOException {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, new MockAnalyzer(random()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "pa pb pc", Field.Store.NO)); // 3 terms match p*
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        LeafReaderContext ctx = searcher.getIndexReader().leaves().get(0);
        IntervalQuery query = new IntervalQuery("f", Intervals.prefix(new BytesRef("p"), 2));

        // Control: searching expands to 3 > 2 terms and throws.
        expectThrows(IndexSearcher.TooManyClauses.class, () -> searcher.count(query));

        Weight weight =
            searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
        expectThrows(
            IndexSearcher.TooManyClauses.class,
            () -> {
              Matches m = weight.matches(ctx, 0);
              assertNull("unreachable if the limit holds", m);
            });
      }
    }
  }
}

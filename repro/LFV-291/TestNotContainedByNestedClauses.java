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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Fix-comparison probe, not part of the bug repro: an OR whose clauses nest. Without the rewrite,
 * minimization reduces or("x y z", y) to y, so "x" in "x y z" is kept (also on 8.0.0, before the
 * rewrite existed). The rewrite's stated goal is that the big source is not minimized, so x, which
 * lies inside "x y z", must be excluded.
 */
public class TestNotContainedByNestedClauses extends LuceneTestCase {

  private static final String FIELD = "f";

  public void testNestedClauses() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : new String[] {"x y z", "x q"}) {
          Document doc = new Document();
          doc.add(new TextField(FIELD, text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource x = Intervals.term("x");
        IntervalsSource big = Intervals.or(Intervals.phrase("x", "y", "z"), Intervals.term("y"));
        IntervalsSource source = Intervals.notContainedBy(x, big);
        // only "x q": x in "x y z" lies inside the clause "x y z"
        assertEquals(source.toString(), 1, searcher.count(new IntervalQuery(FIELD, source)));
      }
    }
  }
}

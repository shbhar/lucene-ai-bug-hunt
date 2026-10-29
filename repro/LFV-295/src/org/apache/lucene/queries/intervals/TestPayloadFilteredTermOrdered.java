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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestPayloadFilteredTermOrdered extends LuceneTestCase {

  public void testOrderedOfSameTermWithDifferentPayloadFilters() throws Exception {
    Token ax = new Token("a", 0, 1);
    ax.setPayload(new BytesRef("x"));
    Token ay = new Token("a", 2, 3);
    ay.setPayload(new BytesRef("y"));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("f", new CannedTokenStream(ax, ay)));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IntervalsSource source =
            Intervals.ordered(
                Intervals.term("a", new BytesRef("x")::equals),
                Intervals.term("a", new BytesRef("y")::equals));
        // the doc is "a|x a|y": expected 1 hit, actual 0 (the two sources compare equal)
        assertEquals(
            "hits of ordered(a|x, a|y)",
            1,
            new IndexSearcher(reader).count(new IntervalQuery("f", source)));
      }
    }
  }
}

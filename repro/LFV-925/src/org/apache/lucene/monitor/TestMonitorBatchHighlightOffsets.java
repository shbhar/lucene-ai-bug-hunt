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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * HighlightsMatch hits carry start and end offsets. For a document matched alone the Monitor
 * reports them; for the same document matched in a batch of two or more documents every offset is
 * -1, because the batch is indexed with the documents' own field types (TextField indexes no
 * offsets) while a single document goes into a MemoryIndex that stores offsets. The upstream test
 * TestHighlightingMatcher#testHighlightBatches expects offsets in a batch (Hit(1, 4, 2, 16)), but
 * its check uses TreeSet.contains, which compares hits by position only. Uses only public API.
 */
public class TestMonitorBatchHighlightOffsets extends LuceneTestCase {

  private static List<HighlightsMatch.Hit> hits(HighlightsMatch m, String field) {
    return new ArrayList<>(m.getHits(field)); // record components, compared with equals()
  }

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  /** Same document, same query: offsets alone vs offsets in a batch of two. */
  public void testOffsetsAloneAndInBatch() throws IOException {
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      monitor.register(new MonitorQuery("q", new PhraseQuery("f", "cell", "biology")));
      Document d0 = doc("the cell biology count");
      Document d1 = doc("nope");

      List<HighlightsMatch.Hit> alone =
          hits(monitor.match(d0, HighlightsMatch.MATCHER).matches("q"), "f");
      List<HighlightsMatch.Hit> inBatch =
          hits(monitor.match(new Document[] {d0, d1}, HighlightsMatch.MATCHER).matches("q", 0), "f");

      // alone: the phrase hit spans positions 1..2 and offsets 4..16
      assertEquals(List.of(new HighlightsMatch.Hit(1, 4, 2, 16)), alone);
      assertEquals("the batch should report the same hits, offsets included", alone, inBatch);
    }
  }

  /**
   * The upstream batch test's expectation, checked with Hit.equals instead of TreeSet.contains:
   * the batch hit should have start offset 4 and end offset 16 (same documents and phrase as that
   * test, which parses the phrase with the classic query parser).
   */
  public void testUpstreamBatchExpectationWithEquals() throws IOException {
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      monitor.register(new MonitorQuery("query1", new PhraseQuery("f", "cell", "biology")));
      Document doc1 = doc("the cell biology count");
      Document doc2 = doc("nope");
      Document doc3 = doc("biology text");
      MultiMatchingQueries<HighlightsMatch> matches =
          monitor.match(new Document[] {doc1, doc2, doc3}, HighlightsMatch.MATCHER);
      HighlightsMatch m1 = matches.matches("query1", 0);
      assertNotNull(m1);
      // TreeSet.contains compares positions only, so this passes whatever the offsets are:
      assertTrue(m1.getHits("f").contains(new HighlightsMatch.Hit(1, 4, 2, 16)));
      // the same expectation with record equality:
      assertEquals(List.of(new HighlightsMatch.Hit(1, 4, 2, 16)), new ArrayList<>(m1.getHits("f")));
    }
  }
}

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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestWithinNotWithinPartlyNear extends LuceneTestCase {

  public void testPhraseNextToReferenceIsWithinOrNotWithin() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new TextField("f", "the new york times", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(r);
        IntervalsSource newYork = Intervals.phrase("new", "york");
        IntervalsSource times = Intervals.term("times");
        int within = searcher.count(new IntervalQuery("f", Intervals.within(newYork, 1, times)));
        int notWithin =
            searcher.count(new IntervalQuery("f", Intervals.notWithin(newYork, 1, times)));
        // expected: exactly one of within / notWithin matches the doc; actual: neither (0 + 0)
        assertEquals("within + notWithin", 1, within + notWithin);
      }
    }
  }
}

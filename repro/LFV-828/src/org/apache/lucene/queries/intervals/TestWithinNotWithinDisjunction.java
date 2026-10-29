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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

public class TestWithinNotWithinDisjunction extends LuceneTestCase {

  public void testWithinAndNotWithinAreComplements() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new TextField("f", "visit new york", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        IntervalsSource visit = Intervals.term("visit");
        IntervalsSource ref = Intervals.or(Intervals.term("york"), Intervals.phrase("new", "york"));
        int within = searcher.count(new IntervalQuery("f", Intervals.within(visit, 1, ref)));
        int notWithin = searcher.count(new IntervalQuery("f", Intervals.notWithin(visit, 1, ref)));
        // expected: 'visit' is either within 1 position of ref or not (1 hit); actual: both (2)
        assertEquals("within=" + within + ", notWithin=" + notWithin, 1, within + notWithin);
      }
    }
  }
}

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

public class TestPhraseOverOrOfWrappedOr extends LuceneTestCase {

  public void testPhraseRewritesOrInsideMaxWidthInsideOr() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "x b c d", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IntervalsSource inner = Intervals.or(Intervals.phrase("b", "c"), Intervals.term("b"));
        IntervalsSource alt = Intervals.or(Intervals.maxwidth(5, inner), Intervals.term("y"));
        IntervalQuery q =
            new IntervalQuery("f", Intervals.phrase(Intervals.term("x"), alt, Intervals.term("d")));
        // y occurs nowhere and phrase(x, maxwidth(5, inner), d) matches: expected 1 hit, actual 0
        assertEquals(q + " on 'x b c d'", 1, new IndexSearcher(reader).count(q));
      }
    }
  }
}

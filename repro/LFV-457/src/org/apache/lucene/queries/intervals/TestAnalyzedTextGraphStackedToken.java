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
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAnalyzedTextGraphStackedToken extends LuceneTestCase {

  private static Token token(String term, int posInc, int posLength) {
    Token t = new Token(term, 0, 0);
    t.setPositionIncrement(posInc);
    t.setPositionLength(posLength);
    return t;
  }

  public void testStackedTokenInGraph() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("f", "wifi dog", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // query graph for "wifi dog": "wi fi" | "wifi", then "canine" and "dog" stacked
        IntervalsSource source =
            Intervals.analyzedText(
                new CannedTokenStream(
                    token("wi", 1, 1),
                    token("wifi", 0, 2),
                    token("fi", 1, 1),
                    token("canine", 1, 1),
                    token("dog", 0, 1)),
                0,
                true);
        // expected 1 hit ("wifi dog"); actual 0, "dog" is dropped (AssertionError with -ea)
        IntervalQuery query = new IntervalQuery("f", source);
        assertEquals(source.toString(), 1, new IndexSearcher(r).count(query));
      }
    }
  }
}

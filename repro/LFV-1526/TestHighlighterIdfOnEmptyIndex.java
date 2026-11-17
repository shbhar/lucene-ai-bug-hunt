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
package org.apache.lucene.search.highlight;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHighlighterIdfOnEmptyIndex extends LuceneTestCase {

  public void testQueryScorerWithReaderOfEmptyIndex() throws Exception {
    try (Directory dir = newDirectory()) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
      try (IndexReader reader = DirectoryReader.open(dir)) {
        Query query = new TermQuery(new Term("f", "alpha"));
        Highlighter highlighter = new Highlighter(new QueryScorer(query, reader, "f"));
        // expected: the match is highlighted, as with QueryScorer(query, "f"); actual: null
        assertEquals(
            "<B>alpha</B> beta",
            highlighter.getBestFragment(new MockAnalyzer(random()), "f", "alpha beta"));
      }
    }
  }
}

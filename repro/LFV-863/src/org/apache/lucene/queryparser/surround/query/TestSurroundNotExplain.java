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
package org.apache.lucene.queryparser.surround.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The surround parser turns "quick NOT (brown W fox OR dog)" into +quick -(SpanNear(brown fox) dog).
 * Explaining the document that the NOT excludes must return a non-match, not throw.
 */
public class TestSurroundNotExplain extends LuceneTestCase {

  public void testExplainDocumentExcludedByNot() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))) {
        for (String text : new String[] {"quick brown fox", "quick red fox", "slow brown dog"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null); // the production default on main
        Query q =
            QueryParser.parse("quick NOT (brown W fox OR dog)")
                .makeLuceneQueryField("body", new BasicQueryFactory());
        assertEquals(1, searcher.count(q)); // doc 1, "quick red fox"
        Explanation excluded = searcher.explain(q, 0); // "quick brown fox", excluded by the NOT
        assertFalse(excluded.isMatch());
      }
    }
  }
}

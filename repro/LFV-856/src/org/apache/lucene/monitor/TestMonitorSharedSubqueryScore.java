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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorSharedSubqueryScore extends LuceneTestCase {

  private static Query or(String a, String b) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", a)), Occur.SHOULD)
        .add(new TermQuery(new Term("body", b)), Occur.SHOULD)
        .build();
  }

  public void testSharedTermIsScoredAtEachOccurrence() throws Exception {
    Analyzer analyzer = new WhitespaceAnalyzer();
    Document doc = new Document();
    doc.add(new TextField("body", "red green apple", Field.Store.NO));
    Query query =
        new BooleanQuery.Builder()
            .add(or("red", "apple"), Occur.SHOULD)
            .add(or("green", "apple"), Occur.SHOULD)
            .build();
    IndexSearcher searcher = MemoryIndex.fromDocument(doc, analyzer).createSearcher();
    float expected = searcher.search(query, 1).scoreDocs[0].score;
    try (Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", query));
      ScoringMatch match = monitor.match(doc, ScoringMatch.DEFAULT_MATCHER).matches("q");
      // expected IndexSearcher's 0.5230583; actual 0.39229372: the shared "apple" is scored once
      assertEquals(expected, match.getScore(), 1e-6f);
    }
  }
}

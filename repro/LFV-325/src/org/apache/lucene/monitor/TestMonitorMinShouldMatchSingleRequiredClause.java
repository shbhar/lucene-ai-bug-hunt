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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorMinShouldMatchSingleRequiredClause extends LuceneTestCase {

  public void testRequiredClauseWithMinShouldMatchAndNoShouldClause() throws Exception {
    Query query =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("text", "alpha")), Occur.MUST)
            .setMinimumNumberShouldMatch(1)
            .build();
    Document doc = new Document();
    doc.add(new TextField("text", "alpha beta", Field.Store.NO));
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(new MonitorQuery("q", query));
      // (+text:alpha)~1 matches nothing (no SHOULD clause), so expected 0 matches; actual: 1
      assertEquals(0, monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}

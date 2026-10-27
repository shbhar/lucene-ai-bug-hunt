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
package org.apache.lucene.queries.mlt;

// 4.x variant of the lowered-clause-limit test (4.x test-framework packages, like(Reader, String)).
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestMoreLikeThisClauseLimitKeepsBestTerms extends LuceneTestCase {
  public void testLoweredClauseLimitKeepsBestTerms() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    Document d0 = new Document();
    d0.add(newTextField("text", "alpha beta gamma delta", Field.Store.NO));
    w.addDocument(d0);
    IndexReader reader = w.getReader();
    w.close();

    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    String like = "alpha alpha alpha alpha beta beta beta gamma gamma delta";

    int saved = BooleanQuery.getMaxClauseCount();
    BooleanQuery q;
    BooleanQuery control;
    try {
      BooleanQuery.setMaxClauseCount(2);
      // control: maxQueryTerms within the clause limit keeps alpha and beta
      mlt.setMaxQueryTerms(2);
      control = (BooleanQuery) mlt.like(new StringReader(like), "text");
      mlt.setMaxQueryTerms(25);
      q = (BooleanQuery) mlt.like(new StringReader(like), "text");
    } finally {
      BooleanQuery.setMaxClauseCount(saved);
    }
    Set<String> controlKept = new HashSet<String>();
    for (BooleanClause c : control.clauses()) {
      controlKept.add(((TermQuery) c.getQuery()).getTerm().text());
    }
    Set<String> kept = new HashSet<String>();
    for (BooleanClause c : q.clauses()) {
      kept.add(((TermQuery) c.getQuery()).getTerm().text());
    }
    Set<String> expected = new HashSet<String>();
    expected.add("alpha");
    expected.add("beta");
    assertEquals(expected, controlKept);
    assertEquals(expected, kept);

    reader.close();
    dir.close();
  }
}

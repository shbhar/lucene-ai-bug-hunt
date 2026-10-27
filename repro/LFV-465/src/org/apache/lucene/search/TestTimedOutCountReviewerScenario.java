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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Scenario proposed by the cross-model reviewer: 240 docs, term a in 230, term b in 20 (all also
 * have a). Untimed count(a OR b) = 230; with an already expired timeout the shortcut predicts 230 +
 * 20 - 0 = 250 > maxDoc. Control: the same disjunction plus a third SHOULD clause on a term that
 * matches nothing (same matches, no shortcut) gives a lower bound (0) under the same timeout.
 */
public class TestTimedOutCountReviewerScenario extends LuceneTestCase {

  private static TermQuery t(String v) {
    return new TermQuery(new Term("f", v));
  }

  public void testScenario() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 240; i++) {
          Document d = new Document();
          if (i < 230) d.add(new StringField("f", "a", Field.Store.NO));
          if (i < 20) d.add(new StringField("f", "b", Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query two = new BooleanQuery.Builder()
            .add(t("a"), BooleanClause.Occur.SHOULD).add(t("b"), BooleanClause.Occur.SHOULD).build();
        Query three = new BooleanQuery.Builder()
            .add(t("a"), BooleanClause.Occur.SHOULD).add(t("b"), BooleanClause.Occur.SHOULD)
            .add(t("nomatch"), BooleanClause.Occur.SHOULD).build();
        IndexSearcher plain = new IndexSearcher(r);
        plain.setQueryCache(null);
        assertEquals(230, plain.count(two));
        assertEquals(230, plain.count(three));

        IndexSearcher s3 = new IndexSearcher(r);
        s3.setQueryCache(null);
        s3.setTimeout(() -> true);
        int c3 = s3.count(three);
        IndexSearcher s2 = new IndexSearcher(r);
        s2.setQueryCache(null);
        s2.setTimeout(() -> true);
        int c2 = s2.count(two);
        String msg = "maxDoc=" + r.maxDoc() + " untimed=230 timed-out 3-clause control=" + c3
            + " (timedOut=" + s3.timedOut() + ") timed-out 2-clause=" + c2 + " (timedOut=" + s2.timedOut() + ")";
        System.out.println(msg);
        assertTrue("control must be a lower bound: " + msg, c3 <= 230);
        assertTrue("2-clause timed-out count must be a lower bound: " + msg, c2 <= 230);
      }
    }
  }
}

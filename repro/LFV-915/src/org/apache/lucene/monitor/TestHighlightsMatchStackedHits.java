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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHighlightsMatchStackedHits extends LuceneTestCase {

  public void testUnigramAndShingleAtSamePosition() throws IOException {
    Analyzer shingles =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new ShingleFilter(t, 2, 2));
          }
        };
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("f", "wi")), Occur.SHOULD)
            .add(new TermQuery(new Term("f", "wi fi")), Occur.SHOULD)
            .build();
    try (Monitor monitor = new Monitor(shingles)) {
      monitor.register(new MonitorQuery("q", q));
      Document doc = new Document();
      doc.add(new TextField("f", "wi fi", Field.Store.NO));
      HighlightsMatch m = monitor.match(doc, HighlightsMatch.MATCHER).matches("q");
      // expected 2 hits, 0(0)->0(2) "wi" and 0(0)->0(5) "wi fi"; actual 1
      assertEquals(m.getHits("f").toString(), 2, m.getHitCount());
    }
  }
}

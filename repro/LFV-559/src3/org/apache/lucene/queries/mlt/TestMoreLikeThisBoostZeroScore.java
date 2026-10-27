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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.util.LuceneTestCase;

/** (4.x variant: old test-framework packages, DefaultSimilarity, like(Reader, String).)
 * With boost enabled, MoreLikeThis divides every term score by the LOWEST selected score. A
 * TFIDFSimilarity whose idf reaches 0 for a term that occurs in every document (the textbook
 * idf = log(N / df)) makes that lowest score 0, the boost 0 / 0 = NaN, and like() throws
 * IllegalArgumentException from BoostQuery.
 */
public class TestMoreLikeThisBoostZeroScore extends LuceneTestCase {

  public void testBoostWithZeroIdfTerm() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    for (String text : new String[] {"common rare", "common"}) {
      Document doc = new Document();
      doc.add(newTextField("text", text, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader reader = w.getReader();
    w.close();

    // textbook idf: 0 for a term that is in every document, positive otherwise
    DefaultSimilarity sim =
        new DefaultSimilarity() {
          @Override
          public float idf(long docFreq, long docCount) {
            return (float) Math.log((double) docCount / docFreq);
          }
        };
    MoreLikeThis mlt = new MoreLikeThis(reader, sim);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);

    // control: without boost the same call returns text:common text:rare
    mlt.setBoost(false);
    assertEquals(2, ((BooleanQuery) mlt.like(new StringReader("common rare"), "text")).clauses().size());

    mlt.setBoost(true);
    BooleanQuery q = (BooleanQuery) mlt.like(new StringReader("common rare"), "text");
    assertEquals(2, q.clauses().size());
    // releases before 8.0 accept a non-finite boost silently: check the boosts too
    for (org.apache.lucene.search.BooleanClause c : q.clauses()) {
      float b = c.getQuery().getBoost();
      assertTrue("boost must be finite and >= 0, got " + b, Float.isFinite(b) && b >= 0);
    }

    reader.close();
    dir.close();
    analyzer.close();
  }
}

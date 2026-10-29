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
package org.apache.lucene.misc;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSweetSpotTopScores extends LuceneTestCase {

  public void testTopHitMatchesExhaustiveSearch() throws Exception {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(10, 20, 0.5f);
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setSimilarity(sim);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 1280; i++) {
        String text =
            switch (i) {
              case 1024 -> "t t";
              case 1025 -> "t t x x x x x x x x";
              default -> "t x x x x x x x x x";
            };
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query q = new TermQuery(new Term("f", "t"));
        TopDocs exhaustive =
            searcher.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        // expected: search(q, 1) finds the exhaustive top hit, doc 1025 (1.41); actual: doc 0 (1.0)
        assertEquals(exhaustive.scoreDocs[0].doc, searcher.search(q, 1).scoreDocs[0].doc);
      }
    }
  }
}

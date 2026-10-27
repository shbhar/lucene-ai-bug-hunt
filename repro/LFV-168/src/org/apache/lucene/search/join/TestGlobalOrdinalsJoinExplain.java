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
package org.apache.lucene.search.join;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The global-ordinals join (JoinUtil#createJoinQuery(joinField, fromQuery, toQuery, ...)) only
 * returns documents that match toQuery, but its explain() never looks at toQuery: a "from"
 * document whose join value was collected is explained as a match although it is not a hit.
 */
public class TestGlobalOrdinalsJoinExplain extends LuceneTestCase {

  public void testExplainAgreesWithHits() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
    // doc 0: a "to" document (a product) with join value p1
    Document product = new Document();
    product.add(new StringField("type", "product", Field.Store.NO));
    product.add(new SortedDocValuesField("join", new BytesRef("p1")));
    w.addDocument(product);
    // doc 1: a "from" document (an offer for product p1) that matches the user query
    Document offer = new Document();
    offer.add(new StringField("type", "offer", Field.Store.NO));
    offer.add(new TextField("text", "red", Field.Store.NO));
    offer.add(new SortedDocValuesField("join", new BytesRef("p1")));
    w.addDocument(offer);
    w.forceMerge(1); // single segment: no OrdinalMap needed
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = new IndexSearcher(reader);

    Query fromQuery =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("text", "red")), Occur.MUST)
            .add(new TermQuery(new Term("type", "offer")), Occur.FILTER)
            .build();
    Query toQuery = new TermQuery(new Term("type", "product"));

    StringBuilder failures = new StringBuilder();
    for (ScoreMode scoreMode : ScoreMode.values()) {
      Query join = JoinUtil.createJoinQuery("join", fromQuery, toQuery, searcher, scoreMode, null);
      TopDocs hits = searcher.search(join, 10);
      Set<Integer> hitDocs = new HashSet<>();
      for (ScoreDoc sd : hits.scoreDocs) {
        hitDocs.add(sd.doc);
      }
      assertEquals("only the product is a hit", Set.of(0), hitDocs);
      for (int doc = 0; doc < reader.maxDoc(); doc++) {
        Explanation expl = searcher.explain(join, doc);
        if (expl.isMatch() != hitDocs.contains(doc)) {
          failures.append(
              "scoreMode="
                  + scoreMode
                  + " doc="
                  + doc
                  + " hit="
                  + hitDocs.contains(doc)
                  + " but explain.isMatch()="
                  + expl.isMatch()
                  + " ("
                  + expl.getDescription()
                  + ")\n");
        }
      }
    }
    reader.close();
    dir.close();
    assertEquals("explain(join, doc).isMatch() must agree with the hits", "", failures.toString());
  }
}

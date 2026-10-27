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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Probe (not a repro for the issue): the index and query of
 * TestMaxScoreRequiredClauseRounding#testTopHitDoesNotDependOnTotalHitsThreshold, paged with
 * search(q, 10) and then searchAfter(lastHit, q, 10) until no hits are left. Doc 5000 is the
 * best-scoring document. Prints what each step returns; fails if doc 5000 never appears on any page.
 */
public class TestSkippedDocAcrossPages extends LuceneTestCase {

  private static Query disjunction(String[] terms, float[] boosts) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < terms.length; i++) {
      b.add(
          new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", terms[i]))), boosts[i]),
          BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  private static void addDoc(IndexWriter w, String... terms) throws IOException {
    Document d = new Document();
    for (String t : terms) {
      d.add(new StringField("f", t, Field.Store.NO));
    }
    w.addDocument(d);
  }

  public void testPagingNeverReturnsTheBestDocument() throws Exception {
    String[] terms = {"e", "a", "t", "j"};
    float[] boosts = {0x1.dfff4ep0f, 0x1.ab0p-16f, 0x1.00008p-53f, 0x1.00fe54p0f};
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 1100; i++) {
        addDoc(w, "e", "a");
      }
      for (int i = 1100; i < 5000; i++) {
        addDoc(w, "x");
      }
      addDoc(w, "e", "a", "t"); // doc 5000
      for (int i = 5001; i < 6000; i++) {
        addDoc(w, "x");
      }
      addDoc(w, "j"); // doc 6000
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Query q = disjunction(terms, boosts);

        TopDocs exhaustive = s.search(q, new TopScoreDocCollectorManager(10, Integer.MAX_VALUE));
        System.out.println(
            "COMPLETE top-1: doc "
                + exhaustive.scoreDocs[0].doc
                + " "
                + Float.toHexString(exhaustive.scoreDocs[0].score)
                + ", totalHits "
                + exhaustive.totalHits);

        TopDocs first = s.search(q, 10);
        System.out.println(
            "search(q, 10): first hit doc "
                + first.scoreDocs[0].doc
                + " "
                + Float.toHexString(first.scoreDocs[0].score)
                + ", totalHits "
                + first.totalHits);
        List<Integer> seen = new ArrayList<>();
        int pages = 0;
        TopDocs page = first;
        while (page.scoreDocs.length > 0) {
          pages++;
          for (ScoreDoc sd : page.scoreDocs) {
            seen.add(sd.doc);
          }
          ScoreDoc last = page.scoreDocs[page.scoreDocs.length - 1];
          page = s.searchAfter(last, q, 10);
        }
        System.out.println(
            "paged with searchAfter: "
                + pages
                + " pages, "
                + seen.size()
                + " hits, distinct "
                + seen.stream().distinct().count()
                + ", contains doc 5000: "
                + seen.contains(5000)
                + ", contains doc 6000: "
                + seen.contains(6000));
        assertTrue("doc 5000 (the best hit) appears on some page", seen.contains(5000));
      }
    }
  }
}

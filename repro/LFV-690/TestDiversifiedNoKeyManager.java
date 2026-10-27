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
package org.apache.lucene.misc.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check through DiversifiedTopDocsCollectorManager and IndexSearcher (random segments
 * and slices from the test framework): a keyless document must not use up key 0's quota.
 */
public class TestDiversifiedNoKeyManager extends LuceneTestCase {

  static final class NumericKeyCollector extends DiversifiedTopDocsCollector {
    NumericKeyCollector(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      try {
        return DocValues.getNumeric(context.reader(), "seller");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static final class Manager extends DiversifiedTopDocsCollectorManager<NumericKeyCollector> {
    private final int numHits;
    private final int maxHitsPerKey;

    Manager(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
      this.numHits = numHits;
      this.maxHitsPerKey = maxHitsPerKey;
    }

    @Override
    public NumericKeyCollector newCollector() {
      return new NumericKeyCollector(numHits, maxHitsPerKey);
    }
  }

  private static Document doc(String id, Long seller) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (seller != null) {
      d.add(new NumericDocValuesField("seller", seller));
    }
    return d;
  }

  /**
   * Three matching docs from three different sources (no seller, seller 0, seller 1) plus
   * non-matching filler docs; numHits = 3 and maxHitsPerKey = 1 must return all three.
   */
  public void testManagerKeylessDocTakesKeyZeroQuota() throws IOException {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        w.addDocument(doc("nokey", null));
        for (int i = 0; i < 20; i++) {
          w.addDocument(doc("filler" + i, (long) (i % 3)));
        }
        w.addDocument(doc("seller0", 0L));
        w.addDocument(doc("seller1", 1L));
        try (DirectoryReader r = w.getReader()) {
          IndexSearcher s = newSearcher(r);
          BooleanQuery.Builder b = new BooleanQuery.Builder();
          String[] ids = {"nokey", "seller0", "seller1"};
          float[] scores = {3f, 2f, 1f};
          for (int i = 0; i < ids.length; i++) {
            b.add(
                new BoostQuery(
                    new ConstantScoreQuery(new TermQuery(new Term("id", ids[i]))), scores[i]),
                BooleanClause.Occur.SHOULD);
          }
          Query q = b.build();
          TopDocs td = s.search(q, new Manager(3, 1));
          TreeSet<String> got = new TreeSet<>();
          List<String> trace = new ArrayList<>();
          for (ScoreDoc sd : td.scoreDocs) {
            String id = s.storedFields().document(sd.doc).get("id");
            got.add(id);
            trace.add(
                id
                    + "(score="
                    + sd.score
                    + ", key="
                    + ((DiversifiedTopDocsCollector.ScoreDocKey) sd).getKey()
                    + ")");
          }
          System.out.println(
              "segments=" + r.leaves().size() + " totalHits=" + td.totalHits + " hits: " + trace);
          assertEquals(new TreeSet<>(Arrays.asList(ids)), got);
        }
      }
    }
  }
}

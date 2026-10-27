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
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.ConstantScoreQuery;
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
 * Independent check: a document for which the key source reports no value (advanceExact returns
 * false) should not share the maxHitsPerKey quota of the documents whose key is 0.
 *
 * <p>Only public and protected API is used. Scores are fixed by boosted constant-score clauses, so
 * the ranking does not depend on doc ids or the similarity. Collector only (works on 9.x too).
 */
public class TestDiversifiedNoKeyCollector extends LuceneTestCase {

  /** Keys = the value of a numeric doc-values field. */
  static final class NumericKeyCollector extends DiversifiedTopDocsCollector {
    private final String field;

    NumericKeyCollector(int numHits, int maxHitsPerKey, String field) {
      super(numHits, maxHitsPerKey);
      this.field = field;
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      try {
        return DocValues.getNumeric(context.reader(), field);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * Keys = the ordinal of a sorted doc-values field (the pattern of the upstream test of this
   * collector). The index has one segment, so per-segment ordinals are global.
   */
  static final class OrdinalKeyCollector extends DiversifiedTopDocsCollector {
    private final String field;

    OrdinalKeyCollector(int numHits, int maxHitsPerKey, String field) {
      super(numHits, maxHitsPerKey);
      this.field = field;
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      final SortedDocValues sdv;
      try {
        sdv = DocValues.getSorted(context.reader(), field);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return new NumericDocValues() {
        @Override
        public long longValue() throws IOException {
          return sdv.ordValue();
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return sdv.advanceExact(target);
        }

        @Override
        public int docID() {
          return sdv.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return sdv.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return sdv.advance(target);
        }

        @Override
        public long cost() {
          return sdv.cost();
        }
      };
    }
  }

  /** One matching doc: its stored id, its score, and optionally a numeric key and an artist. */
  private static Document doc(String id, Long seller, String artist) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (seller != null) {
      d.add(new NumericDocValuesField("seller", seller));
    }
    if (artist != null) {
      d.add(new SortedDocValuesField("artist", new BytesRef(artist)));
    }
    return d;
  }

  /** id:a^sa OR id:b^sb ... with constant-score clauses: each doc scores exactly its boost. */
  private static Query scored(String[] ids, float[] scores) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int i = 0; i < ids.length; i++) {
      b.add(
          new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("id", ids[i]))), scores[i]),
          BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  /** Runs the given collector over a one-segment index; returns the sorted ids of the top hits. */
  private TreeSet<String> search(List<Document> docs, Query q, DiversifiedTopDocsCollector c)
      throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        w.addDocuments(docs);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        CollectorManager<DiversifiedTopDocsCollector, TopDocs> once =
            new CollectorManager<DiversifiedTopDocsCollector, TopDocs>() {
              boolean used;

              @Override
              public DiversifiedTopDocsCollector newCollector() {
                assertFalse("expected a single slice", used);
                used = true;
                return c;
              }

              @Override
              public TopDocs reduce(Collection<DiversifiedTopDocsCollector> cs) {
                assertEquals(1, cs.size());
                return c.topDocs();
              }
            };
        TopDocs td = s.search(q, once);
        TreeSet<String> ids = new TreeSet<>();
        List<String> trace = new ArrayList<>();
        for (ScoreDoc sd : td.scoreDocs) {
          String id = s.storedFields().document(sd.doc).get("id");
          ids.add(id);
          trace.add(id + "(score=" + sd.score + ", key=" + ((DiversifiedTopDocsCollector.ScoreDocKey) sd).getKey() + ")");
        }
        System.out.println("hits: " + trace);
        return ids;
      }
    }
  }

  private static TreeSet<String> set(String... ids) {
    return new TreeSet<>(Arrays.asList(ids));
  }

  /** Keyless doc ranks first: the key-0 doc is dropped although numHits leaves room for it. */
  public void testKeylessRanksFirstKeyZeroDocDropped() throws IOException {
    List<Document> docs = Arrays.asList(doc("nokey", null, null), doc("seller0", 0L, null));
    Query q = scored(new String[] {"nokey", "seller0"}, new float[] {3f, 2f});
    assertEquals(
        set("nokey", "seller0"), search(docs, q, new NumericKeyCollector(2, 1, "seller")));
  }

  /** Key-0 doc ranks first: the keyless doc is dropped. */
  public void testKeyZeroRanksFirstKeylessDocDropped() throws IOException {
    List<Document> docs = Arrays.asList(doc("nokey", null, null), doc("seller0", 0L, null));
    Query q = scored(new String[] {"nokey", "seller0"}, new float[] {2f, 3f});
    assertEquals(
        set("nokey", "seller0"), search(docs, q, new NumericKeyCollector(2, 1, "seller")));
  }

  /** Control: the same pair with key 1 instead of key 0 returns both docs (passes on main). */
  public void testControlKeylessAndKeyOneBothReturned() throws IOException {
    List<Document> docs = Arrays.asList(doc("nokey", null, null), doc("seller1", 1L, null));
    Query q = scored(new String[] {"nokey", "seller1"}, new float[] {3f, 2f});
    assertEquals(
        set("nokey", "seller1"), search(docs, q, new NumericKeyCollector(2, 1, "seller")));
  }

  /** Control: two real key-0 docs are capped to one (passes on main; the cap itself works). */
  public void testControlTwoKeyZeroDocsCapped() throws IOException {
    List<Document> docs = Arrays.asList(doc("seller0a", 0L, null), doc("seller0b", 0L, null));
    Query q = scored(new String[] {"seller0a", "seller0b"}, new float[] {3f, 2f});
    assertEquals(set("seller0a"), search(docs, q, new NumericKeyCollector(2, 1, "seller")));
  }

  /**
   * Ordinal keys: a song without an artist takes the slot of the first artist in sort order (ord
   * 0), here "ABBA", so ABBA's song is dropped from a 3-song list of 3 different sources.
   */
  public void testOrdinalKeysSongWithoutArtistTakesFirstArtistsSlot() throws IOException {
    List<Document> docs =
        Arrays.asList(
            doc("no-artist", null, null), doc("abba", null, "ABBA"), doc("zappa", null, "Zappa"));
    Query q = scored(new String[] {"no-artist", "abba", "zappa"}, new float[] {3f, 2f, 1f});
    assertEquals(
        set("abba", "no-artist", "zappa"),
        search(docs, q, new OrdinalKeyCollector(3, 1, "artist")));
  }
}

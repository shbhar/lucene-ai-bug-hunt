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
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A document without a value in the key field must not use up the maxHitsPerKey quota of a real
 * key. Every document matches MatchAllDocsQuery with the same score, so hits rank in index order;
 * every search asks for one hit per document with maxHitsPerKey = 1.
 */
public class TestDiversifiedTopDocsCollectorMissingKey extends LuceneTestCase {

  /** Keys are the values of the numeric doc-values field "seller". */
  private static final class SellerCollector extends DiversifiedTopDocsCollector {
    SellerCollector(int numHits, int maxHitsPerKey) {
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

  /**
   * Keys are the per-segment ordinals of the sorted doc-values field "artist". The upstream test
   * uses top-level ordinals; each index here has a single segment, so both give the same keys.
   */
  private static final class ArtistCollector extends DiversifiedTopDocsCollector {
    ArtistCollector(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      final SortedDocValues sdv;
      try {
        sdv = DocValues.getSorted(context.reader(), "artist");
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

  /** A song without an artist must not take the slot of "ABBA", whose ordinal is 0. */
  public void testSongWithoutArtistDoesNotTakeFirstArtistSlot() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(song("no-artist", null));
        w.addDocument(song("abba", "ABBA"));
        w.addDocument(song("zappa", "Zappa"));
      }
      assertEquals("[no-artist, abba, zappa]", search(dir, 3, () -> new ArtistCollector(3, 1)));
    }
  }

  /** A document without a seller must not take the slot of seller 0. */
  public void testDocWithoutSellerDoesNotTakeSellerZeroSlot() throws Exception {
    assertEquals("[no-seller, seller-0]", searchSellers(0));
  }

  /** Control: next to seller 1 instead of seller 0, both documents are returned. */
  public void testDocWithoutSellerNextToSellerOne() throws Exception {
    assertEquals("[no-seller, seller-1]", searchSellers(1));
  }

  /** Control: the cap itself works, two documents of seller 0 give one hit. */
  public void testTwoDocsOfSellerZeroAreCapped() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(product("seller-0-a", 0L));
        w.addDocument(product("seller-0-b", 0L));
      }
      assertEquals("[seller-0-a]", search(dir, 2, () -> new SellerCollector(2, 1)));
    }
  }

  private String searchSellers(long seller) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(product("no-seller", null));
        w.addDocument(product("seller-" + seller, seller));
      }
      return search(dir, 2, () -> new SellerCollector(2, 1));
    }
  }

  private static Document song(String id, String artist) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (artist != null) {
      doc.add(new SortedDocValuesField("artist", new BytesRef(artist)));
    }
    return doc;
  }

  private static Document product(String id, Long seller) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (seller != null) {
      doc.add(new NumericDocValuesField("seller", seller));
    }
    return doc;
  }

  /** Searches through DiversifiedTopDocsCollectorManager and returns the ids of the hits. */
  private String search(
      Directory dir, int numHits, Supplier<DiversifiedTopDocsCollector> collectors)
      throws IOException {
    try (IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = newSearcher(reader, false);
      TopDocs td =
          searcher.search(
              new MatchAllDocsQuery(),
              new DiversifiedTopDocsCollectorManager<>(numHits, 1) {
                @Override
                public DiversifiedTopDocsCollector newCollector() {
                  return collectors.get();
                }
              });
      assertEquals(reader.maxDoc(), td.totalHits.value());
      List<String> ids = new ArrayList<>();
      for (ScoreDoc sd : td.scoreDocs) {
        ids.add(searcher.storedFields().document(sd.doc).get("id"));
      }
      return ids.toString();
    }
  }
}

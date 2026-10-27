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
 * A document for which getKeys() has no value (advanceExact returns false) is grouped under key 0
 * (DiversifiedTopDocsCollector#insert), so it uses up the maxHitsPerKey quota of the real key 0
 * and a matching key-0 document is dropped from the top hits.
 *
 * <p>Two documents, both match, numHits = 2, maxHitsPerKey = 1: the second document is the only
 * hit of key 0 and must be returned.
 */
public class TestDiversifiedMissingKey extends LuceneTestCase {

  /** Keys straight from a numeric doc-values field ("seller id"). */
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

  /** Keys are term ordinals of a sorted doc-values field, as in TestDiversifiedTopDocsCollector. */
  static final class OrdinalKeyCollector extends DiversifiedTopDocsCollector {
    OrdinalKeyCollector(int numHits, int maxHitsPerKey) {
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

  public void testDocWithoutKeyTakesQuotaOfKeyZero() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document noSeller = new Document();
        noSeller.add(new StringField("id", "no-seller", Field.Store.YES));
        w.addDocument(noSeller);
        Document seller0 = new Document();
        seller0.add(new StringField("id", "seller-0", Field.Store.YES));
        seller0.add(new NumericDocValuesField("seller", 0));
        w.addDocument(seller0);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false);
        TopDocs td =
            searcher.search(
                new MatchAllDocsQuery(),
                new DiversifiedTopDocsCollectorManager<NumericKeyCollector>(2, 1) {
                  @Override
                  public NumericKeyCollector newCollector() {
                    return new NumericKeyCollector(2, 1);
                  }
                });
        assertEquals(2, td.totalHits.value());
        assertEquals("ids returned: " + ids(searcher, td), 2, td.scoreDocs.length);
      }
    }
  }

  public void testSongWithoutArtistTakesQuotaOfFirstArtist() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document noArtist = new Document();
        noArtist.add(new StringField("id", "song-without-artist", Field.Store.YES));
        w.addDocument(noArtist);
        Document abba = new Document();
        abba.add(new StringField("id", "abba-song", Field.Store.YES));
        abba.add(new SortedDocValuesField("artist", new BytesRef("ABBA"))); // ordinal 0
        w.addDocument(abba);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false);
        TopDocs td =
            searcher.search(
                new MatchAllDocsQuery(),
                new DiversifiedTopDocsCollectorManager<OrdinalKeyCollector>(2, 1) {
                  @Override
                  public OrdinalKeyCollector newCollector() {
                    return new OrdinalKeyCollector(2, 1);
                  }
                });
        assertEquals(2, td.totalHits.value());
        assertEquals("ids returned: " + ids(searcher, td), 2, td.scoreDocs.length);
      }
    }
  }

  private static String ids(IndexSearcher searcher, TopDocs td) throws IOException {
    StringBuilder b = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      b.append(searcher.storedFields().document(sd.doc).get("id")).append(' ');
    }
    return b.toString().trim();
  }
}

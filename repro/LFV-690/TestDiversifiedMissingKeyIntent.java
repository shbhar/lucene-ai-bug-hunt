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
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Intended-behaviour checks for DiversifiedTopDocsCollector with documents that have no key.
 *
 * <p>The collector is driven leaf by leaf (as the upstream test's testInvalidArguments does), so the
 * same source runs on releases without DiversifiedTopDocsCollectorManager. All matching documents
 * score 1.0, so ties go to the lower doc id; doc 0 is always the document without a key.
 *
 * <p>Tests named test*Expect2 assert the reading "a document without a key is not from the source
 * of key 0" and fail on the current code. Tests named test*Observed assert the current behaviour
 * and pass; they record the facts the intended-behaviour argument rests on.
 */
public class TestDiversifiedMissingKeyIntent extends LuceneTestCase {

  /** Keys straight from a numeric doc-values field, the most direct getKeys implementation. */
  static final class NumericKeys extends DiversifiedTopDocsCollector {
    NumericKeys(int numHits, int maxHitsPerKey) {
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

  /** Caller-side policy: a dense view that reports a sentinel key for documents without a value. */
  static final class DenseSentinelKeys extends DiversifiedTopDocsCollector {
    DenseSentinelKeys(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      final NumericDocValues in;
      try {
        in = DocValues.getNumeric(context.reader(), "seller");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return new NumericDocValues() {
        int doc = -1;
        boolean has;

        @Override
        public boolean advanceExact(int target) throws IOException {
          doc = target;
          has = in.advanceExact(target);
          return true;
        }

        @Override
        public long longValue() throws IOException {
          return has ? in.longValue() : Long.MIN_VALUE;
        }

        @Override
        public int docID() {
          return doc;
        }

        @Override
        public int nextDoc() {
          throw new UnsupportedOperationException();
        }

        @Override
        public int advance(int target) {
          throw new UnsupportedOperationException();
        }

        @Override
        public long cost() {
          return context.reader().maxDoc();
        }
      };
    }
  }

  /**
   * Copy of the upstream test's "100% accurate" DocValuesDiversifiedCollector: top-level ordinals of
   * a SortedDocValues field, advanceExact delegated to the sorted iterator.
   */
  static final class UpstreamAccurateOrdinalKeys extends DiversifiedTopDocsCollector {
    private SortedDocValues sdv;

    UpstreamAccurateOrdinalKeys(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
      if (sdv == null) {
        sdv =
            MultiDocValues.getSortedValues(
                ReaderUtil.getTopLevelContext(context).reader(), "artist");
      }
      return super.getLeafCollector(context);
    }

    @Override
    protected NumericDocValues getKeys(final LeafReaderContext context) {
      return new NumericDocValues() {
        @Override
        public int docID() {
          return sdv.docID() - context.docBase;
        }

        @Override
        public int nextDoc() throws IOException {
          return sdv.nextDoc() - context.docBase;
        }

        @Override
        public int advance(int target) throws IOException {
          return sdv.advance(target + context.docBase);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return sdv.advanceExact(target + context.docBase);
        }

        @Override
        public long cost() {
          return 0;
        }

        @Override
        public long longValue() throws IOException {
          return sdv.ordValue();
        }
      };
    }
  }

  /** Copy of the upstream test's "fuzzy" HashedDocValuesDiversifiedCollector. */
  static final class UpstreamHashedKeys extends DiversifiedTopDocsCollector {
    private SortedDocValues vals;

    UpstreamHashedKeys(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      return new NumericDocValues() {
        @Override
        public int docID() {
          return vals.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return vals.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return vals.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return vals.advanceExact(target);
        }

        @Override
        public long cost() {
          return vals.cost();
        }

        @Override
        public long longValue() throws IOException {
          return vals == null ? -1 : vals.lookupOrd(vals.ordValue()).hashCode();
        }
      };
    }

    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
      this.vals = DocValues.getSorted(context.reader(), "artist");
      return super.getLeafCollector(context);
    }
  }

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  /** Doc 0 has neither field; doc 1 gets the given fields. One segment. */
  private void index(Long seller, String artist, boolean secondKeyless) throws IOException {
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document keyless = new Document();
      keyless.add(new StringField("id", "keyless", Field.Store.YES));
      w.addDocument(keyless);
      Document second = new Document();
      if (secondKeyless) {
        second.add(new StringField("id", "keyless-2", Field.Store.YES));
      } else {
        second.add(new StringField("id", "keyed", Field.Store.YES));
        if (seller != null) {
          second.add(new NumericDocValuesField("seller", seller));
        }
        if (artist != null) {
          second.add(new SortedDocValuesField("artist", new BytesRef(artist)));
        }
      }
      w.addDocument(second);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    super.tearDown();
  }

  private TopDocs run(DiversifiedTopDocsCollector c) throws IOException {
    Weight w =
        searcher.createWeight(searcher.rewrite(new MatchAllDocsQuery()), ScoreMode.COMPLETE, 1f);
    for (LeafReaderContext ctx : reader.leaves()) {
      LeafCollector lc = c.getLeafCollector(ctx);
      BulkScorer bs = w.bulkScorer(ctx);
      if (bs != null) {
        bs.score(lc, ctx.reader().getLiveDocs(), 0, DocIdSetIterator.NO_MORE_DOCS);
      }
    }
    assertEquals(2, c.getTotalHits());
    return c.topDocs();
  }

  private String describe(TopDocs td) throws IOException {
    StringBuilder b = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      b.append(reader.storedFields().document(sd.doc).get("id"))
          .append("(key=")
          .append(((DiversifiedTopDocsCollector.ScoreDocKey) sd).getKey())
          .append(") ");
    }
    return b.toString().trim();
  }

  // ---- the candidate's claim, numeric key source ----

  public void testNumericKeylessVsKeyZeroExpect2() throws Exception {
    index(0L, null, false);
    TopDocs td = run(new NumericKeys(2, 1));
    assertEquals("returned: " + describe(td), 2, td.scoreDocs.length);
  }

  public void testNumericKeylessVsKeySevenObserved() throws Exception {
    index(7L, null, false);
    TopDocs td = run(new NumericKeys(2, 1));
    assertEquals("returned: " + describe(td), 2, td.scoreDocs.length);
  }

  /** Two keyless documents share one quota: the collector has a single "missing" group. */
  public void testTwoKeylessDocsShareOneGroupObserved() throws Exception {
    index(null, null, true);
    TopDocs td = run(new NumericKeys(2, 1));
    assertEquals("returned: " + describe(td), 1, td.scoreDocs.length);
    // ...and the public ScoreDocKey.getKey() reports 0 for it, indistinguishable from a real 0
    assertEquals(
        Long.valueOf(0L), ((DiversifiedTopDocsCollector.ScoreDocKey) td.scoreDocs[0]).getKey());
  }

  /** The caller can choose another policy without any Lucene change. */
  public void testDenseSentinelWorkaroundObserved() throws Exception {
    index(0L, null, false);
    TopDocs td = run(new DenseSentinelKeys(2, 1));
    assertEquals("returned: " + describe(td), 2, td.scoreDocs.length);
  }

  // ---- the two key adapters of the upstream test, which it treats as interchangeable ----

  public void testUpstreamAccurateOrdinalAdapterExpect2() throws Exception {
    index(null, "ABBA", false);
    TopDocs td = run(new UpstreamAccurateOrdinalKeys(2, 1));
    assertEquals("returned: " + describe(td), 2, td.scoreDocs.length);
  }

  public void testUpstreamHashedAdapterObserved() throws Exception {
    index(null, "ABBA", false);
    TopDocs td = run(new UpstreamHashedKeys(2, 1));
    assertEquals("returned: " + describe(td), 2, td.scoreDocs.length);
  }
}

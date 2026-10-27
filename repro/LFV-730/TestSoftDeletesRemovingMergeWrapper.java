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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * {@link MergePolicy.OneMerge#wrapForMerge} may "add/remove fields or documents". A merge wrapper
 * that drops expired documents must keep working when the index has a soft-deletes field, alone or
 * under {@link SoftDeletesRetentionMergePolicy}. Public API only.
 */
public class TestSoftDeletesRemovingMergeWrapper extends LuceneTestCase {

  /** A merge policy whose merges drop every document that has expired:true. */
  private static MergePolicy expiring(MergePolicy in) {
    return new OneMergeWrappingMergePolicy(
        in,
        toWrap ->
            new MergePolicy.OneMerge(toWrap.segments) {
              @Override
              public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                return hideExpired(toWrap.wrapForMerge(reader));
              }
            });
  }

  private static CodecReader hideExpired(CodecReader reader) throws IOException {
    FixedBitSet live = new FixedBitSet(reader.maxDoc());
    Bits liveDocs = reader.getLiveDocs();
    for (int d = 0; d < reader.maxDoc(); d++) {
      if (liveDocs == null || liveDocs.get(d)) {
        live.set(d);
      }
    }
    PostingsEnum pe = reader.postings(new Term("expired", "true"));
    if (pe != null) {
      for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) {
        live.clear(d);
      }
    }
    int numDocs = live.cardinality();
    return new FilterCodecReader(reader) {
      @Override
      public Bits getLiveDocs() {
        return live;
      }

      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return in.getCoreCacheHelper();
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null; // live docs differ from the wrapped reader
      }
    };
  }

  private static IndexWriterConfig config(boolean softDeletes, MergePolicy mp) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    if (softDeletes) {
      iwc.setSoftDeletesField("soft");
    }
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(1000);
    iwc.setMaxFullFlushMergeWaitMillis(0);
    return iwc;
  }

  private static Document doc(String id, boolean expired) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (expired) {
      d.add(new StringField("expired", "true", Field.Store.NO));
    }
    return d;
  }

  /** Segment 1 = {a, b (expired)}, segment 2 = {c}; forceMerge(1) must keep a and c. */
  private void mergeDropsExpired(boolean softDeletes, boolean unrelatedHardDelete)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, config(softDeletes, expiring(new LogDocMergePolicy())))) {
      w.addDocument(doc("a", false));
      w.addDocument(doc("b", true));
      if (unrelatedHardDelete) {
        w.addDocument(doc("x", false));
        w.deleteDocuments(new Term("id", "x"));
      }
      w.commit();
      w.addDocument(doc("c", false));
      w.commit();
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("a and c remain, b expired", 2, r.numDocs());
        assertEquals(2, r.maxDoc());
      }
    }
  }

  /** Bug: only a soft-deletes field is configured; nothing is soft-deleted. */
  public void testSoftDeletesFieldNothingSoftDeleted() throws IOException {
    mergeDropsExpired(true, false);
  }

  /** Control: the same merge without a soft-deletes field. */
  public void testControlNoSoftDeletesField() throws IOException {
    mergeDropsExpired(false, false);
  }

  /** Control: segment 1 also holds one unrelated hard-deleted document. */
  public void testControlUnrelatedHardDelete() throws IOException {
    mergeDropsExpired(true, true);
  }

  /** Retention keeps soft-deleted documents while retain is true, over the expiring policy. */
  private static IndexWriter retentionWriter(Directory dir, AtomicBoolean retain)
      throws IOException {
    Supplier<Query> query = () -> retain.get() ? new MatchAllDocsQuery() : new MatchNoDocsQuery();
    MergePolicy mp =
        new SoftDeletesRetentionMergePolicy("soft", query, expiring(new LogDocMergePolicy()));
    return new IndexWriter(dir, config(true, mp));
  }

  /**
   * Segment 1 = {a, b (expired)}, segment 2 = {c, a2}, where a2 soft-updates a. After forceMerge(1)
   * the segment holds a (soft-deleted, retained), c and a2: 1 soft delete, 2 live.
   */
  private static void indexAndMergeRetained(IndexWriter w) throws IOException {
    w.addDocument(doc("a", false));
    w.addDocument(doc("b", true));
    w.commit();
    w.addDocument(doc("c", false));
    w.softUpdateDocument(
        new Term("id", "a"), doc("a2", false), new NumericDocValuesField("soft", 1));
    w.commit();
    w.forceMerge(1);
    w.commit();
  }

  /** Bug, silent with assertions off: the merged segment records 0 soft deletes instead of 1. */
  public void testRetentionCounts() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = retentionWriter(dir, new AtomicBoolean(true))) {
      indexAndMergeRetained(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        String got =
            "softDelCount="
                + SegmentInfos.readLatestCommit(dir).info(0).getSoftDelCount()
                + " hits="
                + s.search(new MatchAllDocsQuery(), 10).scoreDocs.length
                + " count="
                + s.count(new MatchAllDocsQuery())
                + " numDocs="
                + r.numDocs()
                + " docStats.numDocs="
                + w.getDocStats().numDocs;
        assertEquals("softDelCount=1 hits=2 count=2 numDocs=2 docStats.numDocs=2", got);
      }
    }
  }

  /** Bug: CheckIndex at level 2 (integrity checks) rejects the merged segment. */
  public void testRetentionCheckIndex() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = retentionWriter(dir, new AtomicBoolean(true))) {
        indexAndMergeRetained(w);
      }
      TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS, true, false, null);
    }
  }

  /** Bug: once the retention query stops matching, the next merge of that segment must work. */
  public void testMergeAfterRetentionEnds() throws IOException {
    AtomicBoolean retain = new AtomicBoolean(true);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = retentionWriter(dir, retain)) {
      indexAndMergeRetained(w);
      retain.set(false);
      w.addDocument(doc("d", false));
      w.commit();
      w.forceMerge(1); // purges a
      w.commit();
      assertEquals("c, a2, d", 3, w.getDocStats().maxDoc);
      assertEquals(3, w.getDocStats().numDocs);
    }
  }
}

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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * {@link MergePolicy.OneMerge#wrapForMerge} is documented as the place to "add/remove fields or
 * documents" before a merge. A merge wrapper that removes documents must keep working when the
 * index has a soft-deletes field, with or without {@link SoftDeletesRetentionMergePolicy}.
 */
public class TestMergeWrapperRemovingDocsWithSoftDeletes extends LuceneTestCase {

  /** Hides every document that has expired:true, on top of the reader's own deletions. */
  static CodecReader dropExpired(CodecReader reader) throws IOException {
    Bits liveDocs = reader.getLiveDocs();
    FixedBitSet newLive = new FixedBitSet(reader.maxDoc());
    for (int d = 0; d < reader.maxDoc(); d++) {
      if (liveDocs == null || liveDocs.get(d)) newLive.set(d);
    }
    PostingsEnum pe = reader.postings(new Term("expired", "true"));
    if (pe != null) {
      for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) {
        newLive.clear(d);
      }
    }
    final int numDocs = newLive.cardinality();
    return new FilterCodecReader(reader) {
      @Override
      public Bits getLiveDocs() {
        return newLive;
      }

      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return null;
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }

  static MergePolicy expiringPolicy() {
    return new OneMergeWrappingMergePolicy(
        new LogDocMergePolicy(),
        toWrap ->
            new MergePolicy.OneMerge(toWrap.segments) {
              @Override
              public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                return dropExpired(toWrap.wrapForMerge(reader));
              }
            });
  }

  static Document doc(String id, boolean expired) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (expired) d.add(new StringField("expired", "true", Field.Store.NO));
    return d;
  }

  static IndexWriterConfig config(MergePolicy mp, boolean softDeletes) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(16);
    if (softDeletes) iwc.setSoftDeletesField("soft");
    return iwc;
  }

  /** Control: without a soft-deletes field the expiring merge works. */
  public void testRemovingWrapperWithoutSoftDeletesField() throws IOException {
    runRemovingWrapper(false);
  }

  /**
   * Same index and policy, but with a soft-deletes field configured (no document is ever
   * soft-deleted): the merge must still drop the expired document and succeed.
   */
  public void testRemovingWrapperWithSoftDeletesField() throws IOException {
    runRemovingWrapper(true);
  }

  private void runRemovingWrapper(boolean softDeletes) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(expiringPolicy(), softDeletes))) {
        w.addDocument(doc("a", false));
        w.addDocument(doc("b", true));
        w.commit();
        w.addDocument(doc("c", false));
        w.commit();
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(2, r.numDocs());
        assertEquals(2, r.maxDoc());
      }
      TestUtil.checkIndex(dir);
    }
  }

  /**
   * Composition with SoftDeletesRetentionMergePolicy: the retention policy keeps the soft-deleted
   * document, the inner wrapper drops the expired one. The merged segment must report its one
   * soft-deleted document, so that the soft-deletes-aware document count stays right.
   */
  public void testRemovingWrapperUnderRetentionPolicy() throws IOException {
    try (Directory dir = newDirectory()) {
      MergePolicy mp =
          new SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, expiringPolicy());
      try (IndexWriter w = new IndexWriter(dir, config(mp, true))) {
        w.addDocument(doc("a", false));
        w.addDocument(doc("b", true));
        w.commit();
        w.addDocument(doc("c", false));
        // soft-delete "a" by soft-updating it with a new version "a2"
        w.softUpdateDocument(
            new Term("id", "a"), doc("a2", false), new NumericDocValuesField("soft", 1));
        w.commit();
        w.forceMerge(1);
        w.commit();
        // a (retained, soft-deleted), c, a2 survive; b expired
        try (DirectoryReader nrt = DirectoryReader.open(w)) {
          assertEquals("maxDoc", 3, nrt.maxDoc());
          assertEquals("numDocs (soft-deleted 'a' does not count)", 2, nrt.numDocs());
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(3, r.maxDoc());
        try (DirectoryReader soft = new SoftDeletesDirectoryReaderWrapper(r, "soft")) {
          assertEquals(2, soft.numDocs());
          int live = 0;
          for (LeafReaderContext ctx : soft.leaves()) {
            Bits ld = ctx.reader().getLiveDocs();
            for (int d = 0; d < ctx.reader().maxDoc(); d++) if (ld == null || ld.get(d)) live++;
          }
          assertEquals("live documents counted one by one", 2, live);
        }
      }
      TestUtil.checkIndex(dir);
    }
  }
}

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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * IndexWriter#addIndexes(CodecReader...) runs the merges that MergePolicy#findMerges(CodecReader...)
 * returns, and OneMerge#wrapForMerge may "add/remove fields or documents". When the wrapper
 * removes documents, the writer's document accounting and the new segment's soft-delete count must
 * describe what was actually added.
 */
public class TestAddIndexesMergeWrapperRemovingDocs extends LuceneTestCase {

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

  /** A policy that drops expired documents while adding readers (and merges like LogDoc). */
  static MergePolicy expiringAddIndexesPolicy() {
    return new FilterMergePolicy(new LogDocMergePolicy()) {
      @Override
      public MergeSpecification findMerges(CodecReader... readers) {
        MergeSpecification spec = new MergeSpecification();
        spec.add(
            new OneMerge(readers) {
              @Override
              public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                return dropExpired(reader);
              }
            });
        return spec;
      }
    };
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
    if (softDeletes) iwc.setSoftDeletesField("soft");
    return iwc;
  }

  /** No soft deletes: the reserved document count must match the documents actually added. */
  public void testPendingNumDocsAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, config(NoMergePolicy.INSTANCE, false))) {
        w.addDocument(doc("a", true));
        w.addDocument(doc("b", false));
        w.addDocument(doc("c", false));
      }
      try (IndexWriter w = new IndexWriter(dst, config(expiringAddIndexesPolicy(), false));
          DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader[] readers = new CodecReader[r.leaves().size()];
        for (int i = 0; i < readers.length; i++) {
          readers[i] = (CodecReader) r.leaves().get(i).reader();
        }
        w.addIndexes(readers);
        w.commit();
        assertEquals("maxDoc after dropping the expired document", 2, w.getDocStats().maxDoc);
        assertEquals("reserved documents", w.getDocStats().maxDoc, w.getPendingNumDocs());
      }
    }
  }

  /**
   * Soft deletes: the incoming reader carries a soft-deleted (and expired) document that the
   * wrapper drops. The new segment must not claim a soft-deleted document it does not hold.
   */
  public void testSoftDeleteCountAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, config(NoMergePolicy.INSTANCE, true))) {
        w.addDocument(doc("a", true));
        w.addDocument(doc("c", false));
        // soft-delete the expired "a" by soft-updating it with a new version "a2"
        w.softUpdateDocument(
            new Term("id", "a"), doc("a2", false), new NumericDocValuesField("soft", 1));
      }
      try (IndexWriter w = new IndexWriter(dst, config(expiringAddIndexesPolicy(), true));
          DirectoryReader r = DirectoryReader.open(src)) {
        // DirectoryReader.open(Directory) applies only hard deletes: a, c, a2 are all visible
        CodecReader[] readers = new CodecReader[r.leaves().size()];
        for (int i = 0; i < readers.length; i++) {
          readers[i] = (CodecReader) r.leaves().get(i).reader();
        }
        w.addIndexes(readers);
        w.commit();
        // c and a2 were added; "a" (soft-deleted and expired) was dropped by the wrapper
        assertEquals("DocStats.maxDoc", 2, w.getDocStats().maxDoc);
        assertEquals("DocStats.numDocs", 2, w.getDocStats().numDocs);
      }
      TestUtil.checkIndex(dst);
    }
  }
}

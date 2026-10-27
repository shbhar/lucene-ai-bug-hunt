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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * IndexWriter#addIndexes(CodecReader...) runs the merges of MergePolicy#findMerges(CodecReader...),
 * and OneMerge#wrapForMerge may "add/remove fields or documents". When the wrapper removes
 * documents, the writer's accounting must describe the documents the merge actually wrote.
 *
 * <p>Source index (soft-deletes field "soft"): a (expired; soft-deleted by a soft update), c, a2.
 * DirectoryReader.open(Directory) applies hard deletes only, so all three reach addIndexes, and the
 * wrapper drops the expired a. The new segment holds c and a2, none of them soft-deleted.
 */
public class TestAddIndexesWrapForMergeAccounting extends LuceneTestCase {

  /** Hides every document that has expired:true, on top of the reader's own deletions. */
  private static CodecReader dropExpired(CodecReader reader) throws IOException {
    Bits liveDocs = reader.getLiveDocs();
    FixedBitSet newLive = new FixedBitSet(reader.maxDoc());
    for (int d = 0; d < reader.maxDoc(); d++) {
      if (liveDocs == null || liveDocs.get(d)) {
        newLive.set(d);
      }
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

  /** addIndexes runs one merge whose wrapForMerge drops expired documents. */
  private static MergePolicy dropExpiredOnAddIndexes() {
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

  private static Document doc(String id, boolean expired) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (expired) {
      d.add(new StringField("expired", "true", Field.Store.NO));
    }
    return d;
  }

  private static IndexWriterConfig config(MergePolicy mp, boolean softDeletes) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    if (softDeletes) {
      iwc.setSoftDeletesField("soft");
    }
    return iwc;
  }

  /** a (expired), c, then a soft update of a to a2: with soft deletes a is soft-deleted. */
  private static void writeSource(Directory src, boolean softDeletes) throws IOException {
    try (IndexWriter w = new IndexWriter(src, config(NoMergePolicy.INSTANCE, softDeletes))) {
      w.addDocument(doc("a", true));
      w.addDocument(doc("c", false));
      if (softDeletes) {
        w.softUpdateDocument(
            new Term("id", "a"), doc("a2", false), new NumericDocValuesField("soft", 1));
      } else {
        w.addDocument(doc("a2", false));
      }
    }
  }

  private static void addIndexesAndCommit(IndexWriter w, Directory src) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(src)) {
      CodecReader[] readers = new CodecReader[r.leaves().size()];
      for (int i = 0; i < readers.length; i++) {
        readers[i] = (CodecReader) r.leaves().get(i).reader();
      }
      w.addIndexes(readers);
    }
    w.commit();
  }

  /** The committed segment must not claim a soft-deleted document it does not hold. */
  public void testSoftDeleteCountAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      writeSource(src, true);
      try (IndexWriter w = new IndexWriter(dst, config(dropExpiredOnAddIndexes(), true))) {
        addIndexesAndCommit(w, src);
        assertEquals("maxDoc", 2, w.getDocStats().maxDoc);
        assertEquals(
            "soft deletes recorded in the new segment",
            0,
            SegmentInfos.readLatestCommit(dst).info(0).getSoftDelCount());
        assertEquals("numDocs", 2, w.getDocStats().numDocs);
      }
    }
  }

  /** A later ordinary forceMerge must keep c, a2 and z as three distinct documents. */
  public void testForceMergeAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      writeSource(src, true);
      try (IndexWriter w = new IndexWriter(dst, config(dropExpiredOnAddIndexes(), true))) {
        addIndexesAndCommit(w, src);
        w.addDocument(doc("z", false));
        w.commit();
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        assertEquals("maxDoc after forceMerge", 3, r.maxDoc());
        IndexSearcher s = new IndexSearcher(r);
        for (String id : new String[] {"c", "a2", "z"}) {
          TopDocs td = s.search(new TermQuery(new Term("id", id)), 10);
          assertEquals("hits for id:" + id, 1, td.totalHits.value());
          assertEquals(
              "stored id of the hit for id:" + id,
              id,
              s.storedFields().document(td.scoreDocs[0].doc).get("id"));
        }
      }
    }
  }

  /** Without soft deletes: the reserved document count must match the documents added. */
  public void testPendingNumDocsAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      writeSource(src, false);
      try (IndexWriter w = new IndexWriter(dst, config(dropExpiredOnAddIndexes(), false))) {
        addIndexesAndCommit(w, src);
        assertEquals("maxDoc", 2, w.getDocStats().maxDoc);
        assertEquals("getPendingNumDocs", 2, w.getPendingNumDocs());
      }
    }
  }
}

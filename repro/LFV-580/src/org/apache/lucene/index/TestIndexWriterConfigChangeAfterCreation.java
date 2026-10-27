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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriterConfig class javadoc: "Once IndexWriter has been created with this object, changes to
 * this object will not affect the IndexWriter instance. For that, use LiveIndexWriterConfig that is
 * returned from IndexWriter#getConfig()." The per-setter javadocs of setCodec, setSimilarity,
 * setReaderPooling, ... repeat "Only takes effect when IndexWriter is first created."
 *
 * <p>IndexWriter keeps the caller's object (config = conf) and IndexWriter / DocumentsWriterPerThread
 * re-read settings from it, so these changes do affect the writer.
 */
public class TestIndexWriterConfigChangeAfterCreation extends LuceneTestCase {

  private static IndexWriterConfig newConfig() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000); // no natural merges; forceMerge still merges
    iwc.setMergePolicy(mp);
    return iwc;
  }

  /**
   * Applies a change to a config that is already used by a writer. Refusing the change (an
   * IllegalStateException) also honours the contract; then the rest of the test must still pass.
   */
  private static void changeAfterCreation(Runnable change) {
    try {
      change.run();
    } catch (IllegalStateException refused) {
      // the writer is not affected either way
    }
  }

  private static Document doc(int id) {
    Document d = new Document();
    d.add(new StringField("id", Integer.toString(id), Field.Store.YES));
    d.add(new NumericDocValuesField("flag", 0)); // an ordinary application doc-values field
    d.add(new NumericDocValuesField("a", id % 3));
    d.add(new NumericDocValuesField("b", -id));
    return d;
  }

  /**
   * setSoftDeletesField on the config after the writer exists: DocumentsWriterPerThread re-reads the
   * field at flush and records documents carrying it as soft-deleted, while the writer, the
   * FieldInfos and every reader still run without soft deletes and report them live. Deleting
   * another document of that segment then makes the segment look fully deleted, and IndexWriter
   * drops it together with the live document.
   */
  public void testSetSoftDeletesFieldAfterCreationLosesLiveDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = newConfig(); // soft deletes disabled
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        changeAfterCreation(() -> iwc.setSoftDeletesField("flag")); // must not affect w
        w.addDocument(doc(1)); // has a "flag" doc value
        Document plain = new Document();
        plain.add(new StringField("id", "2", Field.Store.YES));
        w.addDocument(plain);
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          assertEquals(2, r.numDocs());
        }
        w.deleteDocuments(new Term("id", "2"));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("only id:2 was deleted; id:1 must still be there", 1, r.numDocs());
      }
    }
  }

  /**
   * setParentField on the config after the writer exists: segments flushed afterwards are written
   * without the parent field, every merge with an older segment fails, and the failed merge is a
   * tragic event that closes the writer. The committed index stays unmergeable for later writers.
   */
  public void testSetParentFieldAfterCreationMakesIndexUnmergeable() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = newConfig().setParentField("_parent");
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc(0));
      w.commit();
      changeAfterCreation(() -> iwc.setParentField(null)); // must not affect w
      w.addDocument(doc(1));
      w.commit();
      Throwable mergeFailure = null;
      try {
        w.forceMerge(1);
      } catch (Throwable t) {
        mergeFailure = t;
      }
      boolean writerOpen = w.isOpen();
      if (writerOpen) {
        w.close();
      }
      assertNull("forceMerge failed (writer open afterwards: " + writerOpen + "): " + mergeFailure, mergeFailure);
      // a new writer configured exactly like the first one
      try (IndexWriter w2 = new IndexWriter(dir, newConfig().setParentField("_parent"))) {
        w2.forceMerge(1);
      }
    }
  }

  /**
   * setIndexSort on the config after the writer exists: segments flushed afterwards are sorted by
   * the new sort, so the next merge fails ("cannot change index sort"), the writer closes on the
   * tragic merge failure, and no IndexWriter configured with the original sort can open the index.
   */
  public void testSetIndexSortAfterCreationBreaksMergesAndReopen() throws Exception {
    Sort original = new Sort(new SortField("a", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = newConfig().setIndexSort(original);
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc(0));
      w.addDocument(doc(1));
      w.commit();
      changeAfterCreation(
          () -> iwc.setIndexSort(new Sort(new SortField("b", SortField.Type.LONG)))); // no effect
      w.addDocument(doc(2));
      w.addDocument(doc(3));
      w.commit();
      Throwable mergeFailure = null;
      try {
        w.forceMerge(1);
      } catch (Throwable t) {
        mergeFailure = t;
      }
      boolean writerOpen = w.isOpen();
      if (writerOpen) {
        w.close();
      }
      assertNull(
          "forceMerge failed (writer open afterwards: " + writerOpen + "): " + mergeFailure,
          mergeFailure);
      // a new writer configured exactly like the first one
      try (IndexWriter w2 = new IndexWriter(dir, newConfig().setIndexSort(original))) {
        w2.forceMerge(1);
      }
    }
  }

  /** setCodec on the config after the writer exists: new segments are written with that codec. */
  public void testSetCodecAfterCreationChangesSegmentCodec() throws Exception {
    Codec original = TestUtil.getDefaultCodec();
    Codec other = new FilterCodec("OtherCodecName", original) {};
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = newConfig().setCodec(original);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        changeAfterCreation(() -> iwc.setCodec(other)); // "Only takes effect when ... first created."
        w.addDocument(doc(0));
        w.flush();
        assertEquals(original.getName(), w.cloneSegmentInfos().info(0).info.getCodec().getName());
      }
    }
  }
}

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * A merge that IndexWriter#getReader runs and waits for (merge-on-refresh, see
 * IndexWriterConfig#setMaxFullFlushMergeWaitMillis) takes a reference on the merged segment's
 * files when the merge completes. If every document of the merged segments is deleted while the
 * merge runs, commitMerge drops the merged segment, getReader closes the reader it kept for it,
 * but the reference on its files is never released: the files are never deleted by this writer,
 * not by deleteUnusedFiles(), commit(), close() or rollback().
 *
 * <p>Uses public API only. The documents are deleted from inside OneMerge#wrapForMerge (which the
 * merge calls while it runs) so the interleaving is deterministic; any delete that is applied while
 * the merge runs (deleteDocuments, updateDocument, tryDeleteDocument) has the same effect.
 */
public class TestGetReaderMergeDroppedSegmentLeak extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Merges every segment on getReader; optionally deletes all docs of r0 while merging. */
  private static final class MergeOnGetReader extends FilterMergePolicy {
    final AtomicBoolean enabled = new AtomicBoolean(false);
    final AtomicBoolean deleteAllWhileMerging = new AtomicBoolean(false);
    final AtomicBoolean deleted = new AtomicBoolean(false);
    final MergeTrigger trigger;
    volatile IndexWriter writer;
    volatile DirectoryReader r0;

    MergeOnGetReader(MergeTrigger trigger) {
      super(NoMergePolicy.INSTANCE);
      this.trigger = trigger;
    }

    private MergeSpecification mergeAll(SegmentInfos infos, MergeContext ctx) {
      if (enabled.get() == false) {
        return null;
      }
      List<SegmentCommitInfo> segs = new ArrayList<>();
      for (SegmentCommitInfo sci : infos) {
        if (ctx.getMergingSegments().contains(sci) == false) {
          segs.add(sci);
        }
      }
      if (segs.size() < 2) {
        return null;
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(
          new OneMerge(segs) {
            @Override
            public CodecReader wrapForMerge(CodecReader reader) throws IOException {
              if (deleteAllWhileMerging.get() && deleted.compareAndSet(false, true)) {
                for (int i = 0; i < r0.maxDoc(); i++) {
                  assertTrue(writer.tryDeleteDocument(r0, i) != -1);
                }
              }
              return reader;
            }
          });
      return spec;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger mergeTrigger, SegmentInfos infos, MergeContext ctx) {
      return mergeTrigger == trigger ? mergeAll(infos, ctx) : null;
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos infos,
        int maxSegmentCount,
        java.util.Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext ctx) {
      return trigger == null ? mergeAll(infos, ctx) : null;
    }
  }

  /** Files in the directory that the latest commit does not reference. */
  private static Set<String> unreferenced(Directory dir) throws IOException {
    Set<String> files = new TreeSet<>(Arrays.asList(dir.listAll()));
    files.removeAll(SegmentInfos.readLatestCommit(dir).files(true));
    files.remove(IndexWriter.WRITE_LOCK_NAME);
    return files;
  }

  /** two one-doc segments, committed; r0 = NRT reader on them; then a getReader/forceMerge. */
  private Set<String> run(MergeTrigger trigger, boolean deleteAllWhileMerging) throws Exception {
    return run(trigger, deleteAllWhileMerging, false);
  }

  private Set<String> run(MergeTrigger trigger, boolean deleteAllWhileMerging, boolean rollback)
      throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir("leak"))) {
      MergeOnGetReader mp = new MergeOnGetReader(trigger);
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(mp)
              .setMergeScheduler(new SerialMergeScheduler())
              .setMaxFullFlushMergeWaitMillis(60_000);
      IndexWriter w = new IndexWriter(dir, iwc);
      mp.writer = w;
      w.addDocument(doc("a"));
      w.flush();
      w.addDocument(doc("b"));
      w.flush();
      w.commit();
      DirectoryReader r0 = DirectoryReader.open(w);
      mp.r0 = r0;
      mp.deleteAllWhileMerging.set(deleteAllWhileMerging);
      mp.enabled.set(true);
      DirectoryReader r1 = null;
      if (trigger == MergeTrigger.GET_READER) {
        r1 = DirectoryReader.open(w); // runs the merge and waits for it
      } else {
        w.forceMerge(1);
      }
      mp.enabled.set(false);
      assertEquals(deleteAllWhileMerging, mp.deleted.get());
      r0.close();
      if (r1 != null) {
        r1.close();
      }
      w.deleteUnusedFiles();
      if (rollback) {
        w.rollback();
      } else {
        w.close();
      }
      Set<String> leftover = unreferenced(dir);
      if (leftover.isEmpty() == false) {
        // they really are garbage: the next writer deletes them on open
        new IndexWriter(dir, new IndexWriterConfig()).close();
        assertEquals(java.util.Collections.<String>emptySet(), unreferenced(dir));
      }
      return leftover;
    }
  }

  /** The bug: getReader merge whose merged segment ends up 100% deleted. */
  public void testGetReaderMergeOfFullyDeletedSegmentsLeaksFiles() throws Exception {
    Set<String> leftover = run(MergeTrigger.GET_READER, true);
    assertEquals(
        "files left behind by IndexWriter after deleteUnusedFiles() and close()",
        java.util.Collections.<String>emptySet(),
        leftover);
  }

  /** The bug, ending with rollback() instead of close(). */
  public void testGetReaderMergeOfFullyDeletedSegmentsLeaksFilesAfterRollback() throws Exception {
    Set<String> leftover = run(MergeTrigger.GET_READER, true, true);
    assertEquals("files left behind by IndexWriter after deleteUnusedFiles() and rollback()", java.util.Collections.<String>emptySet(), leftover);
  }

  /** Control: the same getReader merge without the concurrent deletes leaves nothing behind. */
  public void testGetReaderMergeWithoutDeletesIsClean() throws Exception {
    assertEquals(java.util.Collections.<String>emptySet(), run(MergeTrigger.GET_READER, false));
  }

  /** Control: a forceMerge whose merged segment ends up 100% deleted leaves nothing behind. */
  public void testForceMergeOfFullyDeletedSegmentsIsClean() throws Exception {
    assertEquals(java.util.Collections.<String>emptySet(), run(null, true));
  }
}

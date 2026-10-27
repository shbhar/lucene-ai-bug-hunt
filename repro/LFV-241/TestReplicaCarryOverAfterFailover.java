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
package org.apache.lucene.replicator.nrt;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.IOUtils;

/**
 * After a failover, a replica must not keep a file that its copy job from the old primary already
 * finished when the new primary's NRT point has a different file under the same name.
 *
 * <p>The nodes run in this JVM: a replica copies whole files straight from its primary's directory,
 * one file per {@link InProcCopyJob#copyNextFile} call, so the interleaving is deterministic. Only
 * the extension points of {@link PrimaryNode}, {@link ReplicaNode} and {@link CopyJob} are
 * implemented here; the replication logic under test is Lucene's own.
 */
@SuppressFileSystems("*")
public class TestReplicaCarryOverAfterFailover extends LuceneTestCase {

  /** The replica's copy job from the old primary finished one of its two files. */
  public void testFileFinishedForOldPrimaryIsNotKept() throws Exception {
    runFailover(1);
  }

  /** Control: the replica's copy job from the old primary finished nothing. */
  public void testControlNothingFinishedForOldPrimary() throws Exception {
    runFailover(0);
  }

  private void runFailover(int filesFinishedForOldPrimary) throws Exception {
    PrintStream log = VERBOSE ? System.out : null;
    Path aPath = createTempDir("replicaA");
    Path bPath = createTempDir("replicaB");

    // Primary P1 (primaryGen 1) has two segments: _0 = {a0, b0} and _1 = {a1, b1}.
    IndexWriter w1 = new IndexWriter(FSDirectory.open(createTempDir("p1")), newConfig());
    InProcPrimary p1 = new InProcPrimary(w1, 0, 1, -1, log);
    addDoc(w1, "a0");
    addDoc(w1, "b0");
    w1.flush();
    addDoc(w1, "a1");
    addDoc(w1, "b1");
    assertTrue(p1.flushAndRefresh());
    long v1 = p1.getCopyStateVersion();

    // Replicas A and B both copy P1's point v1.
    InProcReplica a = new InProcReplica(1, FSDirectory.open(aPath), p1, log);
    InProcReplica b = new InProcReplica(2, FSDirectory.open(bPath), p1, log);
    a.syncTo(1, v1);
    b.syncTo(1, v1);

    // P1 deletes a0 and a1 and publishes v2, which adds _0_1.liv and _1_1.liv. B starts copying
    // v2 and finishes some of its files; A has not started.
    w1.deleteDocuments(new Term("id", "a0"), new Term("id", "a1"));
    assertTrue(p1.flushAndRefresh());
    long v2 = p1.getCopyStateVersion();
    InProcCopyJob oldJob = (InProcCopyJob) b.newNRTPoint(1, v2);
    assertEquals(2, oldJob.getFileNamesToCopy().size());
    for (int i = 0; i < filesFinishedForOldPrimary; i++) {
      assertTrue(oldJob.copyNextFile());
    }
    Set<String> finishedForOldPrimary = oldJob.finishedFileNames();

    // P1 crashes. Only then is A promoted to P2 (primaryGen 2). A is as current as B (both search
    // v1) and copies nothing; its version is forced past P1's last version v2.
    p1.setRemoteCloseTimeoutMs(0);
    p1.close();
    a.commit();
    a.close();
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(aPath), newConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    InProcPrimary p2 = new InProcPrimary(w2, 1, 2, v2 + 1, log);

    // P2 deletes b0 and b1 and publishes v3. Its new files have the names of P1's v2 files,
    // _0_1.liv and _1_1.liv (same segments, same delete generation, one delete each), but other
    // content.
    w2.deleteDocuments(new Term("id", "b0"), new Term("id", "b1"));
    assertTrue(p2.flushAndRefresh());
    long v3 = p2.getCopyStateVersion();
    Set<String> expected = liveIds(p2);
    assertEquals(Set.of("a0", "a1"), expected);

    // B hears of P2's point v3 before its copy job from the crashed P1 has failed.
    b.primary = p2;
    InProcCopyJob newJob = (InProcCopyJob) b.newNRTPoint(2, v3);
    assertTrue("the old copy job is cancelled", oldJob.getFailed());
    b.runJob(oldJob);
    b.runJob(newJob);
    assertEquals(v3, b.getCurrentSearchingVersion());
    Set<String> searched = liveIds(b);

    // B commits; read the commit back on its own and run CheckIndex on it.
    b.commit();
    b.close();
    p2.setRemoteCloseTimeoutMs(0);
    p2.close();
    Set<String> committed;
    try (Directory dir = FSDirectory.open(bPath)) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        committed = liveIds(reader);
      }
      TestUtil.checkIndex(dir); // throws unless CheckIndex finds no problems
    }

    String msg =
        "replica B is at P2's version "
            + v3
            + " but searches "
            + searched
            + " and committed "
            + committed
            + " (CheckIndex clean); finished for P1 before the failover: "
            + finishedForOldPrimary;
    assertEquals(msg, expected, searched);
    assertEquals(msg, expected, committed);
  }

  private IndexWriterConfig newConfig() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  /** The "id" values of the live documents in the node's current searcher. */
  private static Set<String> liveIds(Node node) throws IOException {
    ReferenceManager<IndexSearcher> mgr = node.getSearcherManager();
    IndexSearcher s = mgr.acquire();
    try {
      return liveIds((DirectoryReader) s.getIndexReader());
    } finally {
      mgr.release(s);
    }
  }

  private static Set<String> liveIds(DirectoryReader reader) throws IOException {
    Set<String> ids = new TreeSet<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      LeafReader leaf = ctx.reader();
      Bits live = leaf.getLiveDocs();
      StoredFields storedFields = leaf.storedFields();
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        if (live == null || live.get(doc)) {
          ids.add(storedFields.document(doc).get("id"));
        }
      }
    }
    return ids;
  }

  /** A primary without a network: its replicas take its CopyState directly. */
  private static final class InProcPrimary extends PrimaryNode {
    InProcPrimary(
        IndexWriter writer, int id, long primaryGen, long forcePrimaryVersion, PrintStream log)
        throws IOException {
      super(writer, id, primaryGen, forcePrimaryVersion, null, log);
    }

    @Override
    protected void preCopyMergedSegmentFiles(
        SegmentCommitInfo info, Map<String, FileMetaData> files) {
      // NoMergePolicy: nothing to pre-copy
    }
  }

  /** A replica whose "address" is the current primary object; the test runs its copy jobs. */
  private static final class InProcReplica extends ReplicaNode {
    volatile InProcPrimary primary;

    InProcReplica(int id, Directory dir, InProcPrimary primary, PrintStream log)
        throws IOException {
      super(id, dir, null, log);
      this.primary = primary;
      start(1);
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      InProcPrimary p = primary;
      CopyState copyState = null;
      if (files == null) {
        // the primary's latest NRT point, incRef'd until the job releases it
        copyState = p.getCopyState();
        files = copyState.files();
      }
      return new InProcCopyJob(reason, p, copyState, this, files, highPriority, onceDone);
    }

    @Override
    protected void launch(CopyJob job) {
      // the test runs the job with runJob
    }

    @Override
    protected void sendNewReplica() {}

    /** Copies the job's remaining files and then calls its OnceDone, like a launcher thread. */
    void runJob(InProcCopyJob job) throws IOException {
      while (job.copyNextFile()) {}
      try {
        if (job.onceDone != null) {
          job.onceDone.run(job);
        }
      } finally {
        job.release();
      }
    }

    void syncTo(long primaryGen, long version) throws IOException {
      CopyJob job = newNRTPoint(primaryGen, version);
      if (job != null) {
        runJob((InProcCopyJob) job);
      }
    }
  }

  /** Copies whole files straight from the source primary's directory. */
  private static final class InProcCopyJob extends CopyJob {
    final InProcPrimary src;
    final CopyState copyState;
    final byte[] buffer = new byte[8192];
    private Iterator<Map.Entry<String, FileMetaData>> iter;
    private IndexInput in;
    private boolean released;

    InProcCopyJob(
        String reason,
        InProcPrimary src,
        CopyState copyState,
        ReplicaNode dest,
        Map<String, FileMetaData> files,
        boolean highPriority,
        OnceDone onceDone)
        throws IOException {
      super(reason, files, dest, highPriority, onceDone);
      this.src = src;
      this.copyState = copyState;
    }

    @Override
    protected CopyOneFile newCopyOneFile(CopyOneFile prev) {
      // resume the previous job's partly copied file from this job's primary, at the same offset
      try {
        in = src.getDirectory().openInput(prev.name, IOContext.DEFAULT);
        in.seek(prev.getBytesCopied());
        return new CopyOneFile(prev, in);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public synchronized void start() {
      iter = toCopy.iterator();
      if (current != null) {
        totBytes += current.metaData.length();
      }
      for (Map.Entry<String, FileMetaData> ent : toCopy) {
        totBytes += ent.getValue().length();
      }
    }

    /** Copies one whole file; returns false once nothing is left or the job was cancelled. */
    synchronized boolean copyNextFile() throws IOException {
      if (exc != null) {
        return false;
      }
      if (current == null) {
        if (iter.hasNext() == false) {
          return false;
        }
        Map.Entry<String, FileMetaData> next = iter.next();
        in = src.getDirectory().openInput(next.getKey(), IOContext.DEFAULT);
        current = new CopyOneFile(in, dest, next.getKey(), next.getValue(), buffer);
      }
      try {
        while (current.visit() == false) {}
      } catch (Throwable t) {
        cancel("exc during copy", t);
        return false;
      }
      copiedFiles.put(current.name, current.tmpName);
      totBytesCopied += current.getBytesCopied();
      current = null;
      IOUtils.close(in);
      in = null;
      return true;
    }

    /** The names of the files this job has finished copying to temp files. */
    synchronized Set<String> finishedFileNames() {
      return new TreeSet<>(copiedFiles.keySet());
    }

    @Override
    public void runBlocking() throws IOException {
      while (copyNextFile()) {}
      if (getFailed()) {
        throw new IOException("copy failed: " + cancelReason, exc);
      }
    }

    @Override
    public void finish() throws IOException {
      for (Map.Entry<String, String> ent : copiedFiles.entrySet()) {
        dest.dir.rename(ent.getValue(), ent.getKey());
      }
      copiedFiles.clear();
      release();
    }

    @Override
    public synchronized void cancel(String reason, Throwable exc) throws IOException {
      try {
        super.cancel(reason, exc);
      } finally {
        release();
      }
    }

    synchronized void release() throws IOException {
      IOUtils.closeWhileHandlingException(in);
      in = null;
      if (released == false && copyState != null) {
        released = true;
        try {
          src.releaseCopyState(copyState);
        } catch (AlreadyClosedException _) {
          // the source primary is gone: nothing to release
        }
      }
    }

    @Override
    public boolean conflicts(CopyJob other) {
      Set<String> mine = getFileNamesToCopy();
      mine.retainAll(other.getFileNamesToCopy());
      return mine.isEmpty() == false;
    }

    @Override
    public boolean getFailed() {
      return exc != null;
    }

    @Override
    public synchronized Set<String> getFileNamesToCopy() {
      Set<String> names = new HashSet<>();
      for (Map.Entry<String, FileMetaData> ent : toCopy) {
        names.add(ent.getKey());
      }
      return names;
    }

    @Override
    public Set<String> getFileNames() {
      return files.keySet();
    }

    @Override
    public CopyState getCopyState() {
      return copyState;
    }

    @Override
    public long getTotalBytesCopied() {
      return totBytesCopied;
    }

    @Override
    public int compareTo(CopyJob other) {
      if (highPriority != other.highPriority) {
        return highPriority ? -1 : 1;
      }
      return Long.compare(ord, other.ord);
    }

    @Override
    public String toString() {
      return "InProcCopyJob(ord=" + ord + " " + reason + ")";
    }
  }
}

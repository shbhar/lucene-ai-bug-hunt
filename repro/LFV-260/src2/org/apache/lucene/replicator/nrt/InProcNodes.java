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
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.IOUtils;

/**
 * (9.x API variant: CopyState and FileMetaData are classes with public fields.) In-process primary and replica nodes for the NRT replication framework. The replica copies
 * files straight from the primary's {@link Directory} instead of over a socket, and its copy jobs
 * are driven explicitly by the test (one whole file per {@link InProcCopyJob#visit()} call), which
 * makes every interleaving deterministic. Only the extension points of {@link PrimaryNode}, {@link
 * ReplicaNode} and {@link CopyJob} are implemented; the protocol logic under test is Lucene's own.
 */
final class InProcNodes {
  private InProcNodes() {}

  static PrintStream log(boolean verbose) {
    return verbose ? System.out : new PrintStream(OutputStream.nullOutputStream());
  }

  /** A primary with no network: replicas pull its CopyState directly. */
  static final class InProcPrimaryNode extends PrimaryNode {
    /** Optional hook for merge pre-copy (called from the merge thread). */
    volatile PreCopyHook preCopyHook;

    interface PreCopyHook {
      void preCopy(SegmentCommitInfo info, Map<String, FileMetaData> files) throws IOException;
    }

    InProcPrimaryNode(IndexWriter writer, int id, long primaryGen, long forcePrimaryVersion, PrintStream ps)
        throws IOException {
      super(writer, id, primaryGen, forcePrimaryVersion, null, ps);
    }

    IndexWriter writer() {
      return writer;
    }

    @Override
    protected void preCopyMergedSegmentFiles(SegmentCommitInfo info, Map<String, FileMetaData> files)
        throws IOException {
      PreCopyHook hook = preCopyHook;
      if (hook != null) {
        hook.preCopy(info, files);
      }
    }
  }

  /** A replica whose "network address" is a reference to the current primary object. */
  static final class InProcReplicaNode extends ReplicaNode {
    /** The primary this replica talks to (set by the test, like a new primary TCP port). */
    volatile InProcPrimaryNode primary;

    /** Jobs handed to {@link #launch}; the test runs them with {@link #runJob}. */
    final List<InProcCopyJob> launched = Collections.synchronizedList(new ArrayList<>());

    InProcReplicaNode(int id, Directory dir, InProcPrimaryNode primary, long curPrimaryGen, PrintStream ps)
        throws IOException {
      super(id, dir, null, ps);
      this.primary = primary;
      start(curPrimaryGen);
    }

    @Override
    protected CopyJob newCopyJob(
        String reason,
        Map<String, FileMetaData> files,
        Map<String, FileMetaData> prevFiles,
        boolean highPriority,
        CopyJob.OnceDone onceDone)
        throws IOException {
      InProcPrimaryNode p = primary;
      CopyState copyState = null;
      if (files == null) {
        // Ask the primary for its latest NRT point (incRef'd until the job is done):
        copyState = p.getCopyState();
        files = copyState.files;
      }
      return new InProcCopyJob(reason, p, copyState, this, files, highPriority, onceDone);
    }

    @Override
    protected void launch(CopyJob job) {
      launched.add((InProcCopyJob) job);
    }

    @Override
    protected void sendNewReplica() {}

    /** Visits the job until it is done and then calls its OnceDone, like a launcher thread. */
    void runJob(InProcCopyJob job) throws IOException {
      while (job.visit() == false) {}
      try {
        if (job.onceDone != null) {
          job.onceDone.run(job);
        }
      } finally {
        job.done();
        launched.remove(job);
      }
    }

    /** Asks for the given point and copies it to completion. */
    void syncTo(long primaryGen, long version) throws IOException {
      CopyJob job = newNRTPoint(primaryGen, version);
      if (job != null) {
        runJob((InProcCopyJob) job);
      }
    }
  }

  /** Copies whole files straight from the source primary's directory. */
  static final class InProcCopyJob extends CopyJob {
    final InProcPrimaryNode src;
    final CopyState copyState;
    final byte[] buffer = new byte[8192];
    private Iterator<Map.Entry<String, FileMetaData>> iter;
    private IndexInput curIn;
    private boolean released;

    /** Optional hook run at the start of {@link #finish()} (used to model a slow rename). */
    volatile Consumer<InProcCopyJob> beforeFinish;

    InProcCopyJob(
        String reason,
        InProcPrimaryNode src,
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
      // Resume the previous job's in-progress file from this job's primary, at the same offset:
      try {
        IndexInput in = src.getDirectory().openInput(prev.name, IOContext.DEFAULT);
        in.seek(prev.getBytesCopied());
        curIn = in;
        return new CopyOneFile(prev, in);
      } catch (IOException ioe) {
        throw new UncheckedIOException(ioe);
      }
    }

    @Override
    public synchronized void start() throws IOException {
      if (iter != null) {
        throw new IllegalStateException("already started");
      }
      iter = toCopy.iterator();
      if (current != null) {
        totBytes += current.metaData.length;
      }
      for (Map.Entry<String, FileMetaData> ent : toCopy) {
        totBytes += ent.getValue().length;
      }
    }

    /** Copies one whole file; returns true once there is nothing left to do (or we were cancelled). */
    synchronized boolean visit() throws IOException {
      if (exc != null) {
        return true;
      }
      if (current == null) {
        if (iter.hasNext() == false) {
          return true;
        }
        Map.Entry<String, FileMetaData> next = iter.next();
        curIn = src.getDirectory().openInput(next.getKey(), IOContext.DEFAULT);
        if (curIn.length() != next.getValue().length) {
          throw new IllegalStateException("file " + next.getKey() + ": length changed on primary");
        }
        current = new CopyOneFile(curIn, dest, next.getKey(), next.getValue(), buffer);
      }
      try {
        while (current.visit() == false) {}
      } catch (Throwable t) {
        cancel("exc during visit", t);
        return true;
      }
      copiedFiles.put(current.name, current.tmpName);
      totBytesCopied += current.getBytesCopied();
      current = null;
      IOUtils.close(curIn);
      curIn = null;
      return false;
    }

    /** Names of the files whose bytes this job has completely copied to temp files. */
    synchronized Set<String> copiedFileNames() {
      return new TreeSet<>(copiedFiles.keySet());
    }

    @Override
    public void runBlocking() throws IOException {
      while (visit() == false) {}
      if (getFailed()) {
        throw new IOException("copy failed: " + cancelReason, exc);
      }
    }

    @Override
    public void finish() throws IOException {
      Consumer<InProcCopyJob> hook = beforeFinish;
      if (hook != null) {
        hook.accept(this);
      }
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
        IOUtils.closeWhileHandlingException(curIn);
        curIn = null;
        release();
      }
    }

    synchronized void done() throws IOException {
      IOUtils.closeWhileHandlingException(curIn);
      curIn = null;
      release();
    }

    private synchronized void release() throws IOException {
      if (released == false && copyState != null) {
        released = true;
        try {
          src.releaseCopyState(copyState);
        } catch (AlreadyClosedException _) {
          // the source primary is gone (crashed or closed): nothing to release
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

  /** The stored "id" values of all live documents visible through the node's current searcher. */
  static Set<String> visibleIds(Node node) throws IOException {
    ReferenceManager<IndexSearcher> mgr = node.getSearcherManager();
    IndexSearcher s = mgr.acquire();
    try {
      return visibleIds((DirectoryReader) s.getIndexReader());
    } finally {
      mgr.release(s);
    }
  }

  static Set<String> visibleIds(DirectoryReader reader) throws IOException {
    Set<String> ids = new TreeSet<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      LeafReader leaf = ctx.reader();
      Bits live = leaf.getLiveDocs();
      StoredFields sf = leaf.storedFields();
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (live == null || live.get(d)) {
          ids.add(sf.document(d).get("id"));
        }
      }
    }
    return ids;
  }
}

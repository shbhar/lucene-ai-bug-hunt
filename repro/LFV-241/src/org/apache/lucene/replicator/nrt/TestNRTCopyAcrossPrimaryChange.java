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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;

@SuppressFileSystems("*")
public class TestNRTCopyAcrossPrimaryChange extends LuceneTestCase {

  public void testReplicaMatchesNewPrimaryAfterFailover() throws Exception {
    Path aPath = createTempDir();
    IndexWriter w1 = new IndexWriter(FSDirectory.open(createTempDir()), iwc());
    Primary p1 = new Primary(w1, 0, 1, -1);
    w1.addDocument(doc("a0"));
    w1.addDocument(doc("b0"));
    w1.flush();
    w1.addDocument(doc("a1"));
    w1.addDocument(doc("b1"));
    p1.flushAndRefresh();
    Replica ra = new Replica(1, aPath, p1);
    Replica rb = new Replica(2, createTempDir(), p1);
    copyAll(ra.newNRTPoint(1, p1.getCopyStateVersion()));
    copyAll(rb.newNRTPoint(1, p1.getCopyStateVersion()));

    w1.deleteDocuments(new Term("id", "a0"), new Term("id", "a1"));
    p1.flushAndRefresh();
    long v2 = p1.getCopyStateVersion();
    ((Job) rb.newNRTPoint(1, v2)).copyNextFile();

    p1.close();
    ra.commit();
    ra.close();
    IndexWriter w2 =
        new IndexWriter(
            FSDirectory.open(aPath), iwc().setOpenMode(IndexWriterConfig.OpenMode.APPEND));
    Primary p2 = new Primary(w2, 1, 2, v2 + 1);
    w2.deleteDocuments(new Term("id", "b0"), new Term("id", "b1"));
    p2.flushAndRefresh();

    rb.primary = p2;
    copyAll(rb.newNRTPoint(2, p2.getCopyStateVersion()));
    IndexSearcher searcher = rb.getSearcherManager().acquire();
    try {
      Set<String> ids = new TreeSet<>();
      StoredFields storedFields = searcher.getIndexReader().storedFields();
      for (ScoreDoc hit : searcher.search(new MatchAllDocsQuery(), 10).scoreDocs) {
        ids.add(storedFields.document(hit.doc).get("id"));
      }
      // expected: B, now at P2's version, shows P2's live docs; actual: one .liv is still P1's
      assertEquals(new TreeSet<>(Set.of("a0", "a1")), ids);
    } finally {
      rb.getSearcherManager().release(searcher);
      rb.close();
      p2.close();
    }
  }

  private static IndexWriterConfig iwc() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  private static void copyAll(CopyJob job) throws IOException {
    while (((Job) job).copyNextFile()) {}
    job.onceDone.run(job);
  }

  private static final class Primary extends PrimaryNode {
    Primary(IndexWriter writer, int id, long primaryGen, long forcePrimaryVersion)
        throws IOException {
      super(writer, id, primaryGen, forcePrimaryVersion, null, null);
      setRemoteCloseTimeoutMs(0);
    }

    @Override
    protected void preCopyMergedSegmentFiles(
        SegmentCommitInfo info, Map<String, FileMetaData> files) {}
  }

  private static final class Replica extends ReplicaNode {
    Primary primary;

    Replica(int id, Path path, Primary primary) throws IOException {
      super(id, FSDirectory.open(path), null, null);
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
      CopyState copyState = primary.getCopyState();
      return new Job(reason, copyState, this, primary, highPriority, onceDone);
    }

    @Override
    protected void launch(CopyJob job) {}

    @Override
    protected void sendNewReplica() {}
  }

  private static final class Job extends CopyJob {
    private final Primary src;
    private final CopyState copyState;

    Job(
        String reason,
        CopyState copyState,
        ReplicaNode dest,
        Primary src,
        boolean highPriority,
        OnceDone onceDone)
        throws IOException {
      super(reason, copyState.files(), dest, highPriority, onceDone);
      this.src = src;
      this.copyState = copyState;
    }

    boolean copyNextFile() throws IOException {
      if (toCopy.isEmpty()) {
        return false;
      }
      Map.Entry<String, FileMetaData> next = toCopy.removeFirst();
      try (IndexInput in = src.getDirectory().openInput(next.getKey(), IOContext.DEFAULT)) {
        CopyOneFile file =
            new CopyOneFile(in, dest, next.getKey(), next.getValue(), new byte[8192]);
        while (file.visit() == false) {}
        copiedFiles.put(file.name, file.tmpName);
      }
      return true;
    }

    @Override
    public void finish() throws IOException {
      for (Map.Entry<String, String> ent : copiedFiles.entrySet()) {
        dest.dir.rename(ent.getValue(), ent.getKey());
      }
      copiedFiles.clear();
    }

    @Override
    public boolean getFailed() {
      return exc != null;
    }

    @Override
    public Set<String> getFileNamesToCopy() {
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
      return 0;
    }

    @Override
    public int compareTo(CopyJob other) {
      return Long.compare(ord, other.ord);
    }

    @Override
    public void start() {}

    @Override
    protected CopyOneFile newCopyOneFile(CopyOneFile current) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void runBlocking() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean conflicts(CopyJob other) {
      throw new UnsupportedOperationException();
    }
  }
}

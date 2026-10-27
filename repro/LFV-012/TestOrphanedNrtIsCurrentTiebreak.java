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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DirectoryReader#isCurrent javadoc: for a near real-time reader "this method checks if either a
 * new commit has occurred, or any new uncommitted changes have taken place via the writer".
 *
 * <p>Each test keeps an NRT reader after its writer ended without committing, then lets a second
 * writer commit different content to the same directory, and checks that the old reader never
 * reports isCurrent() == true while its documents differ from the latest commit. Default package:
 * public API only. No randomized configuration, so the version sequence is fixed.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints the version sequence as evidence")
public class TestOrphanedNrtIsCurrentTiebreak extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig noMerges() {
    return new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Set<String> ids(DirectoryReader r) throws IOException {
    Set<String> s = new TreeSet<>();
    StoredFields sf = r.storedFields();
    for (int i = 0; i < r.maxDoc(); i++) {
      s.add(sf.document(i).get("id"));
    }
    return s;
  }

  private static Set<String> committedIds(Directory dir) throws IOException {
    try (DirectoryReader fresh = DirectoryReader.open(dir)) {
      return ids(fresh);
    }
  }

  /** Writer 1: commit "A", then k uncommitted documents, each made visible by an NRT refresh. */
  private static DirectoryReader nrtReaderWithUncommittedDocs(IndexWriter w1, int k)
      throws IOException {
    w1.addDocument(doc("A"));
    w1.commit();
    DirectoryReader r = DirectoryReader.open(w1);
    for (int i = 1; i <= k; i++) {
      w1.addDocument(doc("W1-" + i));
      DirectoryReader n = DirectoryReader.openIfChanged(r, w1);
      assertNotNull(n);
      r.close();
      r = n;
    }
    return r;
  }

  /**
   * Writer 2 adds and commits one document at a time. Returns a description of every commit after
   * which the old reader says isCurrent() == true although its documents differ.
   */
  private static List<String> secondSession(
      String label, Directory dir, IndexWriterConfig config, DirectoryReader old, int commits)
      throws IOException {
    List<String> wrong = new ArrayList<>();
    Set<String> oldIds = ids(old);
    try (IndexWriter w2 = new IndexWriter(dir, config)) {
      for (int j = 1; j <= commits; j++) {
        w2.addDocument(doc("W2-" + j));
        w2.commit();
        SegmentInfos latest = SegmentInfos.readLatestCommit(dir);
        Set<String> nowIds = committedIds(dir);
        boolean current = old.isCurrent();
        String line =
            String.format(
                Locale.ROOT,
                "%s: writer-2 commit #%d -> %s version=%d ids=%s | old NRT reader version=%d ids=%s"
                    + " | isCurrent()=%s",
                label,
                j,
                latest.getSegmentsFileName(),
                latest.getVersion(),
                nowIds,
                old.getVersion(),
                oldIds,
                current);
        System.out.println(line);
        if (current && nowIds.equals(oldIds) == false) {
          wrong.add(line);
        }
      }
    }
    return wrong;
  }

  /** Sweep k = 1..8 uncommitted NRT refreshes before rollback(); writer 2 commits 8 times. */
  public void testSweepAfterRollback() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int k = 1; k <= 8; k++) {
      try (Directory dir = new ByteBuffersDirectory()) {
        IndexWriter w1 = new IndexWriter(dir, noMerges());
        DirectoryReader old = nrtReaderWithUncommittedDocs(w1, k);
        w1.rollback();
        try {
          wrong.addAll(secondSession("sweep k=" + k, dir, noMerges(), old, 8));
        } finally {
          old.close();
        }
      }
    }
    assertTrue(
        "isCurrent() returned true for an old NRT reader whose documents differ from the latest"
            + " commit: "
            + wrong,
        wrong.isEmpty());
  }

  /** Same scenario with an entirely default IndexWriterConfig (default merge policy/scheduler). */
  public void testDefaultConfigAfterRollback() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, new IndexWriterConfig());
      DirectoryReader old = nrtReaderWithUncommittedDocs(w1, 3);
      w1.rollback();
      try {
        List<String> wrong =
            secondSession("default-config k=3", dir, new IndexWriterConfig(), old, 4);
        assertTrue("isCurrent() true for different content: " + wrong, wrong.isEmpty());
      } finally {
        old.close();
      }
    }
  }

  /** A directory that fails every createOutput while armed (simulates a full disk). */
  private static final class FailingDirectory extends FilterDirectory {
    volatile boolean fail;

    FailingDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      if (fail) {
        throw new IOException("simulated disk full while creating " + name);
      }
      return super.createOutput(name, context);
    }
  }

  /**
   * No explicit rollback: an I/O error during an NRT refresh flush is a tragic event, and the
   * writer closes itself through its internal rollback. The application then opens a new writer.
   */
  public void testTragicEventDuringNrtRefresh() throws IOException {
    try (FailingDirectory dir = new FailingDirectory(new ByteBuffersDirectory())) {
      IndexWriter w1 = new IndexWriter(dir, noMerges());
      final DirectoryReader old = nrtReaderWithUncommittedDocs(w1, 3);
      w1.addDocument(doc("W1-4"));
      dir.fail = true;
      Throwable t = expectThrows(Throwable.class, () -> DirectoryReader.openIfChanged(old, w1));
      dir.fail = false;
      System.out.println("tragic: openIfChanged threw " + t);
      System.out.println(
          "tragic: w1.isOpen()=" + w1.isOpen() + " w1.getTragicException()=" + w1.getTragicException());
      assertFalse(w1.isOpen());
      assertNotNull(w1.getTragicException());
      try {
        List<String> wrong = secondSession("tragic k=3", dir, noMerges(), old, 4);
        assertTrue("isCurrent() true for different content: " + wrong, wrong.isEmpty());
      } finally {
        old.close();
      }
    }
  }

  /** Control: writer 1 commits on close, so the version stays monotonic; must pass. */
  public void testControlCommitOnClose() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, noMerges());
      DirectoryReader old = nrtReaderWithUncommittedDocs(w1, 3);
      w1.close(); // commitOnClose=true (default)
      try {
        assertFalse(old.isCurrent()); // the close wrote a new commit
        List<String> wrong = secondSession("control commit-on-close k=3", dir, noMerges(), old, 4);
        assertTrue(wrong.isEmpty());
      } finally {
        old.close();
      }
    }
  }

  /** Control: a legitimately current NRT reader of a closed writer stays current; must pass. */
  public void testControlClosedWriterNoNewCommit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, noMerges());
      w1.addDocument(doc("A"));
      w1.commit();
      DirectoryReader r = DirectoryReader.open(w1);
      w1.close(); // nothing changed since the commit: no new commit is written
      try {
        System.out.println(
            "control no-new-commit: latest="
                + SegmentInfos.readLatestCommit(dir).getSegmentsFileName()
                + " version="
                + SegmentInfos.readLatestCommit(dir).getVersion()
                + " reader version="
                + r.getVersion()
                + " isCurrent()="
                + r.isCurrent());
        assertTrue(r.isCurrent());
        try (IndexWriter w2 = new IndexWriter(dir, noMerges())) {
          w2.addDocument(doc("W2-1"));
          w2.commit();
        }
        assertFalse(r.isCurrent());
      } finally {
        r.close();
      }
    }
  }
}

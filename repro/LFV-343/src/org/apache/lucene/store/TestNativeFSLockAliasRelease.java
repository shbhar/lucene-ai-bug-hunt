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
package org.apache.lucene.store;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Constants;

/**
 * NativeFSLockFactory keeps a JVM-wide set (LOCK_HELD) of lock-file paths so that it never opens a
 * second FileChannel on a lock file this JVM already holds: on POSIX systems closing any channel
 * of a file releases every fcntl lock the process holds on it (LUCENE-5738). The set is keyed by
 * the path string returned by toRealPath(), which does not identify the file when the same lock
 * file is reachable under two real paths (a hard link, a bind mount). Then obtainLock opens a
 * channel on the already-locked file, tryLock throws
 * OverlappingFileLockException, the error path closes the channel, and the OS lock of the first
 * holder is gone while its ensureValid() keeps passing: another process can then obtain the same
 * write.lock.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestNativeFSLockAliasRelease extends LuceneTestCase {

  /** Child-process entry point: tries to obtain the lock (or open an IndexWriter) in args[0]. */
  public static final class Child {
    public static void main(String[] args) throws Exception {
      Path path = Paths.get(args[0]);
      boolean writer = args.length > 1 && args[1].equals("writer");
      try (FSDirectory dir = FSDirectory.open(path)) {
        if (writer) {
          try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new StringField("id", "child", Field.Store.NO));
            w.addDocument(doc);
            w.commit(); // acknowledged commit
            System.out.println("OBTAINED");
          }
        } else {
          try (Lock _ = dir.obtainLock(IndexWriter.WRITE_LOCK_NAME)) {
            System.out.println("OBTAINED");
          }
        }
      } catch (LockObtainFailedException _) {
        System.out.println("REFUSED");
      }
    }
  }

  /** Runs {@link Child} in a separate JVM and returns what it printed ("OBTAINED"/"REFUSED"). */
  private static String obtainInOtherProcess(Path dir, boolean writer) throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));
    cmd.add(Child.class.getName());
    cmd.add(dir.toString());
    if (writer) {
      cmd.add("writer");
    }
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String out;
    try (InputStream in = p.getInputStream()) {
      out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue("child did not exit", p.waitFor(120, TimeUnit.SECONDS));
    assertEquals("child output: " + out, 0, p.exitValue());
    if (out.contains("OBTAINED")) {
      return "OBTAINED";
    } else if (out.contains("REFUSED")) {
      return "REFUSED";
    }
    throw new AssertionError("unexpected child output: " + out);
  }

  private static void assumePosixLocks() {
    // The release-on-close semantics are those of POSIX fcntl locks (Linux, macOS, ...).
    assumeFalse("POSIX record-lock semantics only", Constants.WINDOWS);
  }

  /** Same lock file reachable through a second directory (hard link, e.g. a hard-link snapshot). */
  public void testObtainThroughHardLinkedLockFileReleasesLock() throws Exception {
    assumePosixLocks();
    Path a = createTempDir("index");
    Path b = createTempDir("snapshot");
    try (FSDirectory dirA = FSDirectory.open(a);
        Lock lockA = dirA.obtainLock(IndexWriter.WRITE_LOCK_NAME)) {
      lockA.ensureValid();
      // control: while lockA is held, another process cannot obtain the lock
      assertEquals("REFUSED", obtainInOtherProcess(a, false));

      Files.createLink(b.resolve(IndexWriter.WRITE_LOCK_NAME), a.resolve(IndexWriter.WRITE_LOCK_NAME));
      Throwable t;
      try (FSDirectory dirB = FSDirectory.open(b)) {
        t = expectThrows(Throwable.class, () -> dirB.obtainLock(IndexWriter.WRITE_LOCK_NAME));
      }

      lockA.ensureValid(); // passes: lockA claims it still holds the lock
      assertEquals(
          "another process obtained write.lock while lockA is held and lockA.ensureValid() passes"
              + " (the obtain through the hard link threw "
              + t
              + ")",
          "REFUSED",
          obtainInOtherProcess(a, false));
    }
  }

  /**
   * IndexWriter level: an IndexWriter is open on an index; a hard-link snapshot of the index
   * directory (every file, including write.lock) is opened by a second IndexWriter in the same JVM,
   * which fails; afterwards an IndexWriter in another process opens and commits on the live index.
   */
  public void testIndexWriterOnHardLinkSnapshotLetsSecondProcessWrite() throws Exception {
    assumePosixLocks();
    Path a = createTempDir("index");
    Path b = createTempDir("snapshot");
    try (FSDirectory dirA = FSDirectory.open(a);
        IndexWriter w = new IndexWriter(dirA, new IndexWriterConfig())) {
      w.addDocument(new Document());
      w.commit();
      assertEquals("REFUSED", obtainInOtherProcess(a, true)); // control

      for (String file : dirA.listAll()) {
        Files.createLink(b.resolve(file), a.resolve(file)); // hard-link snapshot of the directory
      }
      Throwable t;
      try (FSDirectory dirB = FSDirectory.open(b)) {
        t = expectThrows(Throwable.class, () -> new IndexWriter(dirB, new IndexWriterConfig()));
      }

      w.addDocument(new Document());
      w.commit(); // the first writer keeps working: its lock still passes ensureValid
      assertEquals(
          "an IndexWriter in another process opened (and committed to) the index"
              + " while this process's IndexWriter is open (the IndexWriter on the snapshot threw "
              + t
              + ")",
          "REFUSED",
          obtainInOtherProcess(a, true));
    }
  }

  /**
   * Consequence: once the other process has obtained the lock, both writers commit to the same
   * index and the first writer's next commit replaces the other writer's acknowledged
   * commit (same segments_N generation). Passes if the other process is correctly refused.
   */
  public void testOtherWritersAcknowledgedCommitIsLost() throws Exception {
    assumePosixLocks();
    Path a = createTempDir("index");
    Path b = createTempDir("snapshot");
    try (FSDirectory dirA = FSDirectory.open(a)) {
      IndexWriter w = new IndexWriter(dirA, new IndexWriterConfig());
      Document doc = new Document();
      doc.add(new StringField("id", "parent", Field.Store.NO));
      w.addDocument(doc);
      w.commit();

      for (String file : dirA.listAll()) {
        Files.createLink(b.resolve(file), a.resolve(file)); // hard-link snapshot of the directory
      }
      try (FSDirectory dirB = FSDirectory.open(b)) {
        expectThrows(Throwable.class, () -> new IndexWriter(dirB, new IndexWriterConfig()));
      }

      String child = obtainInOtherProcess(a, true); // adds doc "child" and commits, if it gets the lock
      w.deleteDocuments(new Term("id", "parent"));
      Throwable commitFailure = null;
      try {
        w.commit(); // this writer's lock still passes ensureValid()
      } catch (Throwable t) {
        commitFailure = t;
      } finally {
        w.rollback();
      }

      if (child.equals("OBTAINED")) {
        try (DirectoryReader r = DirectoryReader.open(dirA)) {
          assertEquals(
              "the other process's acknowledged commit (doc 'child') was replaced by this writer's"
                  + " commit (this writer's commit then threw: "
                  + commitFailure
                  + ")",
              1,
              new IndexSearcher(r).count(new TermQuery(new Term("id", "child"))));
        }
      }
    }
  }

  /** The documented same-path case (LUCENE-5738) is handled: this passes on main. */
  public void testControlSamePathSecondObtainKeepsLock() throws Exception {
    assumePosixLocks();
    Path a = createTempDir("index");
    try (FSDirectory dirA = FSDirectory.open(a);
        Lock lockA = dirA.obtainLock(IndexWriter.WRITE_LOCK_NAME);
        FSDirectory dirA2 = FSDirectory.open(a)) {
      expectThrows(LockObtainFailedException.class, () -> dirA2.obtainLock(IndexWriter.WRITE_LOCK_NAME));
      lockA.ensureValid();
      assertEquals("REFUSED", obtainInOtherProcess(a, false));
    }
  }

}

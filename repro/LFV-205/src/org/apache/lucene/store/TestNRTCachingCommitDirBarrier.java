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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Since LUCENE-8048 ("Filesystems do not guarantee order of directories updates") a commit fsyncs
 * the directory before it writes pending_segments_N, so that the names of all files the commit
 * references are durable before the rename that publishes segments_N. NRTCachingDirectory keeps
 * small new files in RAM and creates them in the delegate only inside sync(), i.e. after that
 * directory fsync and before the rename. If the rename reaches stable storage before those
 * creates (allowed until the next directory fsync), the newest commit references files that do not
 * exist and the index cannot be opened.
 *
 * <p>Crash state replayed (one state of the model "directory operations since the last directory
 * fsync may persist in any combination"): the names durable at the last syncMetaData() before the
 * rename, plus the rename itself; all file bytes are fsynced.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestNRTCachingCommitDirBarrier extends LuceneTestCase {

  /** Captures the crash state at the segments_N rename of the given generation. */
  static final class RenameCrashCapture extends FilterDirectory {
    final String commitToCapture;
    TreeSet<String> durableNames = new TreeSet<>();
    final Directory crashState;
    boolean captured;

    RenameCrashCapture(Directory in, String commitToCapture, Directory crashState) {
      super(in);
      this.commitToCapture = commitToCapture;
      this.crashState = crashState;
    }

    @Override
    public void syncMetaData() throws IOException {
      super.syncMetaData();
      durableNames = new TreeSet<>(List.of(listAll()));
      durableNames.remove(IndexWriter.WRITE_LOCK_NAME);
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      super.rename(source, dest);
      if (dest.equals(commitToCapture) && captured == false) {
        captured = true;
        TreeSet<String> names = new TreeSet<>(durableNames);
        names.remove(source);
        names.add(dest);
        for (String name : names) {
          crashState.copyFrom(in, name, name, IOContext.DEFAULT);
        }
      }
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Runs two commits; returns what DirectoryReader.open says about the crash state. */
  private String run(boolean nrtCaching, String commitToCapture)
      throws IOException {
    Path crashed = createTempDir("crashed");
    try (Directory crashDir = FSDirectory.open(crashed);
        RenameCrashCapture capture =
            new RenameCrashCapture(
                FSDirectory.open(createTempDir("live")), commitToCapture, crashDir)) {
      Directory dir = nrtCaching ? new NRTCachingDirectory(capture, 5.0, 60.0) : capture;
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.commit(); // segments_1 -> _0
        w.addDocument(doc("b"));
        w.commit(); // segments_2 -> _0, _1
      }
      assertTrue("crash state captured", capture.captured);
      try (DirectoryReader r = DirectoryReader.open(crashDir)) {
        return "opened with " + r.numDocs() + " docs";
      } catch (IOException e) {
        return "cannot open the index: " + e + "; files=" + List.of(crashDir.listAll());
      }
    }
  }

  /** Control: plain directory. LUCENE-8048's directory fsync covers every referenced name. */
  public void testControlPlainDirectory() throws Exception {
    String r = run(false, "segments_2");
    assertTrue(r, r.startsWith("opened with"));
  }

  /** NRTCachingDirectory, crash state at the second commit's rename. */
  public void testNRTCachingSecondCommit() throws Exception {
    String r = run(true, "segments_2");
    assertTrue(
        "the rename of pending_segments_2 is durable but the files NRTCachingDirectory created in"
            + " the delegate during sync() (after the commit's directory fsync) are not: "
            + r,
        r.startsWith("opened with"));
  }

  /** NRTCachingDirectory, crash state at the first commit's rename. */
  public void testNRTCachingFirstCommit() throws Exception {
    String r = run(true, "segments_1");
    assertTrue(
        "the rename of pending_segments_1 is durable but the files NRTCachingDirectory created in"
            + " the delegate during sync() (after the commit's directory fsync) are not: "
            + r,
        r.startsWith("opened with"));
  }
}

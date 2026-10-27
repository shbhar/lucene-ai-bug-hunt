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
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherLifetimeManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * An IndexWriter opened with IndexWriterConfig#setIndexCommit(reader.getIndexCommit()) on a reader
 * of an older commit starts SegmentInfos.version from that reader, not from the newest commit. Its
 * commits can then carry the version of a newer commit that a reader still has open, and that reader
 * reports isCurrent() == true (and openIfChanged returns null) although a further commit with
 * different content exists. Opening the writer from the same commit taken from listCommits keeps
 * the version increasing (control).
 */
public class TestWriterFromReaderCommitVersionReuse extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config() {
    // keep every commit so that the older commit can be opened again
    return new IndexWriterConfig()
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE);
  }

  private static TreeSet<String> ids(IndexReader r) throws IOException {
    TreeSet<String> out = new TreeSet<>();
    StoredFields sf = r.storedFields();
    Bits live = MultiBits.getLiveDocs(r);
    for (int i = 0; i < r.maxDoc(); i++) {
      if (live == null || live.get(i)) {
        out.add(sf.document(i).get("id"));
      }
    }
    return out;
  }

  /**
   * Commits user-data-only changes with the writer and checks, after each commit, that the reader
   * {@code newer} (open on a newer, now superseded commit) sees that a further commit happened.
   */
  private void commitAndCheck(Directory dir, IndexWriter w, DirectoryReader newer) throws IOException {
    for (int i = 0; i < 4; i++) {
      w.setLiveCommitData(Map.of("step", Integer.toString(i)).entrySet());
      w.commit();
      try (DirectoryReader latest = DirectoryReader.open(dir)) {
        String state =
            "after commit "
                + i
                + ": latest commit "
                + latest.getIndexCommit().getSegmentsFileName()
                + " version="
                + latest.getVersion()
                + " ids="
                + ids(latest)
                + "; open reader on "
                + newer.getIndexCommit().getSegmentsFileName()
                + " version="
                + newer.getVersion()
                + " ids="
                + ids(newer);
        if (VERBOSE) {
          System.out.println(state);
        }
        // DirectoryReader#isCurrent: for a reader created by open(...), "this method checks if any
        // further commits (see IndexWriter#commit) have occurred in the directory".
        assertFalse("isCurrent() is true although a further commit exists; " + state, newer.isCurrent());
        DirectoryReader reopened = DirectoryReader.openIfChanged(newer);
        assertNotNull("openIfChanged returned null although the index changed; " + state, reopened);
        assertEquals(ids(latest), ids(reopened));
        reopened.close();
      }
    }
  }

  /** Writer opened from a commit (non-NRT) reader of the older commit. */
  public void testWriterFromCommitReader() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, config());
      w1.addDocument(doc("a"));
      w1.commit(); // commit 1
      DirectoryReader older = DirectoryReader.open(dir);
      w1.addDocument(doc("b"));
      w1.commit();
      w1.addDocument(doc("c"));
      w1.commit(); // commit 3
      w1.close();
      DirectoryReader newer = DirectoryReader.open(dir); // e.g. an application's searcher

      // go back to commit 1, through the reader's IndexCommit (supported, see
      // TestIndexWriterFromReader#testFromNonNRTReader)
      IndexWriter w2 = new IndexWriter(dir, config().setIndexCommit(older.getIndexCommit()));
      try {
        commitAndCheck(dir, w2, newer);
      } finally {
        w2.close();
        older.close();
        newer.close();
      }
    }
  }

  /**
   * Same with a near-real-time reader of the older commit whose writer is closed: the writer takes
   * the version from that reader's (closed) writer, not from commits made by a later writer.
   */
  public void testWriterFromNrtReaderAfterLaterSession() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, config());
      w1.addDocument(doc("a"));
      w1.commit(); // commit 1
      DirectoryReader older = DirectoryReader.open(w1);
      w1.close();

      IndexWriter w2 = new IndexWriter(dir, config());
      w2.addDocument(doc("b"));
      w2.commit();
      w2.addDocument(doc("c"));
      w2.commit();
      w2.close();
      DirectoryReader newer = DirectoryReader.open(dir);

      IndexWriter w3 = new IndexWriter(dir, config().setIndexCommit(older.getIndexCommit()));
      try {
        commitAndCheck(dir, w3, newer);
      } finally {
        w3.close();
        older.close();
        newer.close();
      }
    }
  }

  /**
   * The reused version also confuses SearcherLifetimeManager, which tracks searchers by
   * DirectoryReader#getVersion: recording a searcher of the new commit fails because a searcher of a
   * different commit already holds that version.
   */
  public void testSearcherLifetimeManagerSeesTwoPointsWithOneVersion() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, config());
      w1.addDocument(doc("a"));
      w1.commit();
      DirectoryReader older = DirectoryReader.open(dir);
      w1.addDocument(doc("b"));
      w1.commit();
      w1.addDocument(doc("c"));
      w1.commit();
      w1.close();
      DirectoryReader newer = DirectoryReader.open(dir);
      SearcherLifetimeManager mgr = new SearcherLifetimeManager();
      mgr.record(new IndexSearcher(newer));

      IndexWriter w2 = new IndexWriter(dir, config().setIndexCommit(older.getIndexCommit()));
      try {
        for (int i = 0; i < 4; i++) {
          w2.setLiveCommitData(Map.of("step", Integer.toString(i)).entrySet());
          w2.commit();
          // every new commit is a different point in time; recording a searcher on it must work
          DirectoryReader latest = DirectoryReader.open(dir);
          try {
            mgr.record(new IndexSearcher(latest));
          } finally {
            latest.close();
          }
        }
      } finally {
        w2.close();
        mgr.close();
        older.close();
        newer.close();
      }
    }
  }

  /** Control: the same commit taken from listCommits keeps the version increasing. */
  public void testControlWriterFromListedCommit() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, config());
      w1.addDocument(doc("a"));
      w1.commit();
      IndexCommit first = DirectoryReader.listCommits(dir).get(0);
      w1.addDocument(doc("b"));
      w1.commit();
      w1.addDocument(doc("c"));
      w1.commit();
      w1.close();
      DirectoryReader newer = DirectoryReader.open(dir);

      IndexWriter w2 = new IndexWriter(dir, config().setIndexCommit(first));
      try {
        commitAndCheck(dir, w2, newer);
      } finally {
        w2.close();
        newer.close();
      }
    }
  }
}

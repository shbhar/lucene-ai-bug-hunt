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
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An upgrade through UpgradeIndexMergePolicy, nested around a SoftDeletesRetentionMergePolicy the
 * way IndexUpgrader nests it, must keep the soft-deleted documents that the retention policy
 * retains.
 */
public class TestUpgradeIndexMergePolicyRetainsSoftDeletes extends LuceneTestCase {

  private static final String SOFT = "soft";

  /** Treats the given segments as written by an older release, so no old index is needed. */
  private static MergePolicy upgrade(MergePolicy in, Set<String> oldSegments) {
    return new UpgradeIndexMergePolicy(in) {
      @Override
      protected boolean shouldUpgradeSegment(SegmentCommitInfo si) {
        return oldSegments.contains(si.info.name);
      }
    };
  }

  /** Keeps every soft-deleted document. */
  private static MergePolicy retainAll(MergePolicy in) {
    return new SoftDeletesRetentionMergePolicy(SOFT, MatchAllDocsQuery::new, in);
  }

  /** 10 documents, then soft updates of ids 7, 8 and 9: 13 documents, 3 of them soft-deleted. */
  private static Set<String> writeIndex(Directory dir, boolean twoSegments) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10; i++) {
        w.addDocument(doc(i));
        if (twoSegments && i == 4) {
          w.commit();
        }
      }
      for (int i = 7; i < 10; i++) {
        w.softUpdateDocument(
            new Term("id", Integer.toString(i)), doc(i), new NumericDocValuesField(SOFT, 1));
      }
      w.commit();
    }
    Set<String> names = new HashSet<>();
    for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dir)) {
      names.add(sci.info.name);
    }
    assertEquals(twoSegments ? 2 : 1, names.size());
    return names;
  }

  private static Document doc(int id) {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(id), Field.Store.YES));
    return doc;
  }

  /** Upgrades like IndexUpgrader (fresh writer, forceMerge(1), commit); asserts what survived. */
  private static void upgradeAndCheck(Directory dir, MergePolicy mp, Set<String> old)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(mp);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.forceMerge(1);
      w.commit();
    }
    for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dir)) {
      assertFalse("old segment was not rewritten: " + sci, old.contains(sci.info.name));
    }
    try (DirectoryReader r =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
      assertEquals("visible documents", 10, r.numDocs());
      assertEquals("retained soft-deleted documents were dropped", 13, r.maxDoc());
    }
  }

  /** The bug: the delegate declines a lone old segment and UpgradeIndexMergePolicy merges it. */
  public void testOneOldSegment() throws IOException {
    try (Directory dir = newDirectory()) {
      Set<String> old = writeIndex(dir, false);
      upgradeAndCheck(dir, upgrade(retainAll(new TieredMergePolicy()), old), old);
    }
  }

  /** Control: the delegate merges two old segments itself, and the retention policy wraps it. */
  public void testTwoOldSegments() throws IOException {
    try (Directory dir = newDirectory()) {
      Set<String> old = writeIndex(dir, true);
      upgradeAndCheck(dir, upgrade(retainAll(new TieredMergePolicy()), old), old);
    }
  }

  /** Control: with the retention policy outermost, UpgradeIndexMergePolicy's merge is wrapped. */
  public void testRetentionPolicyOutermost() throws IOException {
    try (Directory dir = newDirectory()) {
      Set<String> old = writeIndex(dir, false);
      upgradeAndCheck(dir, retainAll(upgrade(new TieredMergePolicy(), old)), old);
    }
  }
}

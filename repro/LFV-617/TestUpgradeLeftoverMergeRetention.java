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
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verifier repro (fixture-free). UpgradeIndexMergePolicy's protected
 * shouldUpgradeSegment hook is overridden to treat the segments written in phase 1 as "old", so
 * the upgrade code path runs without an index written by an older release.
 *
 * <p>Index: 10 visible docs + 3 soft-deleted older versions (ids 7, 8, 9). The upgrade runs on a
 * freshly opened writer, as IndexUpgrader does.
 */
public class TestUpgradeLeftoverMergeRetention extends LuceneTestCase {

  private static final String SOFT = "soft_del";

  /** Treats only the segments named at construction time as "old". */
  static final class UpgradeNamed extends UpgradeIndexMergePolicy {
    private final Set<String> oldNames;

    UpgradeNamed(MergePolicy in, Set<String> oldNames) {
      super(in);
      this.oldNames = oldNames;
    }

    @Override
    protected boolean shouldUpgradeSegment(SegmentCommitInfo si) {
      return oldNames.contains(si.info.name);
    }
  }

  private static final Supplier<Query> RETAIN_ALL = MatchAllDocsQuery::new;

  /** Retains the soft-deleted versions of ids 7 and 8 only (2 of 3). */
  private static final Supplier<Query> RETAIN_TWO =
      () -> {
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        b.add(new TermQuery(new Term("id", "7")), BooleanClause.Occur.SHOULD);
        b.add(new TermQuery(new Term("id", "8")), BooleanClause.Occur.SHOULD);
        return b.build();
      };

  /**
   * Writes 10 docs and soft-updates ids 7..9 (13 docs, 3 soft-deleted). twoSegments: a commit
   * after the first 5 docs gives a second segment. hardDelete: id 0 is also hard-deleted.
   */
  private static Set<String> writeIndex(Directory dir, boolean twoSegments, boolean hardDelete)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setSoftDeletesField(SOFT);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        d.add(new StringField("ver", "1", Field.Store.YES));
        w.addDocument(d);
        if (twoSegments && i == 4) {
          w.commit();
        }
      }
      for (int i = 7; i < 10; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        d.add(new StringField("ver", "2", Field.Store.YES));
        w.softUpdateDocument(
            new Term("id", Integer.toString(i)), d, new NumericDocValuesField(SOFT, 1));
      }
      if (hardDelete) {
        w.deleteDocuments(new Term("id", "0"));
      }
      w.commit();
    }
    Set<String> names = new HashSet<>();
    for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dir)) {
      names.add(sci.info.name);
    }
    return names;
  }

  /** {maxDoc, docs carrying the soft-delete field, number of leaves}. */
  private static int[] counts(Directory dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      int soft = new IndexSearcher(r).count(new FieldExistsQuery(SOFT));
      return new int[] {r.maxDoc(), soft, r.leaves().size()};
    }
  }

  /**
   * forceMerge(1) through the upgrade policy on a freshly opened writer; asserts every phase-1
   * segment was rewritten and the expected docs survived.
   */
  private void upgradeAndCheck(
      boolean twoSegments,
      boolean hardDelete,
      Supplier<Query> retention,
      boolean retentionOutermost,
      int expectedMaxDoc,
      int expectedSoft)
      throws IOException {
    try (Directory dir = newDirectory()) {
      Set<String> oldNames = writeIndex(dir, twoSegments, hardDelete);
      assertEquals(twoSegments ? 2 : 1, oldNames.size());
      int[] before = counts(dir);
      assertEquals("maxDoc before", 13, before[0]);
      assertEquals("soft-deleted before", 3, before[1]);

      MergePolicy mp =
          retentionOutermost
              ? new SoftDeletesRetentionMergePolicy(
                  SOFT, retention, new UpgradeNamed(new TieredMergePolicy(), oldNames))
              : new UpgradeNamed(
                  new SoftDeletesRetentionMergePolicy(SOFT, retention, new TieredMergePolicy()),
                  oldNames);
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setSoftDeletesField(SOFT);
      iwc.setMergePolicy(mp);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
        w.commit();
      }
      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dir)) {
        assertFalse("segment was not rewritten: " + sci, oldNames.contains(sci.info.name));
      }
      int[] after = counts(dir);
      assertEquals("leaves after", 1, after[2]);
      assertEquals("maxDoc after upgrade merge (retained soft deletes)", expectedMaxDoc, after[0]);
      assertEquals("soft-deleted docs after upgrade merge", expectedSoft, after[1]);
    }
  }

  /** BUG case: one old segment, retention keeps all 3 soft-deleted docs. Expect 13; fails. */
  public void testUpgradeOuterOneSegmentRetainAll() throws IOException {
    upgradeAndCheck(false, false, RETAIN_ALL, false, 13, 3);
  }

  /** BUG case: one old segment, retention keeps 2 of 3. Expect 12; fails. */
  public void testUpgradeOuterOneSegmentRetainTwo() throws IOException {
    upgradeAndCheck(false, false, RETAIN_TWO, false, 12, 2);
  }

  /** Control: two old segments, the delegate merges them (wrapped). Expect 13; passes. */
  public void testUpgradeOuterTwoSegmentsRetainAll() throws IOException {
    upgradeAndCheck(true, false, RETAIN_ALL, false, 13, 3);
  }

  /**
   * Control: one old segment that also has a hard delete, so the delegate merges it itself
   * (wrapped). Expect 12 (13 - 1 hard delete) with all 3 soft-deleted docs; passes.
   */
  public void testUpgradeOuterOneSegmentWithHardDeleteRetainAll() throws IOException {
    upgradeAndCheck(false, true, RETAIN_ALL, false, 12, 3);
  }

  /** Control: retention policy outermost also wraps the leftover merge. Expect 13; passes. */
  public void testRetentionOuterOneSegmentRetainAll() throws IOException {
    upgradeAndCheck(false, false, RETAIN_ALL, true, 13, 3);
  }

  /** Control: retention outermost, partial retention. Expect 12; passes. */
  public void testRetentionOuterOneSegmentRetainTwo() throws IOException {
    upgradeAndCheck(false, false, RETAIN_TWO, true, 12, 2);
  }
}

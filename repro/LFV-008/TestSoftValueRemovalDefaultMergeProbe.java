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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Probe: does the revival of a hard-deleted doc become permanent with the DEFAULT merge policy and
 * merge scheduler (TieredMergePolicy, ConcurrentMergeScheduler)? Only setSoftDeletesField is set.
 * The doc ID of a is looked up in the NRT reader, so a merge on NRT open does not matter.
 */
public class TestSoftValueRemovalDefaultMergeProbe extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static List<String> liveIds(IndexReader r) throws IOException {
    List<String> ids = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      StoredFields stored = ctx.reader().storedFields();
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live == null || live.get(d)) {
          ids.add(stored.document(d).get("id"));
        }
      }
    }
    ids.sort(null);
    return ids;
  }

  private static int docIdOf(DirectoryReader r, String id) throws IOException {
    TopDocs td = new IndexSearcher(r).search(new TermQuery(new Term("id", id)), 1);
    assertEquals(1, td.totalHits.value());
    return td.scoreDocs[0].doc;
  }

  /** b soft-deleted, a hard-deleted, a's soft value removed; returns after the NRT reader closed. */
  private static void revive(IndexWriter w) throws IOException {
    w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SOFT, 1));
    try (DirectoryReader r = DirectoryReader.open(w)) {
      StringBuilder segs = new StringBuilder();
      for (LeafReaderContext ctx : r.leaves()) {
        segs.append(" [maxDoc=").append(ctx.reader().maxDoc())
            .append(" numDeleted=").append(ctx.reader().numDeletedDocs()).append("]");
      }
      System.out.println("NRT segments before the hard delete:" + segs + " live=" + liveIds(r));
      int a = docIdOf(r, "a");
      w.deleteDocuments(new Term("id", "a"));
      assertTrue(w.tryUpdateDocValue(r, a, new NumericDocValuesField(SOFT, (Long) null)) > 0);
    }
    try (DirectoryReader r = DirectoryReader.open(w)) {
      System.out.println("NRT live after the sequence: " + liveIds(r));
    }
  }

  private static IndexWriterConfig defaults() {
    return new IndexWriterConfig().setSoftDeletesField(SOFT);
  }

  /** Default merge policy wrapped in a retention policy that keeps every soft-deleted doc. */
  private static IndexWriterConfig retention() {
    return new IndexWriterConfig()
        .setSoftDeletesField(SOFT)
        .setMergePolicy(
            new SoftDeletesRetentionMergePolicy(SOFT, MatchAllDocsQuery::new, new TieredMergePolicy()));
  }

  /** Live ids of a commit, with soft deletes applied. */
  private static List<String> softLiveIds(Directory dir) throws IOException {
    try (DirectoryReader r = new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
      return liveIds(r);
    }
  }

  public void testDefaultConfigForceMerge() throws Exception {
    forceMergeCase(defaults());
  }

  public void testRetentionPolicyForceMerge() throws Exception {
    forceMergeCase(retention());
  }

  public void testDefaultConfigBackgroundMerges() throws Exception {
    backgroundCase(defaults());
  }

  public void testRetentionPolicyBackgroundMerges() throws Exception {
    backgroundCase(retention());
  }

  /** forceMerge(1), commit, close. */
  private static void forceMergeCase(IndexWriterConfig iwc) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.addDocument(doc("b"));
        w.addDocument(doc("c"));
        w.commit();
        w.addDocument(doc("x"));
        w.commit();
        revive(w);
        w.forceMerge(1);
        w.commit();
      }
      TestUtil.checkIndex(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println(
            "forceMerge: committed segments=" + r.leaves().size() + " hard-live=" + liveIds(r)
                + " soft-live=" + softLiveIds(dir));
      }
      assertEquals("committed live docs (soft deletes applied)", List.of("c", "x"), softLiveIds(dir));
    }
  }

  /** No forceMerge: 30 more small commits so that background merges run, then close. */
  private static void backgroundCase(IndexWriterConfig iwc) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a"));
        w.addDocument(doc("b"));
        w.addDocument(doc("c"));
        w.commit();
        revive(w);
        for (int i = 0; i < 30; i++) {
          w.addDocument(doc("y" + i));
          w.commit();
        }
      } // close waits for running merges and commits
      TestUtil.checkIndex(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        List<String> live = softLiveIds(dir);
        System.out.println(
            "background: committed segments=" + r.leaves().size() + " a live=" + live.contains("a")
                + " b live=" + live.contains("b") + " live count=" + live.size());
        assertFalse("hard-deleted a must not be live in the commit", live.contains("a"));
      }
    }
  }
}

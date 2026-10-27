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
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.Map;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.replicator.nrt.FileMetaData;
import org.apache.lucene.replicator.nrt.PrimaryNode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Lucene's NRT segment replication (lucene/replicator nrt.PrimaryNode) opens its SearcherManager
 * with writeAllDeletes=true and publishes a new CopyState only if the SIS version moved. A
 * doc-values-only change (DV update, soft delete) is then never published to replicas, and is not
 * even visible on the primary's own searcher, until some other change bumps the version.
 */
public class TestGivenBugPrimaryNode extends LuceneTestCase {

  static final class MinimalPrimary extends PrimaryNode {
    MinimalPrimary(IndexWriter w) throws IOException {
      super(w, 0, 0, -1, null, new PrintStream(OutputStream.nullOutputStream()));
    }

    @Override
    protected void preCopyMergedSegmentFiles(SegmentCommitInfo info, Map<String, FileMetaData> files) {}
  }

  public void testPrimaryNodeFlushAndRefreshPublishesDocValuesUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, TestGivenBugProbes.iwc(true, true));
      MinimalPrimary primary = new MinimalPrimary(w);
      try {
        w.addDocument(TestGivenBugProbes.doc("1", 1));
        w.addDocument(TestGivenBugProbes.doc("2", 1));
        assertTrue("control: newly added docs are published", primary.flushAndRefresh());
        long v1 = primary.getCopyStateVersion();

        w.updateNumericDocValue(new Term("id", "1"), "val", 2);
        w.updateDocValues(new Term("id", "2"), new NumericDocValuesField(TestGivenBugProbes.SOFT, 1));
        boolean changed = primary.flushAndRefresh();
        long v2 = primary.getCopyStateVersion();

        IndexSearcher s = primary.getSearcherManager().acquire();
        Map<String, String> seen;
        try {
          seen = TestGivenBugProbes.view(s.getIndexReader());
        } finally {
          primary.getSearcherManager().release(s);
        }
        Map<String, String> fresh;
        try (DirectoryReader f = DirectoryReader.open(w)) {
          fresh = TestGivenBugProbes.view(f);
        }
        assertTrue(
            "flushAndRefresh must report a change (version "
                + v1
                + " -> "
                + v2
                + ", primary searcher view "
                + seen
                + ", fresh view "
                + fresh
                + ")",
            changed && v2 != v1 && seen.equals(fresh));
      } finally {
        primary.close();
      }
    }
  }
}

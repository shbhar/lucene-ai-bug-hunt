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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUpgradeIndexMergePolicyRetainsSoftDeletes extends LuceneTestCase {

  public void testLoneOldSegmentKeepsRetainedSoftDeletes() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setSoftDeletesField("soft");
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        w.addDocument(doc);
        w.softUpdateDocument(new Term("id", "1"), doc, new NumericDocValuesField("soft", 1));
      }
      String oldSegment = SegmentInfos.readLatestCommit(dir).info(0).info.name;
      MergePolicy upgrade =
          new UpgradeIndexMergePolicy(
              new SoftDeletesRetentionMergePolicy(
                  "soft", MatchAllDocsQuery::new, new TieredMergePolicy())) {
            @Override
            protected boolean shouldUpgradeSegment(SegmentCommitInfo si) {
              return si.info.name.equals(oldSegment);
            }
          };
      iwc = new IndexWriterConfig().setSoftDeletesField("soft").setMergePolicy(upgrade);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // expected: the retained soft-deleted doc survives the upgrade (maxDoc 2); actual: maxDoc 1
        assertEquals(2, r.maxDoc());
      }
    }
  }
}

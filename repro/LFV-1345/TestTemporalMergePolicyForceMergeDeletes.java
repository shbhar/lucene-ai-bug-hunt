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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTemporalMergePolicyForceMergeDeletes extends LuceneTestCase {

  public void testOldSegmentsFromDifferentWindowsStayApart() throws Exception {
    TemporalMergePolicy mp =
        new TemporalMergePolicy().setTemporalField("ts").setMaxAgeSeconds(30 * 86_400L);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setMergePolicy(mp)
                    .setMergeScheduler(new SerialMergeScheduler()))) {
      for (long ts : new long[] {1_577_836_800L, 1_609_459_200L}) {
        Document deleted = new Document();
        deleted.add(new StringField("id", "x", Field.Store.NO));
        deleted.add(new LongPoint("ts", ts));
        w.addDocument(deleted);
        Document kept = new Document();
        kept.add(new LongPoint("ts", ts));
        w.addDocument(kept);
        w.commit();
      }
      w.deleteDocuments(new Term("id", "x"));
      w.forceMergeDeletes();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected 2 segments (2020 and 2021 windows, both over-age); actual 1 merged segment
        assertEquals(2, r.leaves().size());
      }
    }
  }
}

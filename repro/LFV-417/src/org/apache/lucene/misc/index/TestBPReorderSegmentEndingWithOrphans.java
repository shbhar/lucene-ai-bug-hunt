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
package org.apache.lucene.misc.index;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Deleting the parent document of the last block of a segment leaves that segment ending with
 * children that have no parent after them. A later reordering merge through BPReorderingMergePolicy
 * with BPIndexReorderer must still succeed.
 */
public class TestBPReorderSegmentEndingWithOrphans extends LuceneTestCase {

  public void testReorderingMergeOfSegmentEndingWithOrphans() throws Exception {
    try (Directory dir = newDirectory()) {
      // Session 1, no reordering: 30 blocks of 2 children + 1 parent, then delete the parent of
      // the last block and merge, which leaves a segment whose last two documents are children.
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setCodec(TestUtil.getDefaultCodec())
                  .setParentField("_parent")
                  .setMergeScheduler(new SerialMergeScheduler())
                  .setMergePolicy(new LogDocMergePolicy()))) {
        for (int b = 0; b < 30; b++) {
          List<Document> block = new ArrayList<>();
          for (int k = 0; k < 3; k++) {
            Document doc = new Document();
            doc.add(new StringField("id", b + "-" + k, Field.Store.YES));
            doc.add(new StringField("body", b % 2 == 0 ? "even" : "odd", Field.Store.NO));
            block.add(doc);
          }
          w.addDocuments(block); // the last document of the block is the parent
        }
        w.deleteDocuments(new Term("id", "29-2"));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(89, r.maxDoc());
      }
      try (CheckIndex checker = new CheckIndex(dir)) {
        assertTrue("CheckIndex accepts the index", checker.checkIndex().clean);
      }

      // Session 2: BPReorderingMergePolicy with a default BPIndexReorderer; any merge of that
      // segment (here a forced merge after deleting one other child) reorders it.
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setCodec(TestUtil.getDefaultCodec())
                  .setParentField("_parent")
                  .setMergeScheduler(new SerialMergeScheduler())
                  .setMergePolicy(
                      new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer())));
      w.deleteDocuments(new Term("id", "0-0"));
      try {
        w.forceMerge(1);
      } finally {
        assertNull("the reordering merge closed the writer", w.getTragicException());
      }
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(88, r.numDocs());
      }
    }
  }
}

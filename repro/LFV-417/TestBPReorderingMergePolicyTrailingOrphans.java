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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A segment whose last documents are children with no parent after them must not make BP reordering
 * merges fail. 20 blocks of 4 children + 1 parent = 100 documents.
 */
public class TestBPReorderingMergePolicyTrailingOrphans extends LuceneTestCase {

  private static IndexWriter newWriter(Directory dir) throws IOException {
    return new IndexWriter(
        dir,
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setParentField("parent")
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(
                new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer())));
  }

  private static List<Document> block(int b) {
    List<Document> docs = new ArrayList<>();
    for (int i = 0; i <= 4; i++) { // 4 children, then the parent
      Document doc = new Document();
      doc.add(new StringField("id", i < 4 ? "b" + b + "c" + i : "b" + b + "p", Field.Store.NO));
      doc.add(new StringField("block", "b" + b, Field.Store.NO));
      docs.add(doc);
    }
    return docs;
  }

  private static IndexWriter indexBlocks(Directory dir) throws IOException {
    IndexWriter w = newWriter(dir);
    for (int b = 0; b < 20; b++) {
      w.addDocuments(block(b));
    }
    return w;
  }

  private static void assertForceMergeWorks(IndexWriter w, int expectedNumDocs) throws IOException {
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      throw new AssertionError("reordering merge failed; writer open: " + w.isOpen(), t);
    }
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(expectedNumDocs, r.numDocs());
    }
    w.close();
  }

  /** No delete at all: IndexWriter rejects a block at its third child, just before a commit. */
  public void testBlockRejectedAtChildBeforeFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = indexBlocks(dir);
      List<Document> rejected = block(20);
      String tooLong = "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1);
      rejected.get(2).add(new StringField("tag", tooLong, Field.Store.NO));
      expectThrows(IllegalArgumentException.class, () -> w.addDocuments(rejected));
      w.commit(); // 103 docs: the 3 indexed docs of the rejected block are deleted, no parent
      TestUtil.checkIndex(dir);
      assertForceMergeWorks(w, 100);
    }
  }

  /** Deleting only the parent of the last block; the first merge drops it. */
  public void testParentOnlyDeleteOfLastBlock() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = indexBlocks(dir);
      w.deleteDocuments(new Term("id", "b19p"));
      w.forceMergeDeletes(); // works: 99 docs, the last 4 are children with no parent after them
      w.commit();
      TestUtil.checkIndex(dir);
      w.deleteDocuments(new Term("id", "b0c0"));
      assertForceMergeWorks(w, 98);
    }
  }

  /** Control: deleting the whole block works. */
  public void testWholeBlockDelete() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = indexBlocks(dir);
      w.deleteDocuments(new Term("block", "b19"));
      w.forceMergeDeletes();
      w.deleteDocuments(new Term("id", "b0c0"));
      assertForceMergeWorks(w, 94);
    }
  }
}

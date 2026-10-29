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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBPReorderAfterRejectedChild extends LuceneTestCase {

  public void testForceMergeAfterBlockRejectedAtChild() throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setParentField("parent")
            .setMergePolicy(
                new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer()))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 32; i++) {
        w.addDocuments(List.of(new Document(), new Document())); // child, parent
      }
      Document badChild = new Document();
      badChild.add(
          new StringField("f", "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1), Field.Store.NO));
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocuments(List.of(badChild, new Document())));
      w.commit();
      try {
        w.forceMerge(1);
      } catch (Throwable t) {
        // Expected: forceMerge succeeds (the bad block was rejected as documented); actual: it throws.
        throw new AssertionError("forceMerge(1) after a block rejected at a child threw " + t, t);
      }
    }
  }
}

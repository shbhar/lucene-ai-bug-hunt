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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAddIndexesNoMergeScheduler extends LuceneTestCase {

  public void testAddIndexesCodecReaderReturns() throws Exception {
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
        sw.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
        IndexWriter w =
            new IndexWriter(
                dst,
                new IndexWriterConfig()
                    .setMergeScheduler(NoMergeScheduler.INSTANCE)
                    .setMergePolicy(NoMergePolicy.INSTANCE));
        Thread t =
            new Thread(
                () -> {
                  try {
                    w.addIndexes(leaf);
                  } catch (Exception returnedByThrowing) {
                  }
                });
        t.start();
        t.join(10_000);
        boolean blocked = t.isAlive();
        w.rollback(); // releases a blocked call with MergeAbortedException
        t.join();
        assertFalse("addIndexes must return or throw, but was still blocked after 10 s", blocked);
      }
    }
  }
}

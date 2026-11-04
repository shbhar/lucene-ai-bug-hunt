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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFailedAddIndexesLeavesQueuedMerges extends LuceneTestCase {

  public void testFailedCallDoesNotBreakNextCall() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    boolean[] armed = {true}; // one "disk full" in the first addIndexes merge, then none
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(IndexWriter.class, "addIndexesReaderMerge")
                && callStackContains(MockDirectoryWrapper.class, "createOutput")) {
              armed[0] = false;
              throw new IOException("simulated disk full");
            }
          }
        });
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(
                new TieredMergePolicy() {
                  @Override
                  public MergeSpecification findMerges(CodecReader... readers) {
                    MergeSpecification spec = new MergeSpecification();
                    for (CodecReader reader : readers) {
                      spec.add(new OneMerge(reader)); // one merge per reader
                    }
                    return spec;
                  }
                });
    try (Directory sourceA = sourceIndex("a0", "a1"); // 2 segments -> 2 merges
        Directory sourceB = sourceIndex("b0"); // 1 segment -> 1 merge
        IndexWriter w = new IndexWriter(dir, iwc)) {

      // Call 1 fails on its first merge and adds nothing; its readers are then closed.
      try (DirectoryReader readerA = DirectoryReader.open(sourceA)) {
        expectThrows(IOException.class, () -> w.addIndexes(leaves(readerA)));
      }
      assertTrue(w.isOpen());
      assertNull(w.getTragicException());

      // Call 2 has valid input and no fault. Expected: it adds b0.
      // Actual: AlreadyClosedException "this IndexReader is closed" (call 1's leftover merge).
      try (DirectoryReader readerB = DirectoryReader.open(sourceB)) {
        w.addIndexes(leaves(readerB));
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.numDocs());
      }
    } finally {
      dir.close();
    }
  }

  private static CodecReader[] leaves(DirectoryReader reader) {
    return reader.leaves().stream().map(c -> (CodecReader) c.reader()).toArray(CodecReader[]::new);
  }

  private static Directory sourceIndex(String... ids) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : ids) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        w.addDocument(doc);
        w.flush(); // one segment per document
      }
    }
    return dir;
  }
}

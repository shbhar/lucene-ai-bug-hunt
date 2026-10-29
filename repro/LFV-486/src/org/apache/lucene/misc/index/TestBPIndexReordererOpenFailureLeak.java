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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBPIndexReordererOpenFailureLeak extends LuceneTestCase {

  public void testFailedStartOffsetsOpenClosesTermIdsInput() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      MockDirectoryWrapper tempDir =
          new MockDirectoryWrapper(random(), new ByteBuffersDirectory()) {
            @Override
            public synchronized IndexInput openInput(String name, IOContext context)
                throws IOException {
              if (name.startsWith("start-offsets")) {
                throw new IOException("simulated failure opening " + name);
              }
              return super.openInput(name, context);
            }
          };
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader leaf = (CodecReader) getOnlyLeafReader(reader);
        expectThrows(
            IOException.class, () -> new BPIndexReorderer().computeDocMap(leaf, tempDir, null));
      }
      // expected: 0 temp inputs left open after the failed computeDocMap; actual: 1 (term-ids)
      assertEquals(0, tempDir.getFileHandleCount());
      tempDir.close();
    }
  }
}

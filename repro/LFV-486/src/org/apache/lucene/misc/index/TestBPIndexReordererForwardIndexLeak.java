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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BPIndexReorderer#buildForwardIndex opens the "term-ids" temp file and then the "start-offsets"
 * temp file outside any try/finally. If the second openInput (or the ForwardIndex constructor)
 * throws, the term-ids IndexInput is never closed: computePermutation's failure handler only
 * deletes the temp files. The leaked input survives the failed reorder, the failed merge and
 * IndexWriter#close.
 */
public class TestBPIndexReordererForwardIndexLeak extends LuceneTestCase {

  /** Fails openInput of the reorderer's "start-offsets" temp file once, like an EMFILE error. */
  static final class FailStartOffsetsOpen extends FilterDirectory {
    final AtomicBoolean failed = new AtomicBoolean();

    FailStartOffsetsOpen(Directory in) {
      super(in);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      if (name.startsWith("start-offsets") && failed.compareAndSet(false, true)) {
        throw new IOException("simulated failure opening " + name);
      }
      return super.openInput(name, context);
    }
  }

  private static void addDocs(IndexWriter w, int from, int to) throws IOException {
    for (int i = from; i < to; ++i) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Store.YES));
      doc.add(new StringField("body", Integer.toString(i % 2 == 0 ? 0 : i % 10), Store.NO));
      w.addDocument(doc);
    }
  }

  private static BPIndexReorderer reorderer() {
    BPIndexReorderer r = new BPIndexReorderer();
    r.setMinDocFreq(2);
    r.setMinPartitionSize(2);
    return r;
  }

  /** Direct API: computeDocMap fails, and must not leave an input open in tempDir. */
  public void testComputeDocMapFailureClosesInputs() throws IOException {
    try (Directory indexDir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(indexDir, new IndexWriterConfig())) {
        addDocs(w, 0, 200);
        w.forceMerge(1);
      }
      MockDirectoryWrapper tempMock = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
      FailStartOffsetsOpen tempDir = new FailStartOffsetsOpen(tempMock);
      try (DirectoryReader r = DirectoryReader.open(indexDir)) {
        CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
        IOException e =
            expectThrows(IOException.class, () -> reorderer().computeDocMap(leaf, tempDir, null));
        assertTrue(e.getMessage(), e.getMessage().startsWith("simulated failure opening start-offsets"));
      }
      // The reorder failed; every temp input it opened must be closed by now.
      List<String> problems = new ArrayList<>();
      try {
        tempMock.close();
      } catch (RuntimeException ex) {
        problems.add(ex.getMessage());
      }
      assertEquals("temp directory after the failed computeDocMap", List.of(), problems);
    }
  }

  /** End to end: a reordering forced merge hits the failure; nothing may stay open afterwards. */
  public void testReorderingMergeFailureClosesInputs() throws IOException {
    MockDirectoryWrapper mock = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    mock.setAssertNoUnrefencedFilesOnClose(false);
    FailStartOffsetsOpen dir = new FailStartOffsetsOpen(mock);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer()))
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(64);
    IndexWriter w = new IndexWriter(dir, iwc);
    addDocs(w, 0, 100);
    w.flush();
    addDocs(w, 100, 200);
    w.flush();
    expectThrows(IOException.class, () -> w.forceMerge(1));
    w.close();
    List<String> problems = new ArrayList<>();
    try {
      mock.close();
    } catch (RuntimeException ex) {
      problems.add(ex.getMessage());
    }
    assertEquals("directory after the failed reordering merge and IndexWriter#close", List.of(), problems);
  }
}

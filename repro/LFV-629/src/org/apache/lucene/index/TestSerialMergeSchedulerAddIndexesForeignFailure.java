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
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSerialMergeSchedulerAddIndexesForeignFailure extends LuceneTestCase {

  private static Directory oneDocIndex() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StoredField("f", "v"));
      w.addDocument(doc);
    }
    return dir;
  }

  private static void flipLastStoredFieldsDataByte(Directory dir) throws IOException {
    String fdt = Arrays.stream(dir.listAll()).filter(n -> n.endsWith(".fdt")).findFirst().get();
    byte[] bytes;
    try (IndexInput in = dir.openInput(fdt, IOContext.READONCE)) {
      bytes = new byte[(int) in.length()];
      in.readBytes(bytes, 0, bytes.length);
    }
    bytes[bytes.length - CodecUtil.footerLength() - 1] ^= 1;
    dir.deleteFile(fdt);
    try (IndexOutput out = dir.createOutput(fdt, IOContext.DEFAULT)) {
      out.writeBytes(bytes, bytes.length);
    }
  }

  public void testValidCallDoesNotFailWithConcurrentCallsMergeException() throws Exception {
    try (Directory srcA = oneDocIndex();
        Directory srcB = oneDocIndex();
        Directory dir = new ByteBuffersDirectory()) {
      flipLastStoredFieldsDataByte(srcA);
      try (DirectoryReader readerA = DirectoryReader.open(srcA);
          DirectoryReader readerB = DirectoryReader.open(srcB)) {
        CodecReader corruptA = (CodecReader) readerA.leaves().get(0).reader();
        CodecReader validB = (CodecReader) readerB.leaves().get(0).reader();

        IndexWriter[] writer = new IndexWriter[1];
        Thread callA = new Thread(new FutureTask<>(() -> writer[0].addIndexes(corruptA)));
        Semaphore aQueuedItsMerge = new Semaphore(0);
        SerialMergeScheduler scheduler =
            new SerialMergeScheduler() {
              @Override
              public void merge(MergeSource source, MergeTrigger trigger) throws IOException {
                if (Thread.currentThread() == callA) {
                  aQueuedItsMerge.release();
                }
                super.merge(source, trigger);
              }

              @Override
              public Directory wrapForMerge(MergePolicy.OneMerge merge, Directory in) {
                if (callA.getState() == Thread.State.NEW) {
                  callA.start();
                  aQueuedItsMerge.acquireUninterruptibly();
                }
                return super.wrapForMerge(merge, in);
              }
            };

        IndexWriterConfig iwc =
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setMergeScheduler(scheduler);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          writer[0] = w;
          IOException failureOfB = null;
          try {
            w.addIndexes(validB);
          } catch (IOException e) {
            failureOfB = e;
          }
          callA.join();
          if (failureOfB != null) {
            // expected: B adds its document; actual: B runs A's queued merge, throws A's exception
            throw new AssertionError("expected B to succeed, it threw " + failureOfB, failureOfB);
          }
        }
      }
    }
  }
}

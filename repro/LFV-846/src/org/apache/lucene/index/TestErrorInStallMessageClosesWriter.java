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
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;

public class TestErrorInStallMessageClosesWriter extends LuceneTestCase {

  public void testOutOfMemoryErrorWhileLoggingStallClosesWriter() throws Exception {
    OutOfMemoryError oome = new OutOfMemoryError("while logging 'now stalling flushes'");
    InfoStream infoStream =
        new InfoStream() {
          boolean thrown;

          @Override
          public void message(String component, String message) {
            if (thrown == false) {
              thrown = true;
              throw oome;
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return component.equals("DWFC");
          }

          @Override
          public void close() {}
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setRAMBufferSizeMB(0.001)
            .setInfoStream(infoStream);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc);
      Document doc = new Document();
      doc.add(new TextField("f", "a", Field.Store.NO));
      Thread indexer =
          new Thread(
              () -> {
                try {
                  w.addDocument(doc);
                } catch (Throwable ignored) {
                }
              });
      indexer.start();
      indexer.join(10_000);
      // expected: the Error closes the writer and addDocument returns; actual: addDocument hangs
      assertFalse("addDocument should rethrow the Error, but is still blocked", indexer.isAlive());
      assertSame(oome, w.getTragicException());
      assertFalse(w.isOpen());
    }
  }
}

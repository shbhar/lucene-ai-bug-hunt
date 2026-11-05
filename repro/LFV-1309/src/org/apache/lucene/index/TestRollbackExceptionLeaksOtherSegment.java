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

import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestRollbackExceptionLeaksOtherSegment extends LuceneTestCase {

  public void testExceptionWhileAbortingOneSegmentDoesNotLeakTheOther() throws Exception {
    AtomicBoolean armed = new AtomicBoolean();
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.equals("done abort") && armed.getAndSet(false)) {
              throw new RuntimeException("BOOM");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return component.equals("DWPT");
          }

          @Override
          public void close() {}
        };
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(infoStream));

    Semaphore inside = new Semaphore(0);
    Semaphore release = new Semaphore(0);
    Document blocked = new Document();
    blocked.add(
        new TextField(
            "f",
            new TokenStream() {
              @Override
              public boolean incrementToken() {
                inside.release();
                release.acquireUninterruptibly();
                return false;
              }
            }));
    FutureTask<Long> other = new FutureTask<>(() -> w.addDocument(blocked));
    new Thread(other).start();
    inside.acquireUninterruptibly();
    w.addDocument(new Document());
    release.release();
    other.get();

    armed.set(true);
    assertEquals("BOOM", expectThrows(RuntimeException.class, w::rollback).getMessage());
    // expected: rollback() still releases every buffered segment; actual: "cannot close: there are still 4 open files"
    dir.close();
  }
}

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
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.BufferedIndexInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TestFieldsReader#testExceptions (LUCENE-1262) with the returned documents checked: the same
 * BufferedIndexInput-based directory, but the simulated network outage hits exactly one
 * readInternal call (the n-th after reading starts, for every n); StoredFields.document(i) is
 * retried after an IOException, as in that test. A call that returns must return document i.
 */
public class TestStoredFieldsRetryAfterReadFailure extends LuceneTestCase {

  private static final class FaultyDirectory extends FilterDirectory {
    final AtomicBoolean doFail = new AtomicBoolean();
    final AtomicInteger calls = new AtomicInteger();
    volatile int failAt = -1;

    FaultyDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      return new FaultyIndexInput(this, in.openInput(name, context));
    }
  }

  /** As in TestFieldsReader, with a one-shot failure. */
  private static final class FaultyIndexInput extends BufferedIndexInput {
    private final FaultyDirectory dir;
    final IndexInput delegate;

    FaultyIndexInput(FaultyDirectory dir, IndexInput delegate) {
      super("FaultyIndexInput(" + delegate + ")", BufferedIndexInput.BUFFER_SIZE);
      this.delegate = delegate;
      this.dir = dir;
    }

    private void simOutage() throws IOException {
      if (dir.doFail.get() && dir.calls.incrementAndGet() == dir.failAt) {
        throw new IOException("Simulated network outage");
      }
    }

    @Override
    public void readInternal(ByteBuffer b) throws IOException {
      simOutage();
      delegate.seek(getFilePointer());
      delegate.readBytes(b.array(), b.position(), b.remaining());
      b.position(b.limit());
    }

    @Override
    public void seekInternal(long pos) throws IOException {}

    @Override
    public long length() {
      return delegate.length();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public FaultyIndexInput clone() {
      FaultyIndexInput i = new FaultyIndexInput(dir, delegate.clone());
      try {
        i.seek(getFilePointer());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return i;
    }

    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
      return new FaultyIndexInput(dir, delegate.slice(sliceDescription, offset, length));
    }
  }

  private static String value(int doc, int len) {
    Random r = new Random(doc * 31L + 7);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      sb.append((char) ('a' + r.nextInt(26)));
    }
    return sb.toString();
  }

  public void testRetriedDocumentIsTheRightDocument() throws Exception {
    doTest(20, 3000); // values longer than the 1024-byte read buffer
  }

  public void testRetriedSmallDocuments() throws Exception {
    doTest(400, 40); // many documents per compressed block
  }

  private void doTest(int numDocs, int len) throws Exception {
    try (Directory fs = newFSDirectory(createTempDir())) {
      FaultyDirectory dir = new FaultyDirectory(fs);
      try (IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new StoredField("id", i));
          doc.add(new StoredField("body", value(i, len)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      int failures = 0;
      int returned = 0;
      for (int failAt = 1; failAt <= 80; failAt++) {
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          StoredFields storedFields = reader.storedFields();
          dir.calls.set(0);
          dir.failAt = failAt;
          dir.doFail.set(true);
          Random order = new Random(failAt);
          for (int k = 0; k < numDocs; k++) {
            int i = order.nextInt(numDocs);
            for (int attempt = 0; attempt < 2; attempt++) {
              Document d;
              try {
                d = storedFields.document(i);
              } catch (IOException | RuntimeException e) {
                // DirectReader (stored fields index) rethrows the IOException unchecked
                Throwable t = e instanceof IOException ? e : e.getCause();
                if (t == null || !"Simulated network outage".equals(t.getMessage())) {
                  // anything but the one simulated failure is a consequence of it
                  throw new AssertionError(
                      "failAt=" + failAt + " document " + i + " attempt " + attempt
                          + ": unexpected " + e, e);
                }
                failures++;
                continue; // retry, as TestFieldsReader#testExceptions does
              }
              returned++;
              String where = "failAt=" + failAt + " document " + i + " attempt " + attempt;
              assertEquals(where + ": wrong id", i, d.getField("id").numericValue().intValue());
              assertEquals(where + ": wrong body", value(i, len), d.get("body"));
            }
          }
          dir.doFail.set(false);
        }
      }
      assertTrue("no simulated failure was hit", failures > 0);
      assertTrue("no document was returned", returned > 0);
    }
  }
}

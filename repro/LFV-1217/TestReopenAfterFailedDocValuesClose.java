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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.IOUtils;

public class TestReopenAfterFailedDocValuesClose extends LuceneTestCase {

  public void testReopenAfterFailedDocValuesClose() throws Exception {
    AtomicBoolean failNextClose = new AtomicBoolean();
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            return new FilterIndexInput(name, in.openInput(name, context)) {
              @Override
              public void close() throws IOException {
                in.close();
                if (failNextClose.getAndSet(false)) {
                  throw new IOException("simulated close failure");
                }
              }

              @Override
              public IndexInput clone() {
                return in.clone();
              }
            };
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 2; i++) {
        w.addDocument(
            List.of(
                new StringField("id", Integer.toString(i), Field.Store.NO),
                new NumericDocValuesField("n", i)));
      }
      w.commit();
      w.updateNumericDocValue(new Term("id", "0"), "n", 10);
      w.commit();
    }
    List<IndexCommit> commits = DirectoryReader.listCommits(dir);
    DirectoryReader r1 = DirectoryReader.open(commits.get(1));
    DirectoryReader r2 = DirectoryReader.openIfChanged(r1, commits.get(0));
    failNextClose.set(true);
    expectThrows(IOException.class, r1::close);
    DirectoryReader r3 = DirectoryReader.openIfChanged(r2, commits.get(1));
    // expected: doc 0 reads n=10, as a fresh open of commit 1 does; actual: AlreadyClosedException
    NumericDocValues n = r3.leaves().get(0).reader().getNumericDocValues("n");
    assertTrue(n.advanceExact(0));
    assertEquals(10, n.longValue());
    IOUtils.close(r3, r2, dir);
  }
}

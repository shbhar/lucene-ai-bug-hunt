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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestFailedDeleteFreezeKeepsError extends LuceneTestCase {

  public void testErrorWhileFreezingBufferedDeletes() throws Exception {
    OutOfMemoryError oome = new OutOfMemoryError("simulated, while freezing buffered deletes");
    AtomicBoolean armed = new AtomicBoolean();
    InfoStream throwOnceInFreeze =
        new InfoStream() {
          @Override
          public boolean isEnabled(String component) {
            return "BD".equals(component);
          }

          @Override
          public void message(String component, String message) {
            // logged inside FrozenBufferedUpdates' constructor, before the buffer is cleared
            if (message.startsWith("compressed") && armed.compareAndSet(true, false)) {
              throw oome;
            }
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setInfoStream(throwOnceInFreeze));
      w.addDocument(doc("1", 1));
      w.addDocument(doc("2", 2));
      w.commit();
      w.deleteDocuments(new Term("id", "1"));
      w.updateNumericDocValue(new Term("id", "2"), "dv", 22);

      armed.set(true); // no documents are buffered, so the NRT open freezes only these two changes
      Throwable thrown = expectThrows(Throwable.class, () -> DirectoryReader.open(w).close());
      boolean open = w.isOpen();
      String state = "caller got " + thrown + "; writer open=" + open;
      if (open) {
        state += "; next commit() returned " + w.commit() + "; committed: " + committed(dir);
        w.close();
      }
      assertTrue(state, thrown == oome && open == false);
    }
  }

  private static Document doc(String id, long dv) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    d.add(new NumericDocValuesField("dv", dv));
    return d;
  }

  private static String committed(Directory dir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      int id2 = s.search(new TermQuery(new Term("id", "2")), 1).scoreDocs[0].doc;
      NumericDocValues dv = MultiDocValues.getNumericValues(r, "dv");
      dv.advanceExact(id2);
      return "id:1 hits="
          + s.count(new TermQuery(new Term("id", "1")))
          + ", dv(id:2)="
          + dv.longValue();
    }
  }
}

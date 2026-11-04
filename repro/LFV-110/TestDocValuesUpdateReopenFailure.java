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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDocValuesUpdateReopenFailure extends LuceneTestCase {

  /**
   * An IOException while IndexWriter re-opens its pooled segment reader after writing a doc-values
   * update is not tragic: the writer stays open. The acknowledged update must survive.
   */
  public void testUpdateSurvivesFailedReaderReopen() throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    boolean[] armed = {false};
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(ReadersAndUpdates.class, "swapNewReaderWithLatestLiveDocs")) {
              armed[0] = false; // fail once
              throw new IOException("simulated I/O error while re-opening the segment reader");
            }
          }
        });
    IndexWriterConfig iwc = new IndexWriterConfig(null);
    iwc.setCodec(TestUtil.getDefaultCodec());
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 4; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new NumericDocValuesField("n", i));
      w.addDocument(doc);
    }
    w.commit();
    DirectoryReader.open(w).close(); // the writer now pools the segment's reader

    w.updateNumericDocValue(new Term("id", "0"), "n", 100);
    armed[0] = true;
    expectThrows(IOException.class, () -> DirectoryReader.open(w)); // the injected fault
    assertTrue("an IOException in getReader is not tragic", w.isOpen());

    w.updateNumericDocValue(new Term("id", "1"), "n", 200);
    DirectoryReader.open(w).close(); // refresh
    w.commit();
    w.close();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("last commit", "100 200 2 3", values(r));
    }
    dir.close(); // runs CheckIndex
  }

  private static String values(DirectoryReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : reader.leaves()) {
      NumericDocValues dv = ctx.reader().getNumericDocValues("n");
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        sb.append(dv.advanceExact(doc) ? Long.toString(dv.longValue()) : "-").append(' ');
      }
    }
    return sb.toString().trim();
  }
}

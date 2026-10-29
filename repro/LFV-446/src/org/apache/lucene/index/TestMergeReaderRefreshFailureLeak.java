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

public class TestMergeReaderRefreshFailureLeak extends LuceneTestCase {

  public void testFailedMergeReaderRefreshLeavesFilesOpen() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (callStackContainsAnyOf("getReaderForMerge")) {
              throw new IOException("injected");
            }
          }
        });
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setMergeScheduler(new SerialMergeScheduler()));
    for (String id : new String[] {"a", "b"}) {
      Document doc = new Document();
      doc.add(new StringField("id", id, Field.Store.NO));
      doc.add(new NumericDocValuesField("n", 1));
      w.addDocument(doc);
    }
    w.updateNumericDocValue(new Term("id", "a"), "n", 2);
    DirectoryReader.open(w).close();
    w.deleteDocuments(new Term("id", "b"));
    expectThrows(IOException.class, () -> w.forceMerge(1));
    w.close();
    try {
      dir.close();
    } catch (RuntimeException e) {
      fail("expected no open files after IndexWriter.close(), but " + e.getMessage());
    }
  }
}

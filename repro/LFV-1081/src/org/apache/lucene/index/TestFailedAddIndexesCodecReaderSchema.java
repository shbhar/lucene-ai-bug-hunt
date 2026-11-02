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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFailedAddIndexesCodecReaderSchema extends LuceneTestCase {

  public void testFailedAddIndexesDoesNotDefineIncomingField() throws Exception {
    try (Directory src = newDirectory();
        MockDirectoryWrapper dst = newMockDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "x", Field.Store.NO));
        w.addDocument(doc);
      }
      MockDirectoryWrapper.Failure diskFull =
          new MockDirectoryWrapper.Failure() {
            @Override
            public void eval(MockDirectoryWrapper dir) throws IOException {
              if (doFail) {
                throw new IOException("simulated disk full");
              }
            }
          };
      dst.failOn(diskFull);
      IndexWriterConfig iwc = new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler());
      try (DirectoryReader reader = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, iwc)) {
        diskFull.setDoFail();
        expectThrows(IOException.class, () -> w.addIndexes((CodecReader) reader.leaves().get(0).reader()));
        diskFull.clearDoFail();
        // expected: "f" is unknown after the failed call, so this is accepted; actual: IllegalArgumentException
        Document doc = new Document();
        doc.add(new NumericDocValuesField("f", 1));
        w.addDocument(doc);
      }
    }
  }
}

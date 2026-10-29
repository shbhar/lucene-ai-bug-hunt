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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSortedFlushTempReaderLeak extends LuceneTestCase {

  public void testFailedSortedStoredFieldsWriterLeavesTempReaderOpen() throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    FilterDirectory failing =
        new FilterDirectory(dir) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (name.endsWith(".fdm")) {
              throw new IOException("simulated failure creating " + name);
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
    IndexWriter w = new IndexWriter(failing, iwc);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("sort", 1));
    doc.add(new StoredField("stored", "value"));
    w.addDocument(doc);
    expectThrows(IOException.class, w::commit);
    w.close();
    // expected: no open files after the failed flush; actual: the temporary .fdt/.fdx stay open
    assertEquals("open file handles after IndexWriter closed", 0, dir.getFileHandleCount());
    dir.close();
  }
}

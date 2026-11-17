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
package org.apache.lucene.codecs.simpletext;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleTextFailedOpenClosesInput extends LuceneTestCase {

  // expected: the failed open closes _0.fld; actual: dir.close() throws "1 open files: {_0.fld=1}"
  public void testFailedOpenClosesStoredFieldsInput() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setCheckIndexOnClose(false);
    IndexWriterConfig config =
        new IndexWriterConfig().setCodec(new SimpleTextCodec()).setUseCompoundFile(false);
    try (IndexWriter writer = new IndexWriter(dir, config)) {
      writer.addDocument(new Document());
    }
    dir.deleteFile("_0.fld");
    dir.createOutput("_0.fld", IOContext.DEFAULT).close();
    expectThrows(CorruptIndexException.class, () -> DirectoryReader.open(dir));
    dir.close();
  }
}

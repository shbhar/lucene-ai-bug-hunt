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
package org.apache.lucene.codecs.lucene90.compressing;

import java.io.IOException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFieldsIndexReaderCorruptMeta extends LuceneTestCase {

  public void testCorruptMetaClosesIndexInput() throws IOException {
    ByteBuffersDirectory raw = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
    try (IndexWriter w = new IndexWriter(raw, iwc)) {
      Document doc = new Document();
      doc.add(new StoredField("f", "v"));
      w.addDocument(doc);
    }

    byte[] fdm;
    try (IndexInput in = raw.openInput("_0.fdm", IOContext.READONCE)) {
      fdm = new byte[(int) in.length()];
      in.readBytes(fdm, 0, fdm.length);
    }
    ByteArrayDataInput meta = new ByteArrayDataInput(fdm);
    meta.skipBytes(CodecUtil.indexHeaderLength("Lucene90FieldsIndexMeta", ""));
    meta.readVInt();
    meta.skipBytes(3 * Integer.BYTES);
    int docsStartPointer = meta.getPosition();
    fdm[docsStartPointer + 7] ^= 0x40;
    raw.deleteFile("_0.fdm");
    try (IndexOutput out = raw.createOutput("_0.fdm", IOContext.DEFAULT)) {
      out.writeBytes(fdm, fdm.length);
    }

    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), raw);
    dir.setCheckIndexOnClose(false);
    expectThrows(CorruptIndexException.class, () -> DirectoryReader.open(dir));
    // Expected: close() succeeds; actual: it fails because the failed open left _0.fdx open.
    dir.close();
  }
}

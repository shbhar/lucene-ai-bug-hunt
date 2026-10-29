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
package org.apache.lucene.codecs.lucene94;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene94FieldInfosFormatJavadoc extends LuceneTestCase {

  public void testDocumentedFieldSequence() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      try (IndexInput in = dir.openInput("_0.fnm", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(in, "Lucene94FieldInfos", 0, Integer.MAX_VALUE, id, "");
        assertEquals("FieldsCount", 1, in.readVInt());
        assertEquals("FieldName", "f", in.readString());
        assertEquals("FieldNumber", 0, in.readVInt());
        assertEquals("FieldBits", 0, in.readByte());
        // expected 0 (no doc values); actual 3: the IndexOptions byte the documented sequence omits
        assertEquals("DocValuesBits, DocValues nibble", 0, in.readByte() & 0x0F);
      }
    }
  }
}

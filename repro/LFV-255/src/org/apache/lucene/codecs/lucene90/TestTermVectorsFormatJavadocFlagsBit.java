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
package org.apache.lucene.codecs.lucene90;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
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
import org.apache.lucene.util.packed.PackedInts;

public class TestTermVectorsFormatJavadocFlagsBit extends LuceneTestCase {

  public void testSameOptionsChunkHasFlagsBitOne() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
        ft.setStoreTermVectors(true);
        Document doc = new Document();
        doc.add(new Field("f", "x", ft));
        w.addDocument(doc);
      }
      String segment = SegmentInfos.readLatestCommit(dir).info(0).info.name;
      try (IndexInput in = dir.openInput(segment + ".tvd", IOContext.READONCE)) {
        in.seek(CodecUtil.indexHeaderLength("Lucene90TermVectorsData", ""));
        in.readVInt();
        in.readVInt();
        in.readVInt();
        int token = in.readByte() & 0xFF;
        in.skipBytes(
            PackedInts.Format.PACKED.byteCount(
                PackedInts.VERSION_CURRENT, (token >>> 5) + 1, token & 0x1F));
        in.skipBytes(in.readVLong());
        // javadoc: Flags Bit is 1 when all fields have the same options in the chunk; written: 0
        assertEquals("Flags Bit of a one-document, one-field chunk", 1, in.readVInt());
      }
    }
  }
}

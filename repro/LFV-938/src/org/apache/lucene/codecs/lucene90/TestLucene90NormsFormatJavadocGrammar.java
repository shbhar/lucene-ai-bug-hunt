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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The class javadoc of {@link Lucene90NormsFormat} documents the norms metadata file as
 *
 * <pre>
 * Norms metadata (.dvm) --&gt; Header,&lt;Entry&gt;<sup>NumFields</sup>,Footer
 * Entry --&gt; FieldNumber, DocsWithFieldAddress, DocsWithFieldLength, NumDocsWithField,
 *   BytesPerNorm, NormsAddress
 * </pre>
 *
 * with FieldNumber and NumDocsWithField as Int32, the addresses and the length as Int64 and
 * BytesPerNorm as a byte; a FieldNumber of -1 ends the metadata. This test parses the .nvm file
 * written by the default codec with that grammar. Only public API is used.
 */
public class TestLucene90NormsFormatJavadocGrammar extends LuceneTestCase {

  public void testDocumentedEntryParsesWrittenFile() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setUseCompoundFile(false)
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", "some text " + i, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      int docsWithNorms = 0;
      int fieldNumber;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = r.leaves().get(0).reader();
        FieldInfo fi = leaf.getFieldInfos().fieldInfo("body");
        fieldNumber = fi.number;
        NumericDocValues norms = leaf.getNormValues("body");
        while (norms.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          docsWithNorms++;
        }
      }
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      try (IndexInput in = dir.openInput("_0.nvm", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(in, "Lucene90NormsMetadata", 0, Integer.MAX_VALUE, id, "");
        assertEquals("FieldNumber", fieldNumber, in.readInt());
        long docsWithFieldAddress = in.readLong();
        assertEquals("DocsWithFieldAddress (-1: all documents have a norm)", -1, docsWithFieldAddress);
        in.readLong(); // DocsWithFieldLength
        assertEquals("NumDocsWithField", docsWithNorms, in.readInt());
        byte bytesPerNorm = in.readByte();
        assertTrue("BytesPerNorm " + bytesPerNorm, bytesPerNorm >= 0 && bytesPerNorm <= 8);
        in.readLong(); // NormsAddress
        assertEquals("FieldNumber of -1 ends the metadata", -1, in.readInt());
        assertEquals(in.length() - CodecUtil.footerLength(), in.getFilePointer());
      }
    }
  }
}

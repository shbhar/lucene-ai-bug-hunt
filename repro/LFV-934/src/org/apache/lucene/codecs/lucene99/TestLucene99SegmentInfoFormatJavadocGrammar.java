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
package org.apache.lucene.codecs.lucene99;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The class javadoc of {@link Lucene99SegmentInfoFormat} documents the .si file as
 *
 * <pre>
 * .si: Header, SegVersion, SegSize, IsCompoundFile, Diagnostics, Files, Attributes, IndexSort, Footer
 * </pre>
 *
 * with SegVersion and SegMinVersion as {@code DataOutput#writeString} String, SegSize as Int32 and
 * IsCompoundFile and HasBlocks as Int8 (SegMinVersion and HasBlocks are typed but not placed in the
 * sequence). These tests parse a .si file written by the default codec with that grammar. Only
 * public API is used.
 */
public class TestLucene99SegmentInfoFormatJavadocGrammar extends LuceneTestCase {

  private static Directory index() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
    }
    return dir;
  }

  private void parse(boolean withTypedElements) throws Exception {
    try (Directory dir = index()) {
      SegmentInfo si = SegmentInfos.readLatestCommit(dir).info(0).info;
      try (IndexInput in = dir.openInput(si.name + ".si", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(in, "Lucene90SegmentInfo", 0, Integer.MAX_VALUE, si.getId(), "");
        assertEquals("SegVersion", si.getVersion().toString(), in.readString());
        if (withTypedElements) {
          assertEquals("SegMinVersion", si.getMinVersion().toString(), in.readString());
        }
        assertEquals("SegSize", si.maxDoc(), in.readInt());
        assertEquals("IsCompoundFile", si.getUseCompoundFile() ? 1 : -1, in.readByte());
        if (withTypedElements) {
          assertEquals("HasBlocks", si.getHasBlocks() ? 1 : -1, in.readByte());
        }
        assertEquals("Diagnostics", si.getDiagnostics(), in.readMapOfStrings());
        assertEquals("Files", si.files(), in.readSetOfStrings());
        assertEquals("Attributes", si.getAttributes(), in.readMapOfStrings());
        assertEquals("IndexSort count", 0, in.readVInt());
        assertEquals(in.length() - CodecUtil.footerLength(), in.getFilePointer());
      }
    }
  }

  /** The literal sequence of the javadoc. */
  public void testDocumentedSequenceParsesWrittenFile() throws Exception {
    parse(false);
  }

  /**
   * A generous reading that also places the two elements the javadoc only types (SegMinVersion
   * after SegVersion, HasBlocks after IsCompoundFile).
   */
  public void testDocumentedSequenceWithTypedElementsParsesWrittenFile() throws Exception {
    parse(true);
  }
}

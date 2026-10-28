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
package org.apache.lucene.codecs.lucene103.blocktree;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * The class javadoc of {@link Lucene103BlockTreeTermsWriter} documents
 *
 * <pre>
 * TermsDict (.tim) --&gt; Header, FieldDict<sup>NumFields</sup>, Footer
 * FieldDict --&gt; PostingsHeader, NodeBlock<sup>NumBlocks</sup>
 * TermsMeta (.tmd) --&gt; Header, NumFields, &lt;FieldStats&gt;<sup>NumFields</sup>,
 *   TermIndexLength, TermDictLength, Footer
 * FieldStats --&gt; FieldNumber, NumTerms, RootCodeLength, Byte<sup>RootCodeLength</sup>,
 *   SumTotalTermFreq?, SumDocFreq, DocCount, MinTerm, MaxTerm, IndexStartFP, TrieRootNodeFp,
 *   IndexEndFp
 * </pre>
 *
 * and the postings format documents PostingsHeader --&gt; Header, PackedBlockSize with Header an
 * IndexHeader. These tests parse the files written by the default codec with that grammar. Only
 * public API is used.
 */
public class TestLucene103BlockTreeJavadocGrammar extends LuceneTestCase {

  private static final String FIELD = "body";

  private static Directory index() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String text : new String[] {"apple banana", "banana cherry", "cherry date apple"}) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static String file(Directory dir, String ext) throws Exception {
    for (String f : dir.listAll()) {
      if (ext.equals(IndexFileNames.getExtension(f))) {
        return f;
      }
    }
    throw new AssertionError("no ." + ext + " file");
  }

  private static String suffix(String file) {
    String s = IndexFileNames.stripExtension(file);
    return s.substring(s.indexOf('_', 1) + 1);
  }

  /** Skips an IndexHeader whatever its codec name (magic, name, version, id, suffix). */
  private static void skipIndexHeader(IndexInput in) throws Exception {
    assertEquals("codec magic", CodecUtil.CODEC_MAGIC, CodecUtil.readBEInt(in));
    in.readString(); // codec name
    CodecUtil.readBEInt(in); // version
    in.skipBytes(16); // id
    in.skipBytes(in.readByte() & 0xFF); // suffix
  }

  /** .tim: each FieldDict starts with the PostingsHeader, an IndexHeader. */
  public void testTermsDictFieldStartsWithPostingsHeader() throws Exception {
    try (Directory dir = index()) {
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String tim = file(dir, "tim");
      try (IndexInput in = dir.openInput(tim, IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(in, "BlockTreeTermsDict", 0, Integer.MAX_VALUE, id, suffix(tim));
        assertEquals(
            "PostingsHeader (an IndexHeader starts with the codec magic)",
            CodecUtil.CODEC_MAGIC,
            CodecUtil.readBEInt(in));
      }
    }
  }

  /**
   * .tmd: Header (read as an IndexHeader, which is what CodecUtil writes for every file of this
   * format), then NumFields and the FieldStats of the only field.
   */
  public void testTermsMetaDocumentedSequence() throws Exception {
    try (Directory dir = index()) {
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String tmd = file(dir, "tmd");
      try (DirectoryReader r = DirectoryReader.open(dir);
          IndexInput in = dir.openInput(tmd, IOContext.READONCE)) {
        LeafReader leaf = r.leaves().get(0).reader();
        FieldInfo fi = leaf.getFieldInfos().fieldInfo(FIELD);
        Terms terms = leaf.terms(FIELD);
        CodecUtil.checkIndexHeader(in, "BlockTreeTermsMeta", 0, Integer.MAX_VALUE, id, suffix(tmd));
        assertEquals("NumFields", 1, in.readVInt());
        assertEquals("FieldNumber", fi.number, in.readVInt());
        assertEquals("NumTerms", terms.size(), in.readVLong());
      }
    }
  }

  /**
   * .tmd FieldStats, with the undocumented postings header after the file header skipped: the
   * documented RootCodeLength and RootCode precede SumTotalTermFreq.
   */
  public void testFieldStatsDocumentedSequence() throws Exception {
    try (Directory dir = index()) {
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String tmd = file(dir, "tmd");
      try (DirectoryReader r = DirectoryReader.open(dir);
          IndexInput in = dir.openInput(tmd, IOContext.READONCE)) {
        LeafReader leaf = r.leaves().get(0).reader();
        FieldInfo fi = leaf.getFieldInfos().fieldInfo(FIELD);
        Terms terms = leaf.terms(FIELD);
        CodecUtil.checkIndexHeader(in, "BlockTreeTermsMeta", 0, Integer.MAX_VALUE, id, suffix(tmd));
        // undocumented in .tmd: the postings writer's header (IndexHeader + PackedBlockSize)
        skipIndexHeader(in);
        in.readVInt(); // PackedBlockSize
        assertEquals("NumFields", 1, in.readVInt());
        assertEquals("FieldNumber", fi.number, in.readVInt());
        assertEquals("NumTerms", terms.size(), in.readVLong());
        int rootCodeLength = in.readVInt();
        in.skipBytes(rootCodeLength);
        assertEquals(
            "SumTotalTermFreq (after RootCodeLength=" + rootCodeLength + ")",
            terms.getSumTotalTermFreq(),
            in.readVLong());
        assertEquals("SumDocFreq", terms.getSumDocFreq(), in.readVLong());
        assertEquals("DocCount", terms.getDocCount(), in.readVInt());
        int len = in.readVInt();
        byte[] min = new byte[len];
        in.readBytes(min, 0, len);
        assertEquals("MinTerm", terms.getMin(), new BytesRef(min));
      }
    }
  }
}

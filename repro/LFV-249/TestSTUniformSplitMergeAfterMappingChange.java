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
package org.apache.lucene.codecs.uniformsplit.sharedterms;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Merging segments written under different per-field postings mappings must keep every term when
 * the target group of fields uses SharedTermsUniformSplit.
 */
public class TestSTUniformSplitMergeAfterMappingChange extends LuceneTestCase {

  /** The default codec, with field "a" in {@code forA} and field "b" in {@code forB}. */
  private static Codec codec(PostingsFormat forA, PostingsFormat forB) {
    return new Lucene104Codec() {
      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        return field.equals("a") ? forA : forB;
      }
    };
  }

  private static Document doc(String a, String b, boolean bFirst) {
    Document doc = new Document();
    if (bFirst) {
      doc.add(new StringField("b", b, Field.Store.NO));
    }
    doc.add(new StringField("a", a, Field.Store.NO));
    if (bFirst == false) {
      doc.add(new StringField("b", b, Field.Store.NO));
    }
    return doc;
  }

  /** Doc 1 under {@code before}, then doc 2 under {@code after} and forceMerge(1). */
  private static void indexTwoSessionsAndMerge(
      Directory dir, Codec before, Codec after, boolean bFirst) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(before))) {
      w.addDocument(doc("a1", "b1", bFirst));
    }
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(after))) {
      w.addDocument(doc("a2", "b2", bFirst));
      w.forceMerge(1);
    }
  }

  private static void assertNoTermLost(Directory dir) throws Exception {
    TestUtil.checkIndex(dir); // passes: the index is consistent, the postings are simply gone
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      assertEquals(2, r.numDocs());
      assertEquals(1, r.docFreq(new Term("a", "a1")));
      assertEquals(1, r.docFreq(new Term("a", "a2")));
      assertEquals(1, r.docFreq(new Term("b", "b2")));
      assertEquals("docFreq(b:b1), written in session 1", 1, r.docFreq(new Term("b", "b1")));
    }
  }

  /** b moves from the default postings format into the group that already holds a. */
  public void testFieldJoinsSharedTermsGroup() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory dir = newDirectory()) {
      indexTwoSessionsAndMerge(
          dir, codec(st, TestUtil.getDefaultPostingsFormat()), codec(st, st), false);
      assertNoTermLost(dir);
    }
  }

  /** a and b were in two SharedTermsUniformSplit instances (two groups); they become one group. */
  public void testTwoSharedTermsGroupsBecomeOne() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory dir = newDirectory()) {
      indexTwoSessionsAndMerge(
          dir,
          codec(new STUniformSplitPostingsFormat(), new STUniformSplitPostingsFormat()),
          codec(st, st),
          false);
      assertNoTermLost(dir);
    }
  }

  /** One writer session: addIndexes(CodecReader...) of an index written with the old mapping. */
  public void testAddIndexesFromIndexWithAnotherMapping() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory source = newDirectory();
        Directory dir = newDirectory()) {
      Codec old = codec(st, TestUtil.getDefaultPostingsFormat());
      try (IndexWriter w = new IndexWriter(source, new IndexWriterConfig().setCodec(old))) {
        w.addDocument(doc("a1", "b1", false));
      }
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec(st, st)));
          DirectoryReader r = DirectoryReader.open(source)) {
        w.addDocument(doc("a2", "b2", false));
        w.commit();
        CodecReader[] leaves = new CodecReader[r.leaves().size()];
        for (LeafReaderContext ctx : r.leaves()) {
          leaves[ctx.ord] = (CodecReader) ctx.reader();
        }
        w.addIndexes(leaves);
        w.forceMerge(1);
      }
      assertNoTermLost(dir);
    }
  }

  /**
   * Control: the same mapping change, but b gets the lower field number, so the first field with
   * terms in the old segment is not a SharedTermsUniformSplit field and the merge falls back to the
   * default merge. Passes before and after the fix.
   */
  public void testControlFirstFieldNotSharedTerms() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory dir = newDirectory()) {
      indexTwoSessionsAndMerge(
          dir, codec(st, TestUtil.getDefaultPostingsFormat()), codec(st, st), true);
      assertNoTermLost(dir);
    }
  }
}

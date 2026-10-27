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
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
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
 * When the per-field postings mapping changes between writer sessions (the mapping is written to
 * the index, so new segments may use another one), a merge whose SharedTermsUniformSplit group
 * takes fields that a source segment stored under another format group drops their postings.
 */
public class TestSTUniformSplitMergeMappingChange extends LuceneTestCase {

  /** A codec that writes field "a" with {@code forA} and every other field with {@code forOthers}. */
  private static Codec perFieldCodec(PostingsFormat forA, PostingsFormat forOthers) {
    Codec base = TestUtil.getDefaultCodec();
    return new FilterCodec(base.getName(), base) {
      final PostingsFormat perField =
          new PerFieldPostingsFormat() {
            @Override
            public PostingsFormat getPostingsFormatForField(String field) {
              return field.equals("a") ? forA : forOthers;
            }
          };

      @Override
      public PostingsFormat postingsFormat() {
        return perField;
      }
    };
  }

  private static Document doc(String a, String b) {
    Document doc = new Document();
    doc.add(new StringField("a", a, Field.Store.NO));
    doc.add(new StringField("b", b, Field.Store.NO));
    return doc;
  }

  private static void assertAllTermsPresent(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      assertEquals(1, r.docFreq(new Term("a", "a1")));
      assertEquals(1, r.docFreq(new Term("a", "a2")));
      assertEquals(1, r.docFreq(new Term("b", "b2")));
      assertEquals("postings of field b written by the first session", 1, r.docFreq(new Term("b", "b1")));
    }
  }

  /** Session 1: a -> SharedTermsUniformSplit, b -> default; session 2: both -> SharedTermsUniformSplit. */
  public void testMergeTakesFieldFromAnotherFormat() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory dir = newDirectory()) {
      Codec first = perFieldCodec(st, TestUtil.getDefaultPostingsFormat());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(first))) {
        w.addDocument(doc("a1", "b1"));
      }
      Codec second = perFieldCodec(st, st);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(second))) {
        w.addDocument(doc("a2", "b2"));
        w.forceMerge(1);
      }
      assertAllTermsPresent(dir);
    }
  }

  /** Session 1: a and b in two separate SharedTermsUniformSplit groups; session 2: one group. */
  public void testMergeTakesFieldsFromTwoSharedTermsGroups() throws Exception {
    try (Directory dir = newDirectory()) {
      Codec first = perFieldCodec(new STUniformSplitPostingsFormat(), new STUniformSplitPostingsFormat());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(first))) {
        w.addDocument(doc("a1", "b1"));
      }
      PostingsFormat st = new STUniformSplitPostingsFormat();
      Codec second = perFieldCodec(st, st);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(second))) {
        w.addDocument(doc("a2", "b2"));
        w.forceMerge(1);
      }
      assertAllTermsPresent(dir);
    }
  }

  /** The same loss in a single writer session: addIndexes(CodecReader) of an index with another mapping. */
  public void testAddIndexesFromIndexWithAnotherMapping() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory source = newDirectory();
        Directory dir = newDirectory()) {
      Codec sourceCodec = perFieldCodec(st, TestUtil.getDefaultPostingsFormat());
      try (IndexWriter w = new IndexWriter(source, new IndexWriterConfig().setCodec(sourceCodec))) {
        w.addDocument(doc("a1", "b1"));
      }
      try (IndexWriter w =
              new IndexWriter(dir, new IndexWriterConfig().setCodec(perFieldCodec(st, st)));
          DirectoryReader r = DirectoryReader.open(source)) {
        w.addDocument(doc("a2", "b2"));
        w.commit();
        CodecReader[] leaves = new CodecReader[r.leaves().size()];
        int i = 0;
        for (LeafReaderContext ctx : r.leaves()) {
          leaves[i++] = (CodecReader) ctx.reader();
        }
        w.addIndexes(leaves);
        w.forceMerge(1);
      }
      assertAllTermsPresent(dir);
    }
  }
}

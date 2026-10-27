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
package org.apache.lucene.codecs.perfield;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A merge under a per-field postings codec keeps the per-field postings attributes that a field
 * inherited from a source segment's FieldInfos when no source postings reader lists that field, so
 * the merged segment names postings files that were never written and cannot be opened.
 *
 * <p>Writer sessions may use different codecs (IndexWriterConfig#setCodec); segments written by
 * SimpleTextCodec, whose postings format is not a PerFieldPostingsFormat, carry the FieldInfos
 * attributes of the segments they were merged from.
 */
public class TestPerFieldPostingsStaleAttributes extends LuceneTestCase {

  public void testMergeAfterNonPerFieldSegmentKeepsIndexReadable() throws Exception {
    try (Directory dir = newDirectory()) {
      // Session 1: the default codec with two instances of the default postings format, so that
      // field "g" is written with the per-field suffix 1 (PerFieldPostingsFormat.suffix=1).
      PostingsFormat first = TestUtil.getDefaultPostingsFormat();
      PostingsFormat second = TestUtil.getDefaultPostingsFormat();
      Codec perFieldCodec =
          new FilterCodec(
              TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
            final PostingsFormat perField =
                new PerFieldPostingsFormat() {
                  @Override
                  public PostingsFormat getPostingsFormatForField(String field) {
                    return field.equals("g") ? second : first;
                  }
                };

            @Override
            public PostingsFormat postingsFormat() {
              return perField;
            }
          };
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(perFieldCodec))) {
        Document doc = new Document();
        doc.add(new StringField("id", "0", Field.Store.YES));
        doc.add(new StringField("f", "x", Field.Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));
        doc.add(new StringField("g", "y", Field.Store.NO));
        w.addDocument(doc);
      }

      // Session 2: SimpleTextCodec. Delete the only document with "g" and merge: the merged
      // segment has no postings for "g", but its FieldInfos still carry g's per-field attributes.
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(new SimpleTextCodec()))) {
        w.deleteDocuments(new Term("id", "1"));
        w.forceMerge(1);
      }

      // Session 3: back to the default codec; add a document and merge everything.
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StringField("id", "2", Field.Store.YES));
        doc.add(new StringField("f", "z", Field.Store.NO));
        w.addDocument(doc);
        w.forceMerge(1);
      }

      // Every writer session succeeded, so the committed index must be readable.
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
        assertEquals(2, r.docFreq(new Term("f", "x")) + r.docFreq(new Term("f", "z")));
        assertEquals(0, r.docFreq(new Term("g", "y")));
      }
    }
  }

  /**
   * The same with the default codec in both per-field sessions (no custom per-field mapping): the
   * stale attribute is harmless while another indexed field keeps the same format group alive, but
   * when the final merge has no indexed field to write, it writes no postings group at all.
   */
  public void testDefaultCodecWhenNoIndexedFieldRemains() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StringField("g", "y", Field.Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new StoredField("s", "kept"));
        w.addDocument(doc);
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(new SimpleTextCodec()))) {
        w.deleteDocuments(new Term("g", "y"));
        w.forceMerge(1);
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StoredField("s", "added"));
        w.addDocument(doc);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }
}

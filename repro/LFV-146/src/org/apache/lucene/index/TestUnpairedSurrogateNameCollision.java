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
package org.apache.lucene.index;

import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Two distinct Java strings that differ only in an unpaired surrogate (for example "f\uD800" and
 * "f\uFFFD") are written as the same UTF-8 bytes by DataOutput#writeString, because the UTF-16 to
 * UTF-8 conversion replaces unpaired surrogates with U+FFFD. IndexWriter keys field names, field
 * attributes and commit user data by the original Java strings, so it accepts and commits both,
 * but the readers of the files it wrote reject the now-duplicate names. The commit succeeds and
 * the index can no longer be opened by DirectoryReader or by a new IndexWriter.
 *
 * <p>Each test asserts the basic durability contract: after addDocument/commit returned normally,
 * DirectoryReader.open(dir) opens the index.
 */
public class TestUnpairedSurrogateNameCollision extends LuceneTestCase {

  private static IndexWriterConfig config() {
    // the default codec, not a randomized one
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  /** Field names "f\uD800" and "f\uFFFD": both are written to .fnm as "f" + U+FFFD. */
  public void testFieldNames() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        Document doc = new Document();
        doc.add(new StringField("f\uD800", "a", Field.Store.NO));
        doc.add(new StringField("f\uFFFD", "b", Field.Store.NO));
        w.addDocument(doc);
        w.commit(); // succeeds
      }
      // fails on main: IllegalArgumentException: duplicate field names: 0 and 1 have: f\uFFFD
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }

  /** Commit user data keys "k\uD800" and "k\uFFFD" in segments_N. */
  public void testCommitUserDataKeys() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        w.addDocument(doc);
        w.setLiveCommitData(Map.of("k\uD800", "1", "k\uFFFD", "2").entrySet());
        w.commit(); // succeeds
      }
      // fails on main: IllegalArgumentException: duplicate key: k\uFFFD (from readMapOfStrings)
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }

  /** Field attribute keys (FieldType#putAttribute) "a\uD800" and "a\uFFFD" in .fnm. */
  public void testFieldAttributeKeys() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
        ft.putAttribute("a\uD800", "1");
        ft.putAttribute("a\uFFFD", "2");
        ft.freeze();
        Document doc = new Document();
        doc.add(new Field("body", "hello", ft));
        w.addDocument(doc);
        w.commit(); // succeeds
      }
      // fails on main: IllegalArgumentException: duplicate key: a\uFFFD (from readMapOfStrings)
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }

  /**
   * Index sort on field "s\uD800": the .si stores the sort field name as "s" + U+FFFD, so a new
   * IndexWriter with the very same IndexWriterConfig sort is refused.
   */
  public void testIndexSortFieldName() throws Exception {
    Sort sort = new Sort(new SortField("s\uD800", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config().setIndexSort(sort))) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("s\uD800", 5));
        w.addDocument(doc);
        w.commit(); // succeeds
      }
      // fails on main: IllegalArgumentException: cannot change previous indexSort=<long: "s\uFFFD">
      // (from segment=_0(...)) to new indexSort=<long: "s\uD800">
      try (IndexWriter w = new IndexWriter(dir, config().setIndexSort(sort))) {
        assertEquals(1, w.getDocStats().numDocs);
      }
    }
  }
}

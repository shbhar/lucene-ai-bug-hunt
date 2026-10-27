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
package org.apache.lucene.misc.index;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * MultiPassIndexSplitter "splits input index into multiple equal parts". It takes no
 * IndexWriterConfig and builds every part with a bare new IndexWriterConfig(null), so any index
 * whose field infos carry a soft-deletes field or a parent field cannot be split.
 */
public class TestMultiPassIndexSplitterIndexSettings extends LuceneTestCase {

  private static Document doc(int id) {
    Document d = new Document();
    d.add(new StringField("id", "" + id, Field.Store.YES));
    return d;
  }

  private static Set<String> liveIds(DirectoryReader reader) throws Exception {
    Set<String> ids = new HashSet<>();
    for (var ctx : reader.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      StoredFields sf = ctx.reader().storedFields();
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live == null || live.get(d)) {
          assertTrue("duplicate id", ids.add(sf.document(d).get("id")));
        }
      }
    }
    return ids;
  }

  private void splitAndCheck(DirectoryReader input) throws Exception {
    Set<String> expected = liveIds(input);
    Directory[] parts = {newDirectory(), newDirectory()};
    try {
      new MultiPassIndexSplitter().split(input, parts, random().nextBoolean());
      Set<String> got = new HashSet<>();
      for (Directory part : parts) {
        try (DirectoryReader r = DirectoryReader.open(part)) {
          for (String id : liveIds(r)) {
            assertTrue("document " + id + " in two parts", got.add(id));
          }
        }
      }
      assertEquals(expected, got);
    } finally {
      for (Directory part : parts) {
        part.close();
      }
    }
  }

  /** An index that uses soft deletes (softUpdateDocument). */
  public void testSplitIndexWithSoftDeletes() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null).setSoftDeletesField("soft"))) {
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc(i));
        }
        w.softUpdateDocument(new Term("id", "3"), doc(3), new NumericDocValuesField("soft", 1));
      }
      try (DirectoryReader reader =
          new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
        splitAndCheck(reader);
      }
    }
  }

  /** An index with a parent field (required for document blocks in sorted indexes). */
  public void testSplitIndexWithParentField() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null).setParentField("_parent"))) {
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc(i));
        }
        w.addDocuments(List.of(doc(10), doc(11)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        splitAndCheck(reader);
      }
    }
  }
}

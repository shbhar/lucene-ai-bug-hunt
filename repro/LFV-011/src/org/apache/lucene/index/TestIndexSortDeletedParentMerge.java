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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestIndexSortDeletedParentMerge extends LuceneTestCase {

  private static Document doc(String id, long sortValue) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("sort", sortValue));
    return doc;
  }

  public void testMergeAfterDeletingBlockParent() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)))
            .setParentField("parent")
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
      w.commit();
      w.deleteDocuments(new Term("id", "p1"));
      w.addDocument(doc("x", 5));
      w.forceMerge(1);
      w.addDocument(doc("y", 7));
      // expected: merging [x, c1] (c1 lost its parent) works; actual: IndexOutOfBoundsException
      try {
        w.forceMerge(1);
      } catch (Exception e) {
        throw new AssertionError("expected the second forceMerge(1) to succeed, got " + e, e);
      }
    }
  }
}

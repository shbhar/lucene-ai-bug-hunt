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

public class TestParentFieldDocValuesUpdate extends LuceneTestCase {

  public void testUpdateParentFieldOfChildKeepsIndexSorted() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)))
              .setParentField("_parent");
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document child = new Document();
        child.add(new StringField("id", "child", Field.Store.NO));
        child.add(new NumericDocValuesField("sort", 9));
        Document parent = new Document();
        parent.add(new NumericDocValuesField("sort", 1));
        w.addDocuments(List.of(child, parent));
        w.commit();
        try {
          w.updateNumericDocValue(new Term("id", "child"), "_parent", -1);
        } catch (IllegalArgumentException e) {
          return;
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
        // expected: the update is rejected (IllegalArgumentException) or the segment stays in index-sort order; actual: out of order
        assertNull(CheckIndex.testSort(segment, iwc.getIndexSort(), null, false).error);
      }
    }
  }
}

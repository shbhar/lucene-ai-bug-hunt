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

public class TestIntIndexSortMergeOrder extends LuceneTestCase {

  public void testMergeKeepsIntSortOrder() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setIndexSort(new Sort(new SortField("s", SortField.Type.INT)))
            .setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a", 1L << 32));
      w.commit();
      w.addDocument(doc("b", 1));
      w.addDocument(doc("c", 2));
      w.forceMerge(1);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf = getOnlyLeafReader(r);
        StoredFields storedFields = leaf.storedFields();
        StringBuilder order = new StringBuilder();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          order.append(storedFields.document(doc).get("id"));
        }
        // expected: int order a (int 0), b (1), c (2), as flush and search use; actual: b, c, a
        assertEquals("abc", order.toString());
      }
    }
  }

  private static Document doc(String id, long value) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new NumericDocValuesField("s", value));
    return doc;
  }
}

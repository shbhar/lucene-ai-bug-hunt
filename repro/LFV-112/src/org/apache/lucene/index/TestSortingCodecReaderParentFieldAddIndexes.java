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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Re-sorting an index that has document blocks (parent field set) with SortingCodecReader and
 * addIndexes, as the SortingCodecReader javadoc describes. With assertions enabled, addIndexes
 * fails in SortingCodecReader's cache assertion because the parent field's doc values are read
 * twice (once by MultiSorter, once by the doc-values merge) and the assertion only allows sort
 * fields to be read twice.
 */
public class TestSortingCodecReaderParentFieldAddIndexes extends LuceneTestCase {

  private static Document doc(String id, long sortValue) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("sort", sortValue));
    return d;
  }

  public void testResortIndexWithBlocks() throws Exception {
    Sort sort = new Sort(new SortField("sort", SortField.Type.LONG));
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              src,
              new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setParentField("_parent"))) {
        w.addDocuments(List.of(doc("c1", 7), doc("p1", 7)));
        w.addDocument(doc("x", 3));
      }
      try (IndexWriter w =
              new IndexWriter(
                  dst,
                  new IndexWriterConfig()
                      .setCodec(TestUtil.getDefaultCodec())
                      .setParentField("_parent")
                      .setIndexSort(sort)
                      .setMergeScheduler(new SerialMergeScheduler()));
          DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader leaf = (CodecReader) getOnlyLeafReader(r);
        w.addIndexes(SortingCodecReader.wrap(leaf, sort));
        w.commit();
        try (DirectoryReader sorted = DirectoryReader.open(w)) {
          LeafReader l = getOnlyLeafReader(sorted);
          StoredFields sf = l.storedFields();
          assertEquals("x", sf.document(0).get("id"));
          assertEquals("c1", sf.document(1).get("id"));
          assertEquals("p1", sf.document(2).get("id"));
        }
      }
    }
  }
}

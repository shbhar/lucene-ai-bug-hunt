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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortingCodecReaderParentField extends LuceneTestCase {

  private static Document doc(long sortValue) {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("sort", sortValue));
    return doc;
  }

  public void testResortBlocksWithAddIndexes() throws Exception {
    Sort sort = new Sort(new SortField("sort", SortField.Type.LONG));
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(src, new IndexWriterConfig().setParentField("_parent"))) {
        w.addDocuments(List.of(doc(7), doc(7)));
        w.addDocument(doc(3));
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setParentField("_parent")
              .setIndexSort(sort)
              .setMergeScheduler(new SerialMergeScheduler());
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, iwc)) {
        // expected: the re-sort succeeds (as with -da); actual (-ea): [_parent] is not a sort field
        w.addIndexes(SortingCodecReader.wrap((CodecReader) r.leaves().get(0).reader(), sort));
      }
    }
  }
}

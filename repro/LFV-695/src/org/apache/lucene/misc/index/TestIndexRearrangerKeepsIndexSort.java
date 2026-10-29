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

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestIndexRearrangerKeepsIndexSort extends LuceneTestCase {

  public void testIndexSortOfConfigIsKept() throws Exception {
    Sort sort = new Sort(new SortField("ord", SortField.Type.LONG));
    try (Directory in = newDirectory();
        Directory out = newDirectory()) {
      try (IndexWriter w = new IndexWriter(in, new IndexWriterConfig().setIndexSort(sort))) {
        Document doc = new Document();
        doc.add(new BinaryDocValuesField("id", new BytesRef("0")));
        doc.add(new NumericDocValuesField("ord", 0));
        w.addDocument(doc);
      }
      new IndexRearranger(
              in,
              out,
              new IndexWriterConfig().setIndexSort(sort),
              BinaryDocValueSelector.createLiveSelectorsFromIndex("id", in))
          .execute();
      // expected: the rearranged segment has the config's index sort; actual: null
      assertEquals(sort, SegmentInfos.readLatestCommit(out).info(0).info.getIndexSort());
    }
  }
}

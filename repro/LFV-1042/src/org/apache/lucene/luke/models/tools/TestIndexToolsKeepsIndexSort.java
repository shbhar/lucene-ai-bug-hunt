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
package org.apache.lucene.luke.models.tools;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexToolsKeepsIndexSort extends LuceneTestCase {

  public void testOptimizeKeepsIndexSort() throws Exception {
    Sort sort = new Sort(new SortField("ts", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort))) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("ts", i));
          w.addDocument(doc);
          w.commit();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        new IndexToolsImpl(reader, false, false).optimize(false, 1, null);
      }
      // expected: the sorted writer still opens the index; actual: IAE "cannot change previous indexSort=null"
      new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort)).close();
    }
  }
}

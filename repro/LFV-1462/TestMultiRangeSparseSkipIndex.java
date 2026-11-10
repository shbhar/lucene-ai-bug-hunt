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
package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMultiRangeSparseSkipIndex extends LuceneTestCase {

  public void testTwoDocsWithoutValueInARow() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (Long v : new Long[] {1L, null, null, 50L}) {
        Document doc = new Document();
        if (v != null) {
          doc.add(SortedNumericDocValuesField.indexedField("f", v));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query =
            new DocValuesMultiRangeQuery.SortedNumericStabbingBuilder("f")
                .add(40, 60)
                .add(100, 200)
                .build();
        // expected 1 (the document with 50); actual 2: doc 2, which has no value, also matches
        assertEquals(1, new IndexSearcher(reader).count(query));
      }
    }
  }
}

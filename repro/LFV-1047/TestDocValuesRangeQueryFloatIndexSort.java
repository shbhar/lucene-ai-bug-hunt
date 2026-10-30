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
package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDocValuesRangeQueryFloatIndexSort extends LuceneTestCase {

  public void testExactQueryOnFloatIndexSort() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      iwc.setIndexSort(new Sort(new SortField("f", SortField.Type.FLOAT)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float v : new float[] {-1f, -2f}) {
          Document doc = new Document();
          doc.add(NumericDocValuesField.indexedField("f", Float.floatToRawIntBits(v)));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // expected: the one doc holding raw(-1f) matches; actual on main: 0 matches
        assertEquals(
            1,
            new IndexSearcher(reader)
                .count(NumericDocValuesField.newSlowExactQuery("f", Float.floatToRawIntBits(-1f))));
      }
    }
  }
}

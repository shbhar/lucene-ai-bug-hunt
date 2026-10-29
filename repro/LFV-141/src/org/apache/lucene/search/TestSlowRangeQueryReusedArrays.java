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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntRangeDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSlowRangeQueryReusedArrays extends LuceneTestCase {

  public void testDisjunctionBuiltFromReusedArrays() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new IntRangeDocValuesField("r", new int[] {5}, new int[] {10}));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        int[] min = {0};
        int[] max = {3};
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        b.add(IntRangeDocValuesField.newSlowIntersectsQuery("r", min, max), Occur.SHOULD);
        min[0] = 5;
        max[0] = 6;
        b.add(IntRangeDocValuesField.newSlowIntersectsQuery("r", min, max), Occur.SHOULD);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        // expected: [0,3] OR [5,6] matches the one doc with range [5,10] (count 1); actual: 0
        assertEquals(1, searcher.count(b.build()));
      }
    }
  }
}

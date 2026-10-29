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
package org.apache.lucene.sandbox.facet.plain.histograms;

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.internal.hppc.LongIntHashMap;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHistogramLongMaxValue extends LuceneTestCase {

  public void testValueLongMaxValueIsCounted() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(List.of(new LongField("f", Long.MAX_VALUE, Field.Store.NO)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LongIntHashMap counts =
            new IndexSearcher(reader)
                .search(MatchAllDocsQuery.INSTANCE, new HistogramCollectorManager("f", 2));
        LongIntHashMap expected = new LongIntHashMap();
        expected.put(Long.MAX_VALUE / 2, 1);
        // Expected: 1 doc in bucket Long.MAX_VALUE / 2; actual: no bucket (leafMax + 1 overflows).
        assertEquals(expected, counts);
      }
    }
  }
}

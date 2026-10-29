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
package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFromDoubleValuesSourceRange extends LuceneTestCase {

  public void testRangeComparesDoubles() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new DoubleDocValuesField("d", Math.nextUp(1.0)));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        ValueSource vs =
            ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("d"));
        // expected 0 hits (1.0000000000000002 is not in [1.0, 1.0]); actual 1 (compared as floats)
        FunctionRangeQuery query = new FunctionRangeQuery(vs, 1.0, 1.0, true, true);
        assertEquals(0, new IndexSearcher(r).count(query));
      }
    }
  }
}

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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

public class TestSortableLongValuesSourceRewrite extends LuceneTestCase {

  public void testRewriteKeepsSortableLongEncoding() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new DoubleDocValuesField("price", 0.75));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LongValuesSource source =
            DoubleValuesSource.fromDoubleField("price")
                .toSortableLongDoubleValuesSource()
                .rewrite(new IndexSearcher(reader));
        LongValues values = source.getValues(reader.leaves().get(0), null);
        assertTrue(values.advanceExact(0));
        // expected doubleToSortableLong(0.75) = 4604930618986332160, actual the (long) cast 0
        assertEquals(
            "value of 0.75 after rewrite, via " + source,
            NumericUtils.doubleToSortableLong(0.75),
            values.longValue());
      }
    }
  }
}

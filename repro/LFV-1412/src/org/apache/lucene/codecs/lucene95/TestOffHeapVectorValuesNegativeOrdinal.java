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
package org.apache.lucene.codecs.lucene95;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestOffHeapVectorValuesNegativeOrdinal extends LuceneTestCase {

  public void testNegativeOrdinal() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new KnnFloatVectorField("v", new float[] {1, 2}));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FloatVectorValues values = getOnlyLeafReader(r).getFloatVectorValues("v");
        // expected: IndexOutOfBoundsException (javadoc); actual: returns the zero vector [0.0, 0.0]
        expectThrows(IndexOutOfBoundsException.class, () -> values.vectorValue(-1));
      }
    }
  }
}

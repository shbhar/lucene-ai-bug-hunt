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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSparseVectorValuesSecondIterator extends LuceneTestCase {

  // expected: a second iterator() starts at -1, as on a dense field; actual: it stands on doc 0
  public void testSecondIteratorStartsOver() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("field", new float[] {1f, 2f}));
        writer.addDocument(doc);
        writer.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FloatVectorValues values = getOnlyLeafReader(reader).getFloatVectorValues("field");
        assertEquals(0, values.iterator().nextDoc());

        KnnVectorValues.DocIndexIterator second = values.iterator();
        assertEquals(-1, second.docID());
        assertEquals(0, second.nextDoc());
      }
    }
  }
}

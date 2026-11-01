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
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.Monster;
import org.apache.lucene.tests.util.TestUtil;

@Monster("indexes 357,913,941 documents, needs an 8 GB heap")
public class TestNumericDocValuesBulkLongValuesSize extends LuceneTestCase {

  public void testLongValuesOfAllDocsWith48BitValues() throws Exception {
    final int numDocs = 357_913_941;
    try (Directory dir = FSDirectory.open(createTempDir())) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        NumericDocValuesField field = new NumericDocValuesField("f", 0);
        doc.add(field);
        for (int i = 0; i < numDocs; i++) {
          field.setLongValue((i & 1L) << 47 | i);
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        NumericDocValues dv = getOnlyLeafReader(reader).getNumericDocValues("f");
        int[] docs = new int[numDocs];
        Arrays.setAll(docs, i -> i);
        try {
          dv.longValues(numDocs, docs, new long[numDocs], -1L);
        } catch (IOException | RuntimeException e) {
          // Expected: all values; actual: 6 * numDocs + 2 padding bytes = 2^31 wraps, so it throws.
          throw new AssertionError("expected all " + numDocs + " values, but it threw " + e, e);
        }
      }
    }
  }
}

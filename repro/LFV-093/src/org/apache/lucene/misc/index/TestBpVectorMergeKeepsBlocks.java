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
package org.apache.lucene.misc.index;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorMergeKeepsBlocks extends LuceneTestCase {

  public void testForceMergeKeepsBlocks() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config =
          new IndexWriterConfig()
              .setParentField("_parent")
              .setMergePolicy(
                  new BPReorderingMergePolicy(
                      new LogDocMergePolicy(), new BpVectorReorderer("vector")));
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        for (int block = 0; block < 2; block++) {
          Document child = new Document();
          child.add(new StringField("id", "c" + block, Field.Store.YES));
          child.add(new KnnFloatVectorField("vector", new float[] {1, block + 1}));
          Document parent = new Document();
          parent.add(new StringField("id", "p" + block, Field.Store.YES));
          writer.addDocuments(List.of(child, parent));
          writer.commit();
        }
        writer.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        StoredFields storedFields = reader.storedFields();
        List<String> order = new ArrayList<>();
        for (int doc = 0; doc < reader.maxDoc(); doc++) {
          order.add(storedFields.document(doc).get("id"));
        }
        // expected: each child directly before its own parent; actual: [c0, c1, p0, p1]
        assertTrue(
            "expected blocks [cN, pN] kept together after the merge, got " + order,
            order.equals(List.of("c0", "p0", "c1", "p1"))
                || order.equals(List.of("c1", "p1", "c0", "p0")));
      }
    }
  }
}

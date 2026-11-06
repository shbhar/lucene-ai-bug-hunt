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

import java.util.List;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergedFieldInfosLeafOrder extends LuceneTestCase {

  private static String outcome(IndexReader... leaves) throws Exception {
    try {
      FieldInfos.getMergedFieldInfos(new MultiReader(leaves));
      return "accepted";
    } catch (IllegalArgumentException e) {
      return "rejected";
    }
  }

  public void testLeafOrder() throws Exception {
    try (Directory softDir = newDirectory();
        Directory plainDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(softDir, new IndexWriterConfig().setSoftDeletesField("x"))) {
        w.addDocument(List.of());
        w.addDocument(List.of(new NumericDocValuesField("x", 1)));
      }
      try (IndexWriter w = new IndexWriter(plainDir, new IndexWriterConfig())) {
        w.addDocument(List.of(new NumericDocValuesField("x", 1)));
      }
      try (DirectoryReader soft = DirectoryReader.open(softDir);
          DirectoryReader plain = DirectoryReader.open(plainDir)) {
        // expected: same outcome in both leaf orders; actual: (soft, plain) accepted, (plain, soft) rejected
        assertEquals(outcome(soft, plain), outcome(plain, soft));
      }
    }
  }
}

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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.BinarySortField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** Same scenario as TestSortFieldWithoutDocValues for BinarySortField (main only). */
public class TestBinarySortFieldWithoutDocValues extends LuceneTestCase {

  public void testBinarySortWithStoredBytes() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setIndexSort(new Sort(new BinarySortField("sort", false)));
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      IndexWriter w = new IndexWriter(dir, iwc);
      Document d = new Document();
      d.add(new StringField("id", "pending", Field.Store.YES));
      w.addDocument(d);
      Document bad = new Document();
      bad.add(new StringField("id", "bad", Field.Store.YES));
      bad.add(new StoredField("sort", new BytesRef("x")));
      boolean rejected = false;
      try {
        w.addDocument(bad);
      } catch (IllegalArgumentException e) {
        rejected = true;
      }
      Throwable commitFailure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        commitFailure = t;
      }
      boolean open = w.isOpen();
      if (open) {
        w.close();
      }
      String state =
          "addDocument(bad) "
              + (rejected ? "rejected" : "accepted")
              + "; commit "
              + (commitFailure == null ? "succeeded" : "threw " + commitFailure)
              + "; writer open="
              + open;
      if (commitFailure != null) {
        System.out.println("V1-STATE " + state);
      }
      assertNull("commit must not fail: " + state, commitFailure);
      assertTrue(open);
    }
  }
}

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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexWriterConfigChangeAfterWriterCreation extends LuceneTestCase {

  public void testSetIndexSortAfterWriterCreationHasNoEffect() throws Exception {
    Sort byA = new Sort(new SortField("a", SortField.Type.LONG));
    Sort byB = new Sort(new SortField("b", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setIndexSort(byA).setMergeScheduler(new SerialMergeScheduler());
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc());
      w.commit(); // one segment, sorted by a

      // IndexWriterConfig javadoc: "Once IndexWriter has been created with this object, changes to
      // this object will not affect the IndexWriter instance."
      try {
        iwc.setIndexSort(byB);
      } catch (IllegalStateException _) {
        // refusing the change keeps that promise too
      }
      w.addDocument(doc()); // a second segment, flushed by forceMerge

      try {
        w.forceMerge(1);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("forceMerge(1) failed; writer still open: " + w.isOpen(), e);
      }
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(byA, r.leaves().get(0).reader().getMetaData().sort());
      }
    }
  }

  private static Document doc() {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("a", 1));
    doc.add(new NumericDocValuesField("b", 1));
    return doc;
  }
}

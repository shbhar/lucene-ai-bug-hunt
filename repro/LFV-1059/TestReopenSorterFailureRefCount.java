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

import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReopenSorterFailureRefCount extends LuceneTestCase {

  public void testFailedLeafSortDuringReopenReleasesSharedSegment() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    w.addDocument(new Document());
    w.commit();
    Comparator<LeafReader> sorter =
        (a, b) -> {
          throw new IllegalStateException("sorter failure");
        };
    DirectoryReader reader = DirectoryReader.open(dir, sorter);
    LeafReader shared = reader.leaves().get(0).reader();
    w.addDocument(new Document());
    w.close();
    expectThrows(IllegalStateException.class, () -> DirectoryReader.openIfChanged(reader));
    // Expected: the failed reopen released its reference (refCount 1); actual: 2 (leaked).
    assertEquals(1, shared.getRefCount());
    reader.close();
    dir.close();
  }
}

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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestForceMergeDeletesRejectedMerge extends LuceneTestCase {

  public void testObserverCompleteWhenBlockingCallReturns() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    ConcurrentMergeScheduler heldMerges =
        new ConcurrentMergeScheduler() {
          @Override
          protected void doMerge(MergeSource mergeSource, MergePolicy.OneMerge merge)
              throws IOException {
            try {
              release.await();
            } catch (InterruptedException e) {
              throw new ThreadInterruptedException(e);
            }
            super.doMerge(mergeSource, merge);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(new LogDocMergePolicy())
            .setMergeScheduler(heldMerges);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(List.of(new StringField("id", "a", Field.Store.NO)));
      w.addDocument(List.of(new StringField("id", "b", Field.Store.NO)));
      w.deleteDocuments(new Term("id", "a"));
      w.forceMerge(1, false);
      MergePolicy.MergeObserver observer;
      try {
        observer = w.forceMergeDeletes();
      } finally {
        release.countDown();
      }
      // expected: all observed merges complete on return (javadoc); actual: the rejected one is not
      assertEquals("completed merges", observer.numMerges(), observer.numCompletedMerges());
    }
  }
}

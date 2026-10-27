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
package org.apache.lucene.facet.taxonomy.directory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * TaxonomyWriter.getSize(): "the taxonomy contains ordinals 0 through getSize()-1"; getParent()
 * returns the parent of a category in the taxonomy. DirectoryTaxonomyWriter publishes the new size
 * (nextID.getAndIncrement) before it records the new category's parent in its parent array, so a
 * concurrent getParent(getSize() - 1) can return 0 instead of the real parent, or throw
 * ArrayIndexOutOfBoundsException (AssertionError with assertions on) when the array is growing.
 *
 * <p>Probabilistic (no hook exists inside the window); the number of observed violations is printed.
 */
public class TestTaxonomyWriterGetSizeGetParentRace extends LuceneTestCase {

  public void testGetParentOfLastOrdinalWhileAdding() throws Exception {
    Directory dir = newDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir);
    final int dimOrd = tw.addCategory(new FacetLabel("dim")); // 1; every later category is dim/x
    final int numAdds = 100_000;
    AtomicBoolean done = new AtomicBoolean();
    AtomicReference<String> firstViolation = new AtomicReference<>();
    long[] violations = new long[1];
    long[] checks = new long[1];
    CountDownLatch start = new CountDownLatch(1);

    Thread checker =
        new Thread(
            () -> {
              try {
                start.await();
                while (done.get() == false) {
                  int n = tw.getSize();
                  if (n - 1 <= dimOrd) {
                    continue;
                  }
                  checks[0]++;
                  String bad = null;
                  try {
                    int parent = tw.getParent(n - 1);
                    if (parent != dimOrd) {
                      bad = "getParent(" + (n - 1) + ") = " + parent + " with getSize() = " + n;
                    }
                  } catch (RuntimeException | AssertionError | java.io.IOException e) {
                    bad = "getParent(" + (n - 1) + ") with getSize() = " + n + " threw " + e;
                  }
                  if (bad != null) {
                    violations[0]++;
                    firstViolation.compareAndSet(null, bad);
                  }
                }
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });
    checker.start();
    start.countDown();
    for (int i = 0; i < numAdds && firstViolation.get() == null; i++) {
      tw.addCategory(new FacetLabel("dim", Integer.toString(i)));
    }
    done.set(true);
    checker.join();
    System.out.println(
        "getSize/getParent race: " + violations[0] + " violations in " + checks[0] + " checks");
    tw.close();
    dir.close();
    assertNull(
        "every ordinal below getSize() is in the taxonomy (parent of dim/x is " + dimOrd + ")",
        firstViolation.get());
  }
}

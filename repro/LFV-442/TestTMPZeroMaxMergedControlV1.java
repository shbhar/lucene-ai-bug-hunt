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

import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Controls for TestTMPZeroMaxMergedV1: the same scenarios with a 1-byte maximum (the smallest
 * positive value) and with the default maximum must pass, and report whether deletes were
 * reclaimed (singleton merge).
 */
public class TestTMPZeroMaxMergedControlV1 extends LuceneTestCase {

  static final double ONE_BYTE_MB = 1.0 / 1024 / 1024;

  private void runCommit(double maxMB) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, TestTMPZeroMaxMergedV1.config(maxMB, 20.0));
      TestTMPZeroMaxMergedV1.indexAndDelete(w, 50);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        System.out.println(
            "CONTROL commit maxMB="
                + maxMB
                + " leaves="
                + r.leaves().size()
                + " maxDoc="
                + r.maxDoc()
                + " numDocs="
                + r.numDocs());
        assertEquals(50, r.numDocs());
      }
      w.close();
    }
  }

  private void runForceMergeDeletes(double maxMB) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, TestTMPZeroMaxMergedV1.config(maxMB, 50.0));
      TestTMPZeroMaxMergedV1.indexAndDelete(w, 30);
      w.commit();
      w.forceMergeDeletes();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        System.out.println(
            "CONTROL forceMergeDeletes maxMB="
                + maxMB
                + " leaves="
                + r.leaves().size()
                + " maxDoc="
                + r.maxDoc()
                + " numDocs="
                + r.numDocs());
        assertEquals(70, r.numDocs());
      }
      w.close();
    }
  }

  public void testOneByteCommit() throws Exception {
    runCommit(ONE_BYTE_MB);
  }

  public void testOneByteForceMergeDeletes() throws Exception {
    runForceMergeDeletes(ONE_BYTE_MB);
  }

  public void testDefaultCommit() throws Exception {
    runCommit(new TieredMergePolicy().getMaxMergedSegmentMB());
  }

  public void testDefaultForceMergeDeletes() throws Exception {
    runForceMergeDeletes(new TieredMergePolicy().getMaxMergedSegmentMB());
  }
}

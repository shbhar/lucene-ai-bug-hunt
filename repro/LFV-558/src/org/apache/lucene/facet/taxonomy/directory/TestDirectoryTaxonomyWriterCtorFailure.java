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

import java.io.IOException;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The DirectoryTaxonomyWriter constructor opens its IndexWriter (taking the taxonomy's write lock)
 * and then reads the latest commit and adds the root category. If any of that fails, the
 * constructor throws without closing the IndexWriter: the taxonomy stays write-locked and its files
 * stay open, so every later attempt to open a DirectoryTaxonomyWriter on it fails with
 * LockObtainFailedException.
 */
public class TestDirectoryTaxonomyWriterCtorFailure extends LuceneTestCase {

  private static boolean onStack(String classSimpleName, String method) {
    for (StackTraceElement e : new Throwable().getStackTrace()) {
      String c = e.getClassName();
      if (c.substring(c.lastIndexOf('.') + 1).equals(classSimpleName) && e.getMethodName().equals(method)) {
        return true;
      }
    }
    return false;
  }

  /** fails once, on the first file the DirectoryTaxonomyWriter constructor opens in the given step */
  private static MockDirectoryWrapper.Failure failOnceIn(String stepClass, String stepMethod) {
    return new MockDirectoryWrapper.Failure() {
      boolean done;

      @Override
      public void eval(MockDirectoryWrapper dir) throws IOException {
        if (done == false
            && onStack("MockDirectoryWrapper", "openInput")
            && onStack("DirectoryTaxonomyWriter", "<init>")
            && onStack("IndexWriter", "<init>") == false
            && onStack(stepClass, stepMethod)) {
          done = true;
          throw new IOException("simulated I/O error in " + stepClass + "." + stepMethod);
        }
      }
    };
  }

  private void assertTaxonomyCanBeReopened(MockDirectoryWrapper dir) throws IOException {
    LockObtainFailedException leaked = null;
    try {
      new DirectoryTaxonomyWriter(dir).close();
    } catch (LockObtainFailedException e) {
      leaked = e;
    }
    assertNull(
        "the failed constructor left its IndexWriter open (taxonomy still write-locked): " + leaked,
        leaked);
  }

  /** existing taxonomy: the constructor fails while it reads the commit data (index epoch) */
  public void testIOErrorReadingLatestCommit() throws Exception {
    try (MockDirectoryWrapper dir = newMockFSDirectory(createTempDir("taxo"))) {
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        tw.addCategory(new FacetLabel("dim", "a"));
      }
      dir.failOn(failOnceIn("SegmentInfos", "readLatestCommit"));
      IOException e = expectThrows(IOException.class, () -> new DirectoryTaxonomyWriter(dir));
      assertEquals("simulated I/O error in SegmentInfos.readLatestCommit", e.getMessage());
      assertTaxonomyCanBeReopened(dir);
    }
  }

  /** new taxonomy: the constructor fails while it opens a reader to add the root category */
  public void testIOErrorAddingRootCategory() throws Exception {
    try (MockDirectoryWrapper dir = newMockFSDirectory(createTempDir("taxo"))) {
      dir.failOn(failOnceIn("SegmentReader", "<init>"));
      IOException e = expectThrows(IOException.class, () -> new DirectoryTaxonomyWriter(dir));
      assertTrue(e.getMessage(), e.getMessage().startsWith("simulated I/O error in SegmentReader"));
      assertTaxonomyCanBeReopened(dir);
    }
  }
}

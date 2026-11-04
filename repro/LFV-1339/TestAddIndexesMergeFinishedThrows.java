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

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAddIndexesMergeFinishedThrows extends LuceneTestCase {

  // Expected: rollback() closes the writer after addIndexes reported the mergeFinished
  // failure. Actual: rollback() never returns (it waits in IndexWriter.abortMerges).
  @Timeout(millis = 10_000)
  public void testRollbackAfterMergeFinishedThrows() throws Exception {
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(readers) {
                  @Override
                  public void mergeFinished(boolean success, boolean segmentDropped)
                      throws IOException {
                    throw new IOException("mergeFinished failed");
                  }
                });
            return spec;
          }
        };
    try (Directory src = newDirectory();
        Directory dest = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        IndexWriter w =
            new IndexWriter(
                dest,
                new IndexWriterConfig()
                    .setMergePolicy(mp)
                    .setMergeScheduler(new SerialMergeScheduler()));
        expectThrows(IOException.class, () -> w.addIndexes((CodecReader) getOnlyLeafReader(r)));
        w.rollback();
        assertFalse(w.isOpen());
      }
    }
  }
}

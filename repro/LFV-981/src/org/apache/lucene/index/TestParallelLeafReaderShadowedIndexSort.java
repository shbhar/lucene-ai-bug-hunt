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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelLeafReaderShadowedIndexSort extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("s", SortField.Type.LONG));

  private static Directory index(IndexWriterConfig config, long... values) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, config)) {
      for (long value : values) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("s", value));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  public void testAddIndexesOfSidecarFirstReaderKeepsIndexSort() throws IOException {
    try (Directory main = index(new IndexWriterConfig().setIndexSort(SORT), 0, 1);
        Directory sidecar = index(new IndexWriterConfig(), 2, 1);
        Directory target = new ByteBuffersDirectory();
        DirectoryReader mainReader = DirectoryReader.open(main);
        DirectoryReader sidecarReader = DirectoryReader.open(sidecar);
        ParallelLeafReader parallel =
            new ParallelLeafReader(
                false, getOnlyLeafReader(sidecarReader), getOnlyLeafReader(mainReader))) {
      // expected: IAE or a correctly sorted segment; actual: docID=0 sorts after docID=1
      try (IndexWriter w = new IndexWriter(target, new IndexWriterConfig().setIndexSort(SORT))) {
        w.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
      } catch (IllegalArgumentException rejected) {
        return;
      }
      try (CheckIndex checker = new CheckIndex(target)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        assertNull(checker.checkIndex().segmentInfos.get(0).indexSortStatus.error);
      }
    }
  }
}

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
package org.apache.lucene.misc.index;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestReorderPre99Blocks extends LuceneTestCase {

  /** blocks.9.8.0.zip: written by Lucene 9.8.0, 3 blocks (child cN, parent pN), 2 segments. */
  public void testReorderingMergeKeepsPre99Blocks() throws Exception {
    Path path = createTempDir();
    TestUtil.unzip(getClass().getResourceAsStream("blocks.9.8.0.zip"), path);
    BPIndexReorderer reorderer = new BPIndexReorderer();
    reorderer.setMinDocFreq(1);
    reorderer.setMinPartitionSize(2);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer));
    try (Directory dir = FSDirectory.open(path)) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        StoredFields stored = leaf.storedFields();
        List<String> blocks = new ArrayList<>();
        for (int doc = 0; doc < leaf.maxDoc(); doc += 2) {
          blocks.add(stored.document(doc).get("id") + stored.document(doc + 1).get("id"));
        }
        Collections.sort(blocks);
        // expected: each child cN still directly before its parent pN; actual: blocks torn apart
        assertEquals(List.of("c0p0", "c1p1", "c2p2"), blocks);
      }
    }
  }
}

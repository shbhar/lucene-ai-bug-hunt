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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * blocks.9.8.0.zip was written by Lucene 9.8.0 (fixture-src/MakeBlockIndex.java): 120 parent/child blocks (377 documents, 257 of them children)
 * added with IndexWriter#addDocuments (no parent field, which 9.8 does not have), in 3 segments. Each child
 * has id "bNcJ" and its parent has id "bN". blocks.9.9.2.zip holds the same documents written by 9.9.2.
 *
 * <p>IndexWriter only calls OneMerge#reorder when it can keep blocks together, i.e. when there are no blocks
 * or a parent field identifies them (GITHUB#13128). Segments written before 9.9 do not record that they
 * contain blocks (LeafMetaData#hasBlocks is false), so the check lets BPReorderingMergePolicy renumber the
 * documents of this index and its blocks are silently torn apart.
 */
public class TestReorderPre99Blocks extends LuceneTestCase {

  private Path unzipFixture(String version) throws Exception {
    Path dir = createTempDir("blocks" + version);
    try (InputStream in = getClass().getResourceAsStream("blocks." + version + ".zip");
        ZipInputStream zis = new ZipInputStream(in)) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        Files.copy(zis, dir.resolve(e.getName()));
      }
    }
    return dir;
  }

  /** Number of child documents that are not immediately followed by their siblings and their parent. */
  private static int brokenChildren(LeafReader leaf) throws Exception {
    StoredFields sf = leaf.storedFields();
    int broken = 0;
    for (int doc = 0; doc < leaf.maxDoc(); doc++) {
      String id = sf.document(doc).get("id");
      if (id.indexOf('c') < 0) continue;
      int next = doc + 1;
      while (next < leaf.maxDoc() && sf.document(next).get("id").indexOf('c') >= 0) next++;
      String parent = id.substring(0, id.indexOf('c'));
      if (next == leaf.maxDoc() || parent.equals(sf.document(next).get("id")) == false) broken++;
    }
    return broken;
  }

  /** Fails: the index was written by 9.8.0, which did not record LeafMetaData#hasBlocks. */
  public void testReorderingMergeKeepsPre99Blocks() throws Exception {
    reorderingForceMergeKeepsBlocks("9.8.0");
  }

  /** Control, passes: the same documents written by 9.9.2, which records hasBlocks, are not reordered. */
  public void testReorderingMergeKeeps992Blocks() throws Exception {
    reorderingForceMergeKeepsBlocks("9.9.2");
  }

  private void reorderingForceMergeKeepsBlocks(String version) throws Exception {
    try (Directory dir = newFSDirectory(unzipFixture(version))) {
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(3, r.leaves().size());
        for (var ctx : r.leaves()) assertEquals(0, brokenChildren(ctx.reader()));
      }
      BPIndexReorderer reorderer = new BPIndexReorderer();
      reorderer.setMinPartitionSize(2);
      reorderer.setMinDocFreq(1);
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        // the merged segment does not record that it holds blocks either (printed for the record)
        System.out.println("merged leaf hasBlocks=" + leaf.getMetaData().hasBlocks());
        assertEquals("children separated from their parent after the merge", 0, brokenChildren(leaf));
      }
    }
  }
}

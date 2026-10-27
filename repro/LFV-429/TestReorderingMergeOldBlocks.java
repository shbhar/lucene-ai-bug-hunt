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
import java.nio.file.Path;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BitSet;

/**
 * A forced merge through BPReorderingMergePolicy must not break document blocks (children
 * followed by their parent, added with IndexWriter#addDocuments) when the blocks cannot be
 * identified with a parent field. IndexWriter skips OneMerge#reorder in that case
 * ("Don't reorder if blocks can't be identified using the parent field.").
 *
 * <p>Fixtures: the same 200 blocks (584 docs, 384 children, several segments), written once by
 * Lucene 9.8.0 and once by Lucene 9.9.2 with BlockFixtureGen (plain public API, no parent
 * field). Only the 9.9.2 segments record LeafMetaData#hasBlocks.
 */
public class TestReorderingMergeOldBlocks extends LuceneTestCase {

  private static final int NUM_BLOCKS = 200;

  public void testForceMergeKeepsBlocksWrittenBy980() throws Exception {
    checkForceMergeKeepsBlocks("v1-blocks-9.8.0.zip", "9.8.0");
  }

  public void testForceMergeKeepsBlocksWrittenBy992() throws Exception {
    checkForceMergeKeepsBlocks("v1-blocks-9.9.2.zip", "9.9.2");
  }

  public void testAddIndexesKeepsBlocksWrittenBy980() throws Exception {
    checkAddIndexesKeepsBlocks("v1-blocks-9.8.0.zip", "9.8.0");
  }

  public void testAddIndexesKeepsBlocksWrittenBy992() throws Exception {
    checkAddIndexesKeepsBlocks("v1-blocks-9.9.2.zip", "9.9.2");
  }

  /** Second guard: IndexWriter#addIndexes(CodecReader...) also calls OneMerge#reorder. */
  private void checkAddIndexesKeepsBlocks(String resource, String writtenBy) throws Exception {
    Path src = createTempDir("v1-src-" + writtenBy);
    try (InputStream in = TestReorderingMergeOldBlocks.class.getResourceAsStream(resource)) {
      assertNotNull("missing fixture " + resource, in);
      TestUtil.unzip(in, src);
    }
    try (Directory srcDir = FSDirectory.open(src);
        DirectoryReader srcReader = DirectoryReader.open(srcDir);
        Directory dstDir = FSDirectory.open(createTempDir("v1-dst-" + writtenBy))) {
      assertEquals(0, countChildrenOutsideTheirBlock(srcReader));
      CodecReader[] leaves = new CodecReader[srcReader.leaves().size()];
      for (int i = 0; i < leaves.length; i++) {
        leaves[i] = (CodecReader) srcReader.leaves().get(i).reader();
      }
      BPIndexReorderer reorderer = new BPIndexReorderer();
      reorderer.setMinDocFreq(2);
      reorderer.setMinPartitionSize(8);
      IndexWriterConfig iwc = new IndexWriterConfig();
      // addIndexes only accepts segments of the same created major (IndexWriterConfig javadoc)
      iwc.setIndexCreatedVersionMajor(9);
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergeScheduler(new SerialMergeScheduler());
      iwc.setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer));
      try (IndexWriter w = new IndexWriter(dstDir, iwc)) {
        w.addIndexes(leaves);
        w.commit();
      }
      TestUtil.checkIndex(dstDir);
      try (DirectoryReader reader = DirectoryReader.open(dstDir)) {
        int outside = countChildrenOutsideTheirBlock(reader);
        int wrongJoins = countWrongBlockJoinResults(reader);
        StringBuilder sb = new StringBuilder();
        for (LeafReaderContext ctx : reader.leaves()) {
          SegmentReader sr = (SegmentReader) ctx.reader();
          sb.append(' ')
              .append(sr.maxDoc())
              .append(":bp.reordered=")
              .append(sr.getSegmentInfo().info.getDiagnostics().get("bp.reordered"))
              .append(",hasBlocks=")
              .append(sr.getMetaData().hasBlocks());
        }
        System.out.println(
            "["
                + writtenBy
                + "] after addIndexes(CodecReader...): segments"
                + sb
                + " childrenOutsideTheirBlock="
                + outside
                + " wrongBlockJoinResults="
                + wrongJoins
                + "/"
                + NUM_BLOCKS);
        assertEquals(
            "children whose next parent is not their own parent after addIndexes", 0, outside);
        assertEquals(
            "ToParentBlockJoinQuery(childof:b) results that are not exactly parent b",
            0,
            wrongJoins);
      }
    }
  }

  private void checkForceMergeKeepsBlocks(String resource, String writtenBy) throws Exception {
    Path path = createTempDir("v1-" + writtenBy);
    try (InputStream in = TestReorderingMergeOldBlocks.class.getResourceAsStream(resource)) {
      assertNotNull("missing fixture " + resource, in);
      TestUtil.unzip(in, path);
    }
    try (Directory dir = FSDirectory.open(path)) {
      // Preconditions: the fixture is what we think it is and its blocks are intact.
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertTrue("need several segments", reader.leaves().size() > 1);
        StringBuilder sb = new StringBuilder();
        for (LeafReaderContext ctx : reader.leaves()) {
          SegmentReader sr = (SegmentReader) ctx.reader();
          assertEquals(writtenBy, sr.getSegmentInfo().info.getMinVersion().toString());
          assertNull(sr.getFieldInfos().getParentField());
          sb.append(' ').append(sr.maxDoc()).append(":hasBlocks=").append(sr.getMetaData().hasBlocks());
        }
        System.out.println("[" + writtenBy + "] before: segments" + sb);
        assertEquals(0, countChildrenOutsideTheirBlock(reader));
        assertEquals(0, countWrongBlockJoinResults(reader));
      }

      BPIndexReorderer reorderer = new BPIndexReorderer();
      reorderer.setMinDocFreq(2);
      reorderer.setMinPartitionSize(8);
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergeScheduler(new SerialMergeScheduler());
      iwc.setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
        w.commit();
      }
      // CheckIndex does not know about blocks; it should be clean either way.
      TestUtil.checkIndex(dir);

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        SegmentReader sr = (SegmentReader) reader.leaves().get(0).reader();
        int outside = countChildrenOutsideTheirBlock(reader);
        int wrongJoins = countWrongBlockJoinResults(reader);
        System.out.println(
            "["
                + writtenBy
                + "] after forceMerge(1): bp.reordered="
                + sr.getSegmentInfo().info.getDiagnostics().get("bp.reordered")
                + " hasBlocks="
                + sr.getMetaData().hasBlocks()
                + " childrenOutsideTheirBlock="
                + outside
                + " wrongBlockJoinResults="
                + wrongJoins
                + "/"
                + NUM_BLOCKS);
        assertEquals(
            "children whose next parent is not their own parent after the forced merge",
            0,
            outside);
        assertEquals(
            "ToParentBlockJoinQuery(childof:b) results that are not exactly parent b",
            0,
            wrongJoins);
      }
    }
  }

  /** Block-join semantics: the parent of a child is the next parent doc in the segment. */
  private static int countChildrenOutsideTheirBlock(DirectoryReader reader) throws Exception {
    QueryBitSetProducer parentsFilter =
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    int bad = 0;
    for (LeafReaderContext ctx : reader.leaves()) {
      LeafReader leaf = ctx.reader();
      BitSet parents = parentsFilter.getBitSet(ctx);
      long[] blockIds = new long[leaf.maxDoc()];
      NumericDocValues dv = leaf.getNumericDocValues("blockid");
      for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
        blockIds[doc] = dv.longValue();
      }
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        if (parents.get(doc)) {
          continue;
        }
        int parent = doc + 1 < leaf.maxDoc() ? parents.nextSetBit(doc + 1) : -1;
        if (parent < 0 || parent == DocIdSetIterator.NO_MORE_DOCS || blockIds[parent] != blockIds[doc]) {
          bad++;
        }
      }
    }
    return bad;
  }

  /** User-visible check: joining the children of block b must return exactly parent b. */
  private static int countWrongBlockJoinResults(DirectoryReader reader) throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    QueryBitSetProducer parentsFilter =
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    int wrong = 0;
    for (int b = 0; b < NUM_BLOCKS; b++) {
      ToParentBlockJoinQuery q =
          new ToParentBlockJoinQuery(
              new TermQuery(new Term("childof", Integer.toString(b))), parentsFilter, ScoreMode.None);
      TopDocs td = searcher.search(q, 10);
      boolean ok = td.scoreDocs.length == 1 && blockIdOf(reader, td.scoreDocs[0]) == b;
      if (ok == false) {
        wrong++;
      }
    }
    return wrong;
  }

  private static long blockIdOf(DirectoryReader reader, ScoreDoc sd) throws Exception {
    for (LeafReaderContext ctx : reader.leaves()) {
      if (sd.doc >= ctx.docBase && sd.doc < ctx.docBase + ctx.reader().maxDoc()) {
        NumericDocValues dv = ctx.reader().getNumericDocValues("blockid");
        assertTrue(dv.advanceExact(sd.doc - ctx.docBase));
        return dv.longValue();
      }
    }
    throw new AssertionError("doc not found: " + sd.doc);
  }
}

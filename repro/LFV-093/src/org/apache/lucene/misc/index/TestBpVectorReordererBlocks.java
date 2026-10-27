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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter only lets a merge reorder doc IDs when blocks can be identified by the parent field,
 * so that the reordering can keep each block together with its parent last (#13128). With
 * BPReorderingMergePolicy and BpVectorReorderer, a merge of an index whose child documents carry
 * vectors moves parents and children independently, so blocks no longer hold together.
 */
public class TestBpVectorReordererBlocks extends LuceneTestCase {

  private static final int NUM_BLOCKS = 64;

  private void indexBlocks(IndexWriter w) throws Exception {
    Random r = new Random(42);
    for (int b = 0; b < NUM_BLOCKS; b++) {
      List<Document> block = new ArrayList<>();
      for (int c = 0; c < 2; c++) {
        Document child = new Document();
        child.add(new StringField("id", "b" + b + "c" + c, Field.Store.YES));
        child.add(new StringField("type", "child", Field.Store.NO));
        child.add(new StringField("text", "t" + r.nextInt(8), Field.Store.NO));
        child.add(
            new KnnFloatVectorField(
                "vec", new float[] {(float) r.nextGaussian(), (float) r.nextGaussian()}));
        block.add(child);
      }
      Document parent = new Document();
      parent.add(new StringField("id", "b" + b + "p", Field.Store.YES));
      parent.add(new StringField("type", "parent", Field.Store.NO));
      parent.add(new StringField("text", "t" + r.nextInt(8), Field.Store.NO));
      block.add(parent);
      w.addDocuments(block);
      if (b % 16 == 15) {
        w.commit(); // several segments, so that forceMerge runs a real merge
      }
    }
  }

  private IndexWriterConfig config(IndexReorderer reorderer) {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setParentField("_parent")
        .setMergePolicy(new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer))
        .setMergeScheduler(new SerialMergeScheduler());
  }

  /** Every child must be followed, inside its segment, by the parent of its own block. */
  private void assertBlocksIntact(Directory dir, boolean requireReordered) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(reader);
      if (requireReordered) {
        assertEquals(
            "the merge must have been reordered for this test to be meaningful",
            "true",
            ((SegmentReader) leaf).getSegmentInfo().info.getDiagnostics().get("bp.reordered"));
      }
      NumericDocValues parents = leaf.getNumericDocValues("_parent");
      StoredFields storedFields = leaf.storedFields();
      List<String> pendingChildren = new ArrayList<>();
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        String id = storedFields.document(doc).get("id");
        if (parents.advanceExact(doc)) {
          String block = id.substring(0, id.length() - 1);
          for (String child : pendingChildren) {
            assertTrue(
                "child " + child + " ended up in the block of parent " + id,
                child.startsWith(block + "c"));
          }
          assertTrue("parent " + id, id.endsWith("p"));
          assertEquals("parent " + id + " has the wrong number of children", 2, pendingChildren.size());
          pendingChildren.clear();
        } else {
          pendingChildren.add(id);
        }
      }
      assertTrue(pendingChildren.isEmpty());

      // what a block-join user sees: the parent of child b5c0 must be b5p
      IndexSearcher searcher = newSearcher(reader);
      QueryBitSetProducer parentsFilter =
          new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
      TopDocs hits =
          searcher.search(
              new ToParentBlockJoinQuery(
                  new TermQuery(new Term("id", "b5c0")), parentsFilter, ScoreMode.None),
              10);
      assertEquals(1, hits.totalHits.value());
      assertEquals("b5p", searcher.storedFields().document(hits.scoreDocs[0].doc).get("id"));
    }
  }

  public void testBpVectorReordererKeepsBlocks() throws Exception {
    try (Directory dir = newDirectory()) {
      BpVectorReorderer reorderer = new BpVectorReorderer("vec");
      reorderer.setMinPartitionSize(2);
      try (IndexWriter w = new IndexWriter(dir, config(reorderer))) {
        indexBlocks(w);
        w.forceMerge(1);
      }
      // blocks must hold together whether or not the merge chose to reorder
      assertBlocksIntact(dir, false);
    }
  }

  /** Control: the postings-based BPIndexReorderer keeps blocks together (#13125). */
  public void testBPIndexReordererKeepsBlocks() throws Exception {
    try (Directory dir = newDirectory()) {
      BPIndexReorderer reorderer = new BPIndexReorderer();
      reorderer.setMinDocFreq(2);
      reorderer.setMinPartitionSize(2);
      try (IndexWriter w = new IndexWriter(dir, config(reorderer))) {
        indexBlocks(w);
        w.forceMerge(1);
      }
      assertBlocksIntact(dir, true);
    }
  }
}

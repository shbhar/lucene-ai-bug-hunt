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
package org.apache.lucene.util.bkd;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PointValues.IntersectVisitor;
import org.apache.lucene.index.PointValues.PointTree;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Random navigation of a PointTree is allowed since LUCENE-10262 (moveToChild/clone may follow
 * moveToParent). Visiting a leaf, moving to its parent and then visiting the parent (or moving to
 * a child again) trips an assertion in the default points format's BKD reader.
 */
public class TestBKDPointTreeVisitLeafThenParent extends LuceneTestCase {

  private static final class Counter implements IntersectVisitor {
    long count;

    @Override
    public void visit(int docID) {
      count++;
    }

    @Override
    public void visit(int docID, byte[] packedValue) {
      count++;
    }

    @Override
    public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
      return Relation.CELL_CROSSES_QUERY;
    }
  }

  public void testVisitLeafThenParent() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      final int numDocs = 1024; // two leaves of 512 points (the default leaf size)
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new IntPoint("f", i));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        PointValues values = r.leaves().get(0).reader().getPointValues("f");
        PointTree tree = values.getPointTree();

        assertTrue(tree.moveToChild()); // first leaf
        Counter leaf = new Counter();
        tree.visitDocIDs(leaf);
        assertEquals(tree.size(), leaf.count);

        assertTrue(tree.moveToParent()); // back at the root
        Counter root = new Counter();
        tree.visitDocIDs(root); // AssertionError in BKDPointTree.resetNodeDataPosition with -ea
        assertEquals(numDocs, root.count);
        assertTrue(tree.moveToChild());
      }
    }
  }
}

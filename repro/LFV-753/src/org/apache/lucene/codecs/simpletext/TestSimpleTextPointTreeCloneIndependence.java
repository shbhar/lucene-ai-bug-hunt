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
package org.apache.lucene.codecs.simpletext;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PointValues.PointTree;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * PointTree#clone: "Clone, the current node becomes the root of the new tree." With the SimpleText
 * points format, a clone shares one split-value stack entry with the tree it was cloned from, so
 * moving the original changes the bounds the clone reports after it moves back to its root.
 */
public class TestSimpleTextPointTreeCloneIndependence extends LuceneTestCase {

  public void testMovingOriginalDoesNotChangeClone() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(new SimpleTextCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // 2048 single-valued docs: four leaves of 512 points (the SimpleText default leaf size)
        for (int i = 0; i < 2048; i++) {
          Document doc = new Document();
          doc.add(new IntPoint("f", i));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        PointValues values = r.leaves().get(0).reader().getPointValues("f");
        PointTree tree = values.getPointTree();
        byte[] rootMin = tree.getMinPackedValue().clone();
        byte[] rootMax = tree.getMaxPackedValue().clone();

        // ordinary navigation: down and back up (the tree is at the root again)
        assertTrue(tree.moveToChild());
        assertTrue(tree.moveToParent());

        // clone at the root, and walk the clone down
        PointTree clone = tree.clone();
        assertTrue(clone.moveToChild());

        // walk the original: left child, then its right sibling
        assertTrue(tree.moveToChild());
        assertTrue(tree.moveToSibling());

        // the clone goes back to its root: it must report the root bounds again
        assertTrue(clone.moveToParent());
        assertArrayEquals("clone min at its root", rootMin, clone.getMinPackedValue());
        assertArrayEquals(
            "clone max at its root (expected "
                + IntPoint.decodeDimension(rootMax, 0)
                + ", got "
                + IntPoint.decodeDimension(clone.getMaxPackedValue(), 0)
                + ")",
            rootMax,
            clone.getMaxPackedValue());
      }
    }
  }
}

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
import org.apache.lucene.index.PointValues.PointTree;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleTextPointTreeCloneIndependence extends LuceneTestCase {

  public void testMovingOriginalDoesNotChangeClone() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(new SimpleTextCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 1024; i++) { // two leaves of 512 points
        Document doc = new Document();
        doc.add(new IntPoint("f", i));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        PointTree tree = getOnlyLeafReader(r).getPointValues("f").getPointTree();
        assertTrue(tree.moveToChild());
        assertTrue(tree.moveToParent());
        PointTree clone = tree.clone();
        assertTrue(clone.moveToChild());
        assertTrue(tree.moveToChild());
        assertTrue(tree.moveToSibling());
        assertTrue(clone.moveToParent());
        // expected: the clone's root bounds max 1023 again; actual: 0 (overwritten by the original)
        assertEquals(1023, IntPoint.decodeDimension(clone.getMaxPackedValue(), 0));
      }
    }
  }
}

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * With a parent field configured, IndexWriter lets a merge reorder doc IDs because the blocks can
 * then be kept together (#13128). A merge through BPReorderingMergePolicy with BpVectorReorderer
 * must therefore leave every block contiguous, parent last.
 */
public class TestBpVectorMergeBlocks extends LuceneTestCase {

  /** The children carry the vector, the parents do not (the layout of nested kNN search). */
  public void testVectorsOnChildren() throws IOException {
    doTestMergeKeepsBlocks(true);
  }

  /** The parents carry the vector, the children do not. */
  public void testVectorsOnParents() throws IOException {
    doTestMergeKeepsBlocks(false);
  }

  private void doTestMergeKeepsBlocks(boolean vectorsOnChildren) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setParentField("_parent")
              .setMergePolicy(
                  new BPReorderingMergePolicy(
                      new LogDocMergePolicy(), new BpVectorReorderer("vec")));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int b = 0; b < 4; b++) {
          List<Document> block = new ArrayList<>();
          block.add(doc("b" + b + "c0", vectorsOnChildren, b, 0));
          block.add(doc("b" + b + "c1", vectorsOnChildren, b, 1));
          block.add(doc("b" + b + "p", vectorsOnChildren == false, b, 2));
          w.addDocuments(block); // two children, then their parent
          if (b == 1) {
            w.commit(); // two segments, so that forceMerge(1) really merges
          }
        }
        w.forceMerge(1);
      }
      TestUtil.checkIndex(dir); // passes: CheckIndex does not notice anything

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        StoredFields storedFields = leaf.storedFields();
        List<String> order = new ArrayList<>();
        List<String> wrongParent = new ArrayList<>();
        List<String> pendingChildren = new ArrayList<>();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          String id = storedFields.document(doc).get("id");
          order.add(id);
          if (id.endsWith("p")) {
            // a block join gives each child the first parent that follows it
            String blockPrefix = id.substring(0, id.length() - 1) + "c";
            for (String child : pendingChildren) {
              if (child.startsWith(blockPrefix) == false) {
                wrongParent.add(child + "->" + id);
              }
            }
            pendingChildren.clear();
          } else {
            pendingChildren.add(id);
          }
        }
        for (String child : pendingChildren) {
          wrongParent.add(child + "->none"); // after the last parent: no parent at all
        }
        assertEquals("doc order after the merge: " + order, List.of(), wrongParent);
      }
    }
  }

  private static Document doc(String id, boolean withVector, int b, int c) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (withVector) {
      doc.add(new KnnFloatVectorField("vec", new float[] {b + 1, c + 1}));
    }
    return doc;
  }
}

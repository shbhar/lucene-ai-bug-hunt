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
package org.apache.lucene.search.join;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.FixedBitSet;

public class TestBlockJoinSelectorFirstAdvance extends LuceneTestCase {

  // Expected: advance(1) stops at parent 2 with its children's minimum, 1. Actual: the value is 5.
  public void testAdvanceAsFirstCall() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document child0 = new Document();
      child0.add(new NumericDocValuesField("price", 1));
      Document child1 = new Document();
      child1.add(new NumericDocValuesField("price", 5));
      w.addDocuments(List.of(child0, child1, new Document()));

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        FixedBitSet parents = new FixedBitSet(3);
        parents.set(2);
        FixedBitSet children = new FixedBitSet(3);
        children.set(0, 2);

        NumericDocValues mins =
            BlockJoinSelector.wrap(
                leaf.getNumericDocValues("price"),
                BlockJoinSelector.Type.MIN,
                parents,
                new BitSetIterator(children, 2));

        assertEquals(2, mins.advance(1));
        assertEquals(1, mins.longValue());
      }
    }
  }
}

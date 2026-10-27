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
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/**
 * The single-valued view of a SortedSetDocValues field (DocValues.singleton, returned by
 * DocValues.getSortedSet for a segment where every document has at most one value) caches the
 * ordinal of the current document. intoBitSet moves the iterator to the first document at or after
 * upTo but does not refresh that cache, so nextOrd() then returns the ordinal of an earlier document.
 */
public class TestSingletonSortedSetIntoBitSetStaleOrdRootCause extends LuceneTestCase {

  /** Root cause: nextOrd() after intoBitSet() returns the previous document's ordinal. */
  public void testNextOrdAfterIntoBitSet() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String v : new String[] {"a", "b"}) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesField("f", new BytesRef(v)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        SortedSetDocValues values = DocValues.getSortedSet(leaf, "f");
        long ordB = values.lookupTerm(new BytesRef("b"));
        assertEquals(0, values.nextDoc());
        values.intoBitSet(1, new FixedBitSet(2), 0); // loads doc 0, leaves the iterator on doc 1
        assertEquals(1, values.docID());
        assertEquals(1, values.docValueCount());
        assertEquals("ordinal of doc 1 after intoBitSet", ordB, values.nextOrd());
      }
    }
  }
}

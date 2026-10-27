/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DocsWithFieldSet (used by the doc-values, norms and vectors writers, including the flat-vectors
 * merge) switches to a FixedBitSet once doc IDs stop being dense and then grows it with
 * FixedBitSet#ensureCapacity. For a doc ID above about 1.91 billion the grown set gets a negative
 * length, after which the set's iterator returns no document at all.
 */
public class TestDocsWithFieldSetLargeDocIds extends LuceneTestCase {

  private static void check(int bigDoc) throws IOException {
    DocsWithFieldSet set = new DocsWithFieldSet();
    set.add(0);
    set.add(2); // gap: switch to the bit-set encoding
    set.add(bigDoc);
    assertEquals(3, set.cardinality());
    DocIdSetIterator it = set.iterator();
    assertEquals(0, it.nextDoc());
    assertEquals(2, it.nextDoc());
    assertEquals(bigDoc, it.nextDoc());
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc());
  }

  /** Control. Passes. */
  public void testDocId1_9Billion() throws IOException {
    check(1_900_000_000);
  }

  /** A doc ID below IndexWriter.MAX_DOCS. Fails on main. */
  public void testDocId2Billion() throws IOException {
    check(2_000_000_000);
  }
}

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
package org.apache.lucene.facet;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * A {@link DrillDownQuery} and its {@link DrillDownQuery#clone()} must stay independent when a
 * value is OR'd into a dimension that both already constrain.
 *
 * <p>Index: three documents, color = red, blue and green.
 */
public class TestDrillDownQueryCloneIsolation extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;
  private final FacetsConfig config = new FacetsConfig();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (String color : new String[] {"red", "blue", "green"}) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("color", color));
      w.addDocument(config.build(doc));
    }
    reader = w.getReader();
    w.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, dir);
    super.tearDown();
  }

  private DrillDownQuery red() {
    DrillDownQuery q = new DrillDownQuery(config);
    q.add("color", "red");
    return q;
  }

  /** Adding blue to the clone must not change the original. */
  public void testAddToCloneLeavesOriginalAlone() throws Exception {
    DrillDownQuery original = red();
    DrillDownQuery clone = original.clone();
    clone.add("color", "blue");
    assertEquals("clone: red OR blue", 2, searcher.count(clone));
    assertEquals("original: red only", 1, searcher.count(original));
  }

  /** Adding blue to the original must not change a clone taken before. */
  public void testAddToOriginalLeavesEarlierCloneAlone() throws Exception {
    DrillDownQuery original = red();
    DrillDownQuery snapshot = original.clone();
    original.add("color", "blue");
    assertEquals("original: red OR blue", 2, searcher.count(original));
    assertEquals("snapshot: red only", 1, searcher.count(snapshot));
  }

  /**
   * A copy that was already searched hides the leak at first, and picks it up once its own
   * dimension changes again.
   */
  public void testSearchedOriginalPicksUpCloneValueLater() throws Exception {
    DrillDownQuery original = red();
    assertEquals(1, searcher.count(original));
    DrillDownQuery clone = original.clone();
    clone.add("color", "blue");
    assertEquals("clone: red OR blue", 2, searcher.count(clone));
    assertEquals("original: red only", 1, searcher.count(original));
    original.add("color", "green");
    assertEquals("original: red OR green", 2, searcher.count(original));
  }

  /** Two copies that match different documents must not be equal. */
  public void testCopiesMatchingDifferentDocumentsAreNotEqual() throws Exception {
    DrillDownQuery original = red();
    assertEquals(1, searcher.count(original));
    DrillDownQuery clone = original.clone();
    clone.add("color", "blue");
    assertEquals(1, searcher.count(original));
    assertEquals(2, searcher.count(clone));
    assertNotEquals("copies that match 1 and 2 documents", original, clone);
  }
}

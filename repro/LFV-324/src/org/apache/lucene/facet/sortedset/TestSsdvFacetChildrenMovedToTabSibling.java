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
package org.apache.lucene.facet.sortedset;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSsdvFacetChildrenMovedToTabSibling extends LuceneTestCase {

  public void testChildStaysUnderItsLabel() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("cat", true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("cat", "shoes", "red"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("cat", "shoes\t"));
        w.addDocument(config.build(d2));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesFacetCounts facets =
            new SortedSetDocValuesFacetCounts(new DefaultSortedSetDocValuesReaderState(r, config));
        LabelAndValue[] red = {new LabelAndValue("red", 1)};
        FacetResult expected = new FacetResult("cat", new String[] {"shoes"}, 1, red, 1);
        // expected: cat/shoes has the child red=1; actual: null (red moved to sibling "shoes\t")
        assertEquals(expected, facets.getTopChildren(10, "cat", "shoes"));
      }
    }
  }
}

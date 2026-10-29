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
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSsdvHierarchicalDimNotIndexed extends LuceneTestCase {

  public void testGetAllChildrenOfUnseenHierarchicalDim() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("category", true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new SortedSetDocValuesFacetField("author", "bob"));
        w.addDocument(config.build(doc));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(reader, config));
        // expected null (Facets javadoc: "dimension was never seen"); actual NullPointerException
        assertNull(facets.getAllChildren("category"));
      }
    }
  }
}

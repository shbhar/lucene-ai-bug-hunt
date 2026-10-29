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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortedSetDocValuesDimCountReservedChar extends LuceneTestCase {

  public void testDimNameWithDelimChar() throws Exception {
    String dim = "c\u001Fd";
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(dim, true);
    config.setRequireDimCount(dim, true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new SortedSetDocValuesFacetField(dim, "x"));
        doc.add(new SortedSetDocValuesFacetField(dim, "y"));
        w.addDocument(config.build(doc));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Facets facets =
            new SortedSetDocValuesFacetCounts(new DefaultSortedSetDocValuesReaderState(reader, config));
        // expected: children x and y; actual: only y (x's ordinal is read as the dimension's own)
        assertEquals("[x (1), y (1)]", Arrays.toString(facets.getTopChildren(10, dim).labelValues));
      }
    }
  }
}

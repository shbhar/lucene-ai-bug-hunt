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
package org.apache.lucene.search.grouping;

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestGroupFacetPrefixBinaryValues extends LuceneTestCase {

  public void testFacetPrefixWithBinaryFacetValue() throws Exception {
    byte[] binary = new byte[11];
    Arrays.fill(binary, (byte) 0xFF);
    binary[0] = 'p';
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (BytesRef facet : new BytesRef[] {new BytesRef("pa"), new BytesRef(binary)}) {
        Document doc = new Document();
        doc.add(new SortedDocValuesField("group", new BytesRef("g")));
        doc.add(new SortedDocValuesField("facet", facet));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        GroupedFacetResult result =
            new IndexSearcher(reader)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new TermGroupFacetCollectorManager(
                        "group", "facet", false, new BytesRef("p"), 10, 1, false));
        // expected 2 entries, "pa" and "p" + ten 0xFF bytes; -da gives 0, -ea an AssertionError
        assertEquals(2, result.getFacetEntries(0, 10).size());
      }
    }
  }
}

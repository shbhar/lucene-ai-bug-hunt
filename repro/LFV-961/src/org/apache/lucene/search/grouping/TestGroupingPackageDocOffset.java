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

import java.util.Collection;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestGroupingPackageDocOffset extends LuceneTestCase {

  public void testOffsetThreeOfTopTenGivesSevenGroups() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int g = 0; g < 13; g++) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("g", new BytesRef("g" + g)));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Collection<SearchGroup<BytesRef>> groups =
            new IndexSearcher(r)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new FirstPassGroupingCollectorManager<>(
                        () -> new TermGroupSelector("g"), Sort.INDEXORDER, 3, 10));
        // package-info: groupOffset 3 "means you'll get 7 groups back (assuming topNGroups is 10)"; actual 10
        assertEquals(7, groups.size());
      }
    }
  }
}

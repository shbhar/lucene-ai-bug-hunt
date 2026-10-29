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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestGroupReducerFinish extends LuceneTestCase {

  public void testPerGroupFacetsCollectorSeesItsHit() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new SortedDocValuesField("group", new BytesRef("a")));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SearchGroup<BytesRef> group = new SearchGroup<>();
        group.groupValue = new BytesRef("a");
        GroupReducer<BytesRef, FacetsCollector> reducer =
            new GroupReducer<>() {
              @Override
              public boolean needsScores() {
                return false;
              }

              @Override
              protected FacetsCollector newCollector() {
                return new FacetsCollector();
              }
            };
        new IndexSearcher(reader)
            .search(
                MatchAllDocsQuery.INSTANCE,
                new SecondPassGroupingCollector<>(
                    new TermGroupSelector("group"), List.of(group), reducer));
        // expected: group a's FacetsCollector reports 1 hit; actual: 0 (finish() is never called)
        assertEquals(
            1,
            reducer.getCollector(group.groupValue).getMatchingDocs().stream()
                .mapToInt(FacetsCollector.MatchingDocs::totalHits)
                .sum());
      }
    }
  }
}

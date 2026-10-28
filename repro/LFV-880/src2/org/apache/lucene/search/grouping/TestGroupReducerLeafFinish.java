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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A GroupReducer whose per-group collector does its per-segment work in LeafCollector#finish (here
 * the stock FacetsCollector, which records its MatchingDocs only in finish) must see every
 * document of its group, as the GroupReducer javadoc says each group's collector collects every
 * document in the group.
 */
public class TestGroupReducerLeafFinish extends LuceneTestCase {

  /** Runs one collector on a searcher without executor (a single slice, one collector). */
  private static <C extends Collector> void searchOnce(IndexSearcher searcher, C collector)
      throws IOException {
    searcher.search(
        new MatchAllDocsQuery(),
        new CollectorManager<C, Void>() {
          private boolean used;

          @Override
          public C newCollector() {
            assertFalse("single slice expected", used);
            used = true;
            return collector;
          }

          @Override
          public Void reduce(Collection<C> collectors) {
            return null;
          }
        });
  }

  public void testPerGroupFacetsCollectorSeesEveryDocument() throws Exception {
    String[] groupOfDoc = {"a", "b", "a", "b", "a"};
    Map<String, Integer> expected = Map.of("a", 3, "b", 2);
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < groupOfDoc.length; i++) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("group", new BytesRef(groupOfDoc[i])));
          w.addDocument(doc);
          if (i == 2) {
            w.flush(); // two segments
          }
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FirstPassGroupingCollector<BytesRef> firstPass =
            new FirstPassGroupingCollector<>(new TermGroupSelector("group"), Sort.INDEXORDER, 10);
        searchOnce(searcher, firstPass);
        Collection<SearchGroup<BytesRef>> topGroups = firstPass.getTopGroups(0);
        assertEquals(2, topGroups.size());

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
        SecondPassGroupingCollector<BytesRef> secondPass =
            new SecondPassGroupingCollector<>(new TermGroupSelector("group"), topGroups, reducer);
        searchOnce(searcher, secondPass);
        assertEquals(groupOfDoc.length, secondPass.totalGroupedHitCount);

        for (SearchGroup<BytesRef> group : topGroups) {
          int hits = 0;
          for (FacetsCollector.MatchingDocs md : reducer.getCollector(group.groupValue).getMatchingDocs()) {
            hits += md.totalHits;
          }
          assertEquals(
              "documents seen by the collector of group " + group.groupValue.utf8ToString(),
              (int) expected.get(group.groupValue.utf8ToString()),
              hits);
        }
      }
    }
  }
}

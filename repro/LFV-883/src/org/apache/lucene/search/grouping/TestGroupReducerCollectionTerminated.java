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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A GroupReducer whose per-group collector ends early with CollectionTerminatedException (here a
 * stock TopFieldCollector in index order that only needs the first hit of its group) must stop only
 * that group: the other groups' collectors must still see their documents, as IndexSearcher and
 * MultiCollector treat the exception as "this collector is done with this leaf".
 */
public class TestGroupReducerCollectionTerminated extends LuceneTestCase {

  private static <C extends Collector> void searchOnce(IndexSearcher searcher, C collector)
      throws IOException {
    searcher.search(
        MatchAllDocsQuery.INSTANCE,
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

  public void testEarlyTerminatingGroupDoesNotEndOtherGroups() throws Exception {
    String[] groupOfDoc = {"a", "a", "a", "b", "b"}; // one segment
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String g : groupOfDoc) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("group", new BytesRef(g)));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        FirstPassGroupingCollector<BytesRef> firstPass =
            new FirstPassGroupingCollector<>(new TermGroupSelector("group"), Sort.INDEXORDER, 10);
        searchOnce(searcher, firstPass);
        Collection<SearchGroup<BytesRef>> topGroups = firstPass.getTopGroups(0);
        assertEquals(2, topGroups.size());

        // per group: the first document in index order; the hit count may be a lower bound
        GroupReducer<BytesRef, TopFieldCollector> reducer =
            new GroupReducer<>() {
              @Override
              public boolean needsScores() {
                return false;
              }

              @Override
              protected TopFieldCollector newCollector() {
                return new TopFieldCollectorManager(Sort.INDEXORDER, 1, null, 1).newCollector();
              }
            };
        SecondPassGroupingCollector<BytesRef> secondPass =
            new SecondPassGroupingCollector<>(new TermGroupSelector("group"), topGroups, reducer);
        searchOnce(searcher, secondPass);

        TopDocs a = reducer.getCollector(new BytesRef("a")).topDocs();
        TopDocs b = reducer.getCollector(new BytesRef("b")).topDocs();
        assertEquals("first document of group a", 1, a.scoreDocs.length);
        assertEquals(0, a.scoreDocs[0].doc);
        assertEquals("first document of group b", 1, b.scoreDocs.length);
        assertEquals(3, b.scoreDocs[0].doc);
        assertEquals("documents seen by the second pass", groupOfDoc.length, secondPass.totalHitCount);
      }
    }
  }
}

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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * GroupingSearch#setIncludeMaxScore(true) with doc-block grouping and field sorts.
 *
 * <p>Two groups indexed as blocks; the query body:a matches docs 0, 1 (group 0) and 2 (group 1).
 * Two-pass grouping on the same groups (control) reports each group's best score and the overall
 * best score; doc-block grouping reports NaN.
 */
public class TestBlockGroupingSearchIncludeMaxScore8x extends LuceneTestCase {

  private static final String[][] GROUPS = {{"a", "a b"}, {"a a a", "b"}};

  private interface Body {
    void run(IndexSearcher searcher) throws Exception;
  }

  private void withSearcher(Body body) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        int id = 0;
        for (int g = 0; g < GROUPS.length; g++) {
          List<Document> block = new ArrayList<>();
          for (int i = 0; i < GROUPS[g].length; i++) {
            Document d = new Document();
            d.add(new TextField("body", GROUPS[g][i], Field.Store.NO));
            d.add(new SortedDocValuesField("group", new BytesRef("g" + g)));
            d.add(new NumericDocValuesField("id", id++));
            if (i == GROUPS[g].length - 1) {
              d.add(new StringField("groupEnd", "x", Field.Store.NO));
            }
            block.add(d);
          }
          w.addDocuments(block);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        body.run(new IndexSearcher(r));
      }
    }
  }

  private static final Query QUERY = new TermQuery(new Term("body", "a"));
  private static final Sort BY_ID = new Sort(new SortField("id", SortField.Type.INT));

  private static void check(IndexSearcher s, TopGroups<?> tg) throws Exception {
    TopDocs all = s.search(QUERY, 10);
    float best = all.scoreDocs[0].score;
    assertEquals("TopGroups.maxScore", best, tg.maxScore, 0f);
    assertEquals(2, tg.groups.length);
    for (GroupDocs<?> gd : tg.groups) {
      float groupBest = Float.NEGATIVE_INFINITY;
      for (ScoreDoc sd : gd.scoreDocs) {
        for (ScoreDoc hit : all.scoreDocs) {
          if (hit.doc == sd.doc) {
            groupBest = Math.max(groupBest, hit.score);
          }
        }
      }
      assertEquals("GroupDocs.maxScore", groupBest, gd.maxScore, 0f);
    }
  }

  /** Control: two-pass grouping honours setIncludeMaxScore(true) with the same sorts. */
  public void testTwoPassGroupingControl() throws Exception {
    withSearcher(
        s -> {
          GroupingSearch g = new GroupingSearch("group");
          g.setGroupSort(BY_ID).setSortWithinGroup(BY_ID).setIncludeMaxScore(true);
          g.setGroupDocsLimit(10);
          check(s, g.search(s, QUERY, 0, 10));
        });
  }

  /** Doc-block grouping with the same sorts and setIncludeMaxScore(true). */
  public void testBlockGrouping() throws Exception {
    withSearcher(
        s -> {
          GroupingSearch g = new GroupingSearch(new TermQuery(new Term("groupEnd", "x")));
          g.setGroupSort(BY_ID).setSortWithinGroup(BY_ID).setIncludeMaxScore(true);
          g.setGroupDocsLimit(10);
          check(s, g.search(s, QUERY, 0, 10));
        });
  }
}

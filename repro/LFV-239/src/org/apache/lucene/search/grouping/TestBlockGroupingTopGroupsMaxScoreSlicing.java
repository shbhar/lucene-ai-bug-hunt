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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * Block grouping (GroupingSearch(Query) -> BlockGroupingCollectorManager ->
 * TopGroups.mergeBlockGroups) computes TopGroups.maxScore as the maximum of the per-slice maxima,
 * and each slice's maximum covers all groupOffset+topN groups that slice kept, including groups
 * that the merge does not return. The value therefore depends on how the index is sliced:
 * sequential and concurrent search of the same query report different maxScore values, and the
 * concurrent value is neither the maximum over the returned groups nor over all hits.
 *
 * <p>Index: three one-document blocks. Segment 0: A (v=1, score 1), C (v=3, score 9). Segment 1:
 * B (v=2, score 5). Groups are sorted by v ascending; the top group is A. Two-pass term grouping
 * over the same documents reports maxScore 1.0 (the returned group's maximum).
 */
public class TestBlockGroupingTopGroupsMaxScoreSlicing extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private ExecutorService executor;
  private Query query;

  private static Document doc(String group, int v, int score) {
    Document d = new Document();
    d.add(new SortedDocValuesField("group", new BytesRef(group)));
    d.add(new NumericDocValuesField("v", v));
    d.add(new NumericDocValuesField("score", score));
    d.add(new StringField("groupEnd", "x", Field.Store.NO));
    return d;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocuments(List.of(doc("A", 1, 1)));
      w.addDocuments(List.of(doc("C", 3, 9)));
      w.flush();
      w.addDocuments(List.of(doc("B", 2, 5)));
      w.flush();
    }
    reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
    executor = Executors.newFixedThreadPool(2, new NamedThreadFactory("TestBlockMaxScore"));
    query =
        new FunctionScoreQuery(new MatchAllDocsQuery(), DoubleValuesSource.fromIntField("score"));
  }

  @Override
  public void tearDown() throws Exception {
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    reader.close();
    dir.close();
    super.tearDown();
  }

  private TopGroups<?> search(GroupingSearch gs, IndexSearcher searcher) throws Exception {
    gs.setGroupSort(new Sort(new SortField("v", SortField.Type.INT)));
    gs.setSortWithinGroup(Sort.RELEVANCE).setIncludeMaxScore(true);
    return gs.search(searcher, query, 0, 1);
  }

  public void testMaxScoreDoesNotDependOnSlicing() throws Exception {
    IndexSearcher sequential = new IndexSearcher(reader);
    // one slice per segment (public static IndexSearcher.slices with maxSegmentsPerSlice = 1)
    IndexSearcher concurrent =
        new IndexSearcher(reader, executor) {
          @Override
          protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
            return slices(leaves, 1, 1, false);
          }
        };
    assertEquals(2, concurrent.getSlices().length);

    TopGroups<?> term = search(new GroupingSearch("group"), sequential);
    TopGroups<?> seq = search(new GroupingSearch(new TermQuery(new Term("groupEnd", "x"))), sequential);
    TopGroups<?> conc = search(new GroupingSearch(new TermQuery(new Term("groupEnd", "x"))), concurrent);

    // the same single group (A, doc 0, maxScore 1.0) is returned in all three cases
    for (TopGroups<?> tg : List.of(term, seq, conc)) {
      assertEquals(1, tg.groups.length);
      assertEquals(0, tg.groups[0].scoreDocs()[0].doc);
      assertEquals(1f, tg.groups[0].maxScore(), 0f);
    }
    // Two defensible meanings: the maximum over the returned groups (1.0, what two-pass grouping
    // reports) or over all hits ('Highest score across all hits', 9.0). Either way it must not
    // depend on the slicing.
    String values =
        "term=" + term.maxScore + " sequential block=" + seq.maxScore + " concurrent block=" + conc.maxScore;
    assertTrue(values, conc.maxScore == 1f || conc.maxScore == 9f);
    assertEquals(values, seq.maxScore, conc.maxScore, 0f);
  }
}

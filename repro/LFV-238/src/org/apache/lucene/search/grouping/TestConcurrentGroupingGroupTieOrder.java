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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * With an executor, grouped search ranks groups that tie on the group sort by slice index instead
 * of by the doc ID of their top document. Sequential search returns the group whose top document
 * has the lowest doc ID (FirstPassGroupingCollector and BlockGroupingCollector break ties by doc
 * ID; Sort: "the internal Lucene docid is used to break it"); concurrent search returns another
 * group.
 *
 * <p>Index: 6 segments. Segment 0 holds one document (doc 0, group "g0"); segment k = 1..5 holds
 * two documents of group "gk". Each segment's documents form one block whose last document carries
 * groupEnd:x. IndexSearcher's default slicing sorts segments by size, so segments 1..5 form slice
 * 0 and segment 0 forms slice 1. All documents score 1.0 for MatchAllDocsQuery.
 */
public class TestConcurrentGroupingGroupTieOrder extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private ExecutorService executor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int[] segmentSizes = {1, 2, 2, 2, 2, 2};
      for (int seg = 0; seg < segmentSizes.length; seg++) {
        List<Document> block = new ArrayList<>();
        for (int i = 0; i < segmentSizes[seg]; i++) {
          Document d = new Document();
          d.add(new SortedDocValuesField("group", new BytesRef("g" + seg)));
          if (i == segmentSizes[seg] - 1) {
            d.add(new StringField("groupEnd", "x", Field.Store.NO));
          }
          block.add(d);
        }
        w.addDocuments(block);
        w.flush();
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(6, reader.leaves().size());
    executor = Executors.newFixedThreadPool(2, new NamedThreadFactory("TestGroupTieOrder"));
  }

  @Override
  public void tearDown() throws Exception {
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String topTermGroup(IndexSearcher searcher) throws Exception {
    GroupingSearch gs = new GroupingSearch("group"); // default sorts: Sort.RELEVANCE
    TopGroups<BytesRef> tg = gs.search(searcher, new MatchAllDocsQuery(), 0, 1);
    assertEquals(1, tg.groups.length);
    return tg.groups[0].groupValue().utf8ToString();
  }

  private int topBlockGroupFirstDoc(IndexSearcher searcher) throws Exception {
    GroupingSearch gs = new GroupingSearch(new TermQuery(new Term("groupEnd", "x")));
    TopGroups<?> tg = gs.search(searcher, new MatchAllDocsQuery(), 0, 1);
    assertEquals(1, tg.groups.length);
    return tg.groups[0].scoreDocs()[0].doc;
  }

  private static String topGroupFirstPassManager(IndexSearcher searcher) throws Exception {
    Collection<SearchGroup<BytesRef>> groups =
        searcher.search(
            new MatchAllDocsQuery(),
            new FirstPassGroupingCollectorManager<>(
                () -> new TermGroupSelector("group"), Sort.RELEVANCE, 0, 1));
    assertEquals(1, groups.size());
    return groups.iterator().next().groupValue.utf8ToString();
  }

  private void assertSlicing(IndexSearcher concurrent) {
    assertEquals(2, concurrent.getSlices().length);
    assertEquals("segment 0 is in the last slice", 0, concurrent.getSlices()[1].partitions[0].ctx.docBase);
  }

  /** Two-pass grouping: GroupingSearch(String) -> FirstPassGroupingCollectorManager -> SearchGroup.merge. */
  public void testTermGroupingTopGroupWithExecutor() throws Exception {
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    assertSlicing(concurrent);
    assertEquals("sequential", "g0", topTermGroup(new IndexSearcher(reader)));
    assertEquals("concurrent", "g0", topTermGroup(concurrent));
  }

  /** Same through the public first-pass manager (also present in 10.5.x). */
  public void testFirstPassManagerTopGroupWithExecutor() throws Exception {
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    assertSlicing(concurrent);
    assertEquals("sequential", "g0", topGroupFirstPassManager(new IndexSearcher(reader)));
    assertEquals("concurrent", "g0", topGroupFirstPassManager(concurrent));
  }

  /** Block grouping: GroupingSearch(Query) -> BlockGroupingCollectorManager -> mergeBlockGroups. */
  public void testBlockGroupingTopGroupWithExecutor() throws Exception {
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    assertSlicing(concurrent);
    assertEquals("sequential", 0, topBlockGroupFirstDoc(new IndexSearcher(reader)));
    assertEquals("concurrent", 0, topBlockGroupFirstDoc(concurrent));
  }
}

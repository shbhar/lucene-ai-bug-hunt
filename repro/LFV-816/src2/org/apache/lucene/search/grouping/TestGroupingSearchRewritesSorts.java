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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * GroupingSearch#search is handed the IndexSearcher but uses the group sort and the within-group
 * sort as given: it never calls Sort#rewrite(IndexSearcher), which IndexSearcher#search(Query,
 * int, Sort) does. Ordering groups (or the documents of one group) by a Sort must agree with a
 * search sorted by the same Sort on the same searcher.
 */
public class TestGroupingSearchRewritesSorts extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private final Query queryA = new TermQuery(new Term("body", "a"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      // doc 0: tf(a)=8 in a 10-term body, group g0; doc 1: tf(a)=1 in a 1-term body, group g1;
      // doc 2: same body as doc 1, group g0
      String[][] docs = {{"a a a a a a a a x x", "g0"}, {"a", "g1"}, {"a", "g0"}};
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new TextField("body", d[0], Field.Store.NO));
        doc.add(new SortedDocValuesField("group", new BytesRef(d[1])));
        doc.add(new SortedDocValuesField("one", new BytesRef("all")));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setSimilarity(new ClassicSimilarity());
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private Sort queryFunctionSort() {
    // descending by the score of body:a under the searcher's Similarity, ties by doc id
    return new Sort(
        new QueryValueSource(queryA, 0f).asDoubleValuesSource().getSortField(true),
        SortField.FIELD_DOC);
  }

  private String sortedSearch(Sort sort) throws Exception {
    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      sb.append("doc").append(sd.doc).append(Arrays.toString(((FieldDoc) sd).fields)).append(' ');
    }
    return sb.toString().trim();
  }

  /** Silent: the documents of one group come back in a different order than a sorted search. */
  public void testSortWithinGroupUsesTheSearchersSimilarity() throws Exception {
    Sort sort = queryFunctionSort();
    TopGroups<BytesRef> groups =
        new GroupingSearch("one")
            .setSortWithinGroup(sort)
            .setGroupDocsLimit(10)
            .search(searcher, new MatchAllDocsQuery(), 0, 10);
    assertEquals(1, groups.groups.length);
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : groups.groups[0].scoreDocs) {
      sb.append("doc").append(sd.doc).append(Arrays.toString(((FieldDoc) sd).fields)).append(' ');
    }
    assertEquals(sortedSearch(sort), sb.toString().trim());
  }

  /**
   * Silent: groups are ordered by sort values computed with the default Similarity instead of the
   * searcher's. The group of the best document of the sorted search must come first, with that
   * document's sort values.
   */
  public void testGroupSortUsesTheSearchersSimilarity() throws Exception {
    Sort sort = queryFunctionSort();
    TopDocs best = searcher.search(new MatchAllDocsQuery(), 1, sort);
    FieldDoc top = (FieldDoc) best.scoreDocs[0];
    TopGroups<BytesRef> groups =
        new GroupingSearch("group")
            .setGroupSort(sort)
            .setGroupDocsLimit(1)
            .search(searcher, new MatchAllDocsQuery(), 0, 10);
    String expected = "doc" + top.doc + Arrays.toString(top.fields);
    GroupDocs<BytesRef> first = groups.groups[0];
    String actual = "doc" + first.scoreDocs[0].doc + Arrays.toString(first.groupSortValues);
    assertEquals(expected, actual);
  }

  /** Loud: a ValueSource sort field (SortField.Type.REWRITEABLE) works with IndexSearcher. */
  public void testValueSourceGroupSort() throws Exception {
    Sort sort = new Sort(new QueryValueSource(queryA, 0f).getSortField(true), SortField.FIELD_DOC);
    String expected = sortedSearch(sort); // works: IndexSearcher rewrites the Sort
    TopGroups<BytesRef> groups =
        new GroupingSearch("group")
            .setGroupSort(sort)
            .setGroupDocsLimit(1)
            .search(searcher, new MatchAllDocsQuery(), 0, 10);
    assertNotNull(expected);
    assertEquals(2, groups.groups.length);
  }
}

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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingCollector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A negative first-pass cache limit is accepted, nothing is cached, but the cache still reports
 * isCached() == true, so the second pass replays nothing. An IllegalArgumentException for the
 * negative limit counts as a pass.
 */
public class TestGroupingSearchNegativeCacheLimit extends LuceneTestCase {

  private static final int DOCS = 20; // all match; author0: 7, author1: 7, author2: 6
  private static final Query QUERY = new TermQuery(new Term("body", "match"));

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", "match", Field.Store.NO));
        doc.add(new SortedDocValuesField("author", new BytesRef("author" + (i % 3))));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** For example "totalHitCount=20 [author0=7, author1=7, author2=6]" (hits per group). */
  private static String describe(TopGroups<BytesRef> topGroups) {
    StringBuilder sb = new StringBuilder("totalHitCount=" + topGroups.totalHitCount + " [");
    for (int i = 0; i < topGroups.groups.length; i++) {
      GroupDocs<BytesRef> group = topGroups.groups[i];
      sb.append(i == 0 ? "" : ", ").append(group.groupValue().utf8ToString());
      sb.append('=').append(group.totalHits().value());
    }
    return sb.append(']').toString();
  }

  private void assertSameAsWithoutCache(GroupingSearch cached, String setting) throws IOException {
    String expected = describe(new GroupingSearch("author").search(searcher, QUERY, 0, 10));
    assertTrue(expected, expected.startsWith("totalHitCount=" + DOCS + " "));
    String actual;
    try {
      actual = describe(cached.search(searcher, QUERY, 0, 10));
    } catch (IllegalArgumentException _) {
      return; // rejecting the negative limit is fine
    }
    assertEquals(setting, expected, actual);
  }

  public void testSetCachingMinusOne() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    try {
      groupingSearch.setCaching(-1, true);
    } catch (IllegalArgumentException _) {
      return; // rejecting the negative limit is fine
    }
    assertSameAsWithoutCache(groupingSearch, "setCaching(-1, true)");
  }

  public void testSetCachingInMBMinusOne() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    try {
      groupingSearch.setCachingInMB(-1.0, true);
    } catch (IllegalArgumentException _) {
      return; // rejecting the negative limit is fine
    }
    assertSameAsWithoutCache(groupingSearch, "setCachingInMB(-1.0, true)");
  }

  /**
   * Control: a limit of 0 overflows at the first hit, so the cache is purged and the query re-run.
   */
  public void testZeroLimitControl() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    groupingSearch.setCaching(0, true);
    assertSameAsWithoutCache(groupingSearch, "setCaching(0, true)");
  }

  /** Counts the documents it collects. */
  private static final class CountingCollector extends SimpleCollector {
    int count;

    @Override
    public void collect(int doc) {
      count++;
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  @SuppressWarnings("deprecation") // IndexSearcher#search(Query, Collector), to run on 10.x too
  public void testCachingCollectorMinusOne() throws IOException {
    CountingCollector firstPass = new CountingCollector();
    CachingCollector cache;
    try {
      cache = CachingCollector.create(firstPass, false, -1);
    } catch (IllegalArgumentException _) {
      return; // rejecting the negative limit is fine
    }
    searcher.search(QUERY, cache);
    assertEquals(DOCS, firstPass.count);
    if (cache.isCached()) {
      CountingCollector secondPass = new CountingCollector();
      cache.replay(secondPass);
      assertEquals(
          "isCached() is true, so replay() must deliver every collected document",
          DOCS,
          secondPass.count);
    }
  }
}

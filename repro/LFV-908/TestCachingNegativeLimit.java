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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingCollector;
import org.apache.lucene.search.CachingCollectorManager;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A negative cache limit (GroupingSearch#setCaching(int, boolean), #setCachingInMB(double,
 * boolean), CachingCollector#create, CachingCollectorManager) is accepted, nothing is cached, but
 * the cache still reports isCached() == true, so the second pass replays nothing: GroupingSearch
 * returns the groups with no documents and totalHitCount 0. CachingCollector uses
 * maxDocsToCache == -1 internally to mean "invalidated", but a negative limit given by the caller
 * never clears the cached flag.
 */
public class TestCachingNegativeLimit extends LuceneTestCase {

  private static final int DOCS = 20;

  private static Directory index() throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", "match", Field.Store.NO));
        doc.add(new SortedDocValuesField("author", new BytesRef("author" + (i % 3))));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static final Query QUERY = new TermQuery(new Term("body", "match"));

  private void assertGroupingSearchCorrect(GroupingSearch groupingSearch, String setting)
      throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopGroups<BytesRef> expected = new GroupingSearch("author").search(searcher, QUERY, 0, 10);
      assertEquals(DOCS, expected.totalHitCount);
      TopGroups<BytesRef> actual;
      try {
        actual = groupingSearch.search(searcher, QUERY, 0, 10);
      } catch (IllegalArgumentException e) {
        return; // rejecting the negative limit is fine
      }
      assertEquals(setting + ": totalHitCount", expected.totalHitCount, actual.totalHitCount);
      assertEquals(setting + ": number of groups", expected.groups.length, actual.groups.length);
      for (int i = 0; i < expected.groups.length; i++) {
        assertEquals(
            setting + ": documents of group " + expected.groups[i].groupValue().utf8ToString(),
            expected.groups[i].scoreDocs().length,
            actual.groups[i].scoreDocs().length);
      }
    }
  }

  public void testGroupingSearchNegativeMaxDocsToCache() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    try {
      groupingSearch.setCaching(-1, true);
    } catch (IllegalArgumentException e) {
      return; // rejecting the negative limit is fine
    }
    assertGroupingSearchCorrect(groupingSearch, "setCaching(-1, true)");
  }

  public void testGroupingSearchNegativeMaxCacheRAMMB() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    try {
      groupingSearch.setCachingInMB(-1.0, true);
    } catch (IllegalArgumentException e) {
      return; // rejecting the negative limit is fine
    }
    assertGroupingSearchCorrect(groupingSearch, "setCachingInMB(-1.0, true)");
  }

  /** Counts collected documents. */
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

  private static final class CountingManager
      implements CollectorManager<CountingCollector, Integer> {
    @Override
    public CountingCollector newCollector() {
      return new CountingCollector();
    }

    @Override
    public Integer reduce(Collection<CountingCollector> collectors) {
      int sum = 0;
      for (CountingCollector c : collectors) {
        sum += c.count;
      }
      return sum;
    }
  }

  public void testCachingCollectorManagerNegativeLimit() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      CachingCollectorManager<CountingCollector, Integer> caching;
      try {
        caching = new CachingCollectorManager<>(new CountingManager(), false, null, -1);
      } catch (IllegalArgumentException e) {
        return; // rejecting the negative limit is fine
      }
      try {
        assertEquals(Integer.valueOf(DOCS), searcher.search(QUERY, caching));
      } catch (IllegalArgumentException e) {
        return; // rejecting the negative limit is fine
      }
      if (caching.isCached()) {
        assertEquals(
            "isCached() is true, so replay must deliver every collected document",
            Integer.valueOf(DOCS),
            caching.replay(new CountingManager()));
      }
    }
  }

  @SuppressWarnings("deprecation")
  public void testCachingCollectorNegativeLimit() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      CountingCollector first = new CountingCollector();
      CachingCollector cache;
      try {
        cache = CachingCollector.create(first, false, -1);
      } catch (IllegalArgumentException e) {
        return; // rejecting the negative limit is fine
      }
      try {
        searcher.search(QUERY, cache);
      } catch (IllegalArgumentException e) {
        return; // rejecting the negative limit is fine
      }
      assertEquals(DOCS, first.count);
      if (cache.isCached()) {
        CountingCollector second = new CountingCollector();
        cache.replay(second);
        assertEquals(
            "isCached() is true, so replay must deliver every collected document",
            DOCS,
            second.count);
      }
    }
  }

  /** Not a negative limit: a limit of 0 already behaves (purged as soon as one doc matches). */
  public void testZeroLimitControl() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    groupingSearch.setCaching(0, true);
    assertGroupingSearchCorrect(groupingSearch, "setCaching(0, true)");
  }
}

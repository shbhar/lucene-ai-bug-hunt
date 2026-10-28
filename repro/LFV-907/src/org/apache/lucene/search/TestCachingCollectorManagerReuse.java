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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CachingCollectorManager keeps the CachingCollectors of every search it was used for and never
 * clears them, so after a second search isCached() and replay() describe both searches together.
 * Its javadoc does not say that a manager must not be reused (TopScoreDocCollectorManager and
 * TopFieldCollectorManager say "a new collectorManager should be created for each search"). The
 * delegate here keeps no state between searches, so reusing it is fine on its own.
 */
public class TestCachingCollectorManagerReuse extends LuceneTestCase {

  /** Collects the global ids of the documents it sees. */
  private static final class IdsCollector extends SimpleCollector {
    final List<Integer> ids = new ArrayList<>();
    private int docBase;

    @Override
    protected void doSetNextReader(LeafReaderContext context) {
      docBase = context.docBase;
    }

    @Override
    public void collect(int doc) {
      ids.add(docBase + doc);
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  /** A stateless manager: a new IdsCollector per slice, reduce = sorted union. */
  private static final class IdsManager implements CollectorManager<IdsCollector, List<Integer>> {
    @Override
    public IdsCollector newCollector() {
      return new IdsCollector();
    }

    @Override
    public List<Integer> reduce(Collection<IdsCollector> collectors) {
      List<Integer> all = new ArrayList<>();
      for (IdsCollector c : collectors) {
        all.addAll(c.ids);
      }
      all.sort(null);
      return all;
    }
  }

  public void testReuseReplaysOnlyTheLastSearch() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("k", i < 3 ? "a" : "b", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        CachingCollectorManager<IdsCollector, List<Integer>> caching =
            new CachingCollectorManager<>(new IdsManager(), false, null, 100);

        List<Integer> first = searcher.search(new TermQuery(new Term("k", "a")), caching);
        assertEquals(List.of(0, 1, 2), first);
        List<Integer> second = searcher.search(new TermQuery(new Term("k", "b")), caching);
        assertEquals(List.of(3, 4, 5, 6, 7, 8, 9), second); // the search result itself is right

        assertTrue(caching.isCached());
        List<Integer> replayed = caching.replay(new IdsManager());
        assertEquals(
            "replay after the second search should replay that search's hits", second, replayed);
      }
    }
  }
}

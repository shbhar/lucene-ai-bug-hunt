/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import java.util.Collection;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * End to end, default IndexWriterConfig with the default codec (pinned, since LuceneTestCase
 * randomizes Codec.getDefault()) and a plain IndexSearcher: one segment of IndexWriter.MAX_DOCS
 * documents, one of which has b:y. The query {@code +*:* -b:y} (FILTER MatchAllDocsQuery +
 * MUST_NOT, no scores) must match MAX_DOCS - 1 documents. It is executed by ReqExclBulkScorer over
 * a DenseConjunctionBulkScorer.
 *
 * <p>Runtime: about 2.1 billion addDocument calls (roughly 10 minutes on one core).
 */
public class TestMatchAllMustNotInMaxDocsSegment extends LuceneTestCase {

  public void testCountAllButOne() throws Exception {
    final int maxDocs = IndexWriter.MAX_DOCS;
    final int excluded = 12345;
    try (Directory dir = FSDirectory.open(createTempDir())) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document empty = new Document();
        Document ex = new Document();
        ex.add(new StringField("b", "y", Field.Store.NO));
        for (int doc = 0; doc < maxDocs; doc++) {
          w.addDocument(doc == excluded ? ex : empty);
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(maxDocs, r.maxDoc());
        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setQueryCache(null);
        Query q =
            new BooleanQuery.Builder()
                .add(MatchAllDocsQuery.INSTANCE, BooleanClause.Occur.FILTER)
                .add(new TermQuery(new Term("b", "y")), BooleanClause.Occur.MUST_NOT)
                .build();
        long count = searcher.search(q, new CountingManager());
        assertEquals(maxDocs - 1L, count);
      }
    }
  }

  private static final class CountingCollector extends SimpleCollector {
    long count;

    @Override
    public void collect(int doc) {
      count++;
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  private static final class CountingManager implements CollectorManager<CountingCollector, Long> {
    @Override
    public CountingCollector newCollector() {
      return new CountingCollector();
    }

    @Override
    public Long reduce(Collection<CountingCollector> collectors) throws IOException {
      long sum = 0;
      for (CountingCollector c : collectors) {
        sum += c.count;
      }
      return sum;
    }
  }
}

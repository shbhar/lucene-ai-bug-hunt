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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BlendedTermQuery is for "when several terms should produce identical scores, regardless of
 * their index statistics". It blends the shard's own statistics in rewrite() and passes them as
 * the docFreq/totalTermFreq arguments of IndexSearcher#termStats, the hook that "can be overridden
 * ... to return a term's statistics across a distributed collection". A distributed override (the
 * arithmetic of ShardSearchingTestBase: the passed local value plus the other shard's value) turns
 * the blended values back into per-term values.
 *
 * <p>Shard 0: doc 0 "apple", doc 1 "pear". Shard 1: three docs "pear".
 */
public class TestBlendedTermQueryDistributedStats extends LuceneTestCase {

  /** Shard 0's searcher; statistics are those of shard 0 plus shard 1. */
  static final class ShardSearcher extends IndexSearcher {
    private final IndexSearcher other;

    ShardSearcher(DirectoryReader local, IndexSearcher other) {
      super(local);
      this.other = other;
    }

    @Override
    public TermStats termStats(Term term, int docFreq, long totalTermFreq) throws IOException {
      TermStates ts = TermStates.build(other, term, true);
      return new TermStats(term.bytes(), docFreq + ts.docFreq(), totalTermFreq + ts.totalTermFreq());
    }

    @Override
    public FieldStats fieldStats(String field) throws IOException {
      FieldStats a = super.fieldStats(field), b = other.fieldStats(field);
      return new FieldStats(
          field,
          a.maxDoc() + b.maxDoc(),
          a.docCount() + b.docCount(),
          a.sumTotalTermFreq() + b.sumTotalTermFreq(),
          a.sumDocFreq() + b.sumDocFreq());
    }
  }

  private static Directory index(String... bodies) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      for (String b : bodies) {
        Document d = new Document();
        d.add(new TextField("body", b, Field.Store.NO));
        w.addDocument(d);
      }
    }
    return dir;
  }

  public void testBlendedTermsScoreAlikeWithDistributedStatistics() throws IOException {
    try (Directory d0 = index("apple", "pear");
        Directory d1 = index("pear", "pear", "pear");
        DirectoryReader r0 = DirectoryReader.open(d0);
        DirectoryReader r1 = DirectoryReader.open(d1)) {
      IndexSearcher shard = new ShardSearcher(r0, new IndexSearcher(r1));
      shard.setSimilarity(new BM25Similarity());
      Query q =
          new BlendedTermQuery.Builder()
              .add(new Term("body", "apple"))
              .add(new Term("body", "pear"))
              .build();
      float[] scores = new float[2];
      for (ScoreDoc sd : shard.search(q, 2).scoreDocs) {
        scores[sd.doc] = sd.score;
      }
      assertEquals("doc 'apple' vs doc 'pear'", scores[0], scores[1], 0f);
    }
  }
}

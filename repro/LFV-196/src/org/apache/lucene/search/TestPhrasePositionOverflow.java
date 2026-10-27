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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * PhraseQuery.Builder accepts any position >= 0 and MultiPhraseQuery.Builder any int position, but
 * ExactPhraseMatcher and SloppyPhraseMatcher add document positions and query offsets in int
 * arithmetic. With a query position near Integer.MAX_VALUE the sum overflows: the exact matcher loops
 * forever inside one document, and the sloppy matcher accepts a document whose real distance is
 * about 2^31.
 */
// the hanging search thread cannot be stopped; it is a daemon thread and is left behind on purpose
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestPhrasePositionOverflow extends LuceneTestCase {

  private static DirectoryReader index(Directory dir, Document doc) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  private static Document text(String s) {
    Document d = new Document();
    d.add(new TextField("f", s, Field.Store.NO));
    return d;
  }

  /** Runs count(q) in a daemon thread and fails if it does not return within 10 seconds. */
  private static int countWithin10s(IndexSearcher s, Query q) throws Exception {
    String what = q.getClass().getSimpleName() + " with positions " + java.util.Arrays.toString(((PhraseQuery) q).getPositions()) + " slop " + ((PhraseQuery) q).getSlop();
    ExecutorService ex =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "phrase-search");
              t.setDaemon(true);
              return t;
            });
    Future<Integer> f = ex.submit(() -> s.count(q));
    try {
      return f.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError("IndexSearcher#count(" + what + ") did not return within 10 s", e);
    } finally {
      ex.shutdown();
    }
  }

  /** Exact phrase: "a" at 0 and "b" at Integer.MAX_VALUE cannot match "x a b"; count must be 0. */
  public void testExactPhraseHugePositionTerminates() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("x a b"))) {
      IndexSearcher s = new IndexSearcher(r);
      PhraseQuery q =
          new PhraseQuery.Builder()
              .add(new Term("f", "a"), 0)
              .add(new Term("f", "b"), Integer.MAX_VALUE)
              .build();
      assertEquals(0, countWithin10s(s, q));
    }
  }

  /**
   * Same with a position below IndexWriter.MAX_POSITION: the document's "a" is at position 200, the
   * query's "b" at IndexWriter.MAX_POSITION, so 200 + MAX_POSITION overflows int.
   */
  public void testExactPhraseMaxIndexablePositionTerminates() throws Exception {
    Token a = new Token("a", 0, 1);
    a.setPositionIncrement(201); // position 200
    Token b = new Token("b", 2, 3);
    Document d = new Document();
    d.add(new TextField("f", new CannedTokenStream(a, b)));
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, d)) {
      IndexSearcher s = new IndexSearcher(r);
      PhraseQuery q =
          new PhraseQuery.Builder()
              .add(new Term("f", "a"), 0)
              .add(new Term("f", "b"), IndexWriter.MAX_POSITION)
              .build();
      assertEquals(0, countWithin10s(s, q));
    }
  }

  /**
   * Sloppy phrase: "a" at 0 and "b" at Integer.MAX_VALUE with slop 1; the document "b x x a" has the
   * two terms about 2^31 positions away from the query positions, so it must not match.
   */
  public void testSloppyPhraseHugePositionNoFalseMatch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("b x x a"))) {
      IndexSearcher s = new IndexSearcher(r);
      PhraseQuery q =
          new PhraseQuery.Builder()
              .add(new Term("f", "a"), 0)
              .add(new Term("f", "b"), Integer.MAX_VALUE)
              .setSlop(1)
              .build();
      assertEquals(0, countWithin10s(s, q));
    }
  }

  /**
   * IndexSearcher#setTimeout with a 1-second QueryTimeout does not stop it either: the timeout is
   * checked between documents, and the loop is inside a single document's TwoPhaseIterator#matches.
   */
  public void testExactPhraseHugePositionIgnoresTimeout() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("x a b"))) {
      IndexSearcher s = new IndexSearcher(r);
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      s.setTimeout(() -> System.nanoTime() > deadline);
      PhraseQuery q =
          new PhraseQuery.Builder()
              .add(new Term("f", "a"), 0)
              .add(new Term("f", "b"), Integer.MAX_VALUE)
              .build();
      ExecutorService ex =
          Executors.newSingleThreadExecutor(
              rr -> {
                Thread t = new Thread(rr, "phrase-search-timeout");
                t.setDaemon(true);
                return t;
              });
      Future<TopDocs> f = ex.submit(() -> s.search(q, 10));
      try {
        TopDocs td = f.get(10, TimeUnit.SECONDS);
        assertEquals(0, td.scoreDocs.length);
      } catch (TimeoutException e) {
        throw new AssertionError("search with a 1 s QueryTimeout did not return within 10 s", e);
      } finally {
        ex.shutdown();
      }
    }
  }

  /**
   * MultiPhraseQuery.Builder#add(Term[], int) accepts any int position, also negative ones. With "a"
   * at Integer.MIN_VALUE and "b" at 0 the two query positions are 2^31 apart, so with slop 1 no
   * document can match; PhrasePositions#position = docPosition - offset overflows instead.
   */
  public void testSloppyMultiPhraseNegativePositionNoFalseMatch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("a x x b"))) {
      IndexSearcher s = new IndexSearcher(r);
      MultiPhraseQuery q =
          new MultiPhraseQuery.Builder()
              .add(new Term[] {new Term("f", "a")}, Integer.MIN_VALUE)
              .add(new Term[] {new Term("f", "b")}, 0)
              .setSlop(1)
              .build();
      assertEquals(0, s.count(q));
    }
  }
}

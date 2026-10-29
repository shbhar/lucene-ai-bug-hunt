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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * The phrase matchers add a document position and a query position offset in int. When the sum
 * passes Integer.MAX_VALUE, the exact matcher loops forever inside one document and the sloppy
 * matcher accepts a document whose terms are about 2^31 positions away from the phrase.
 */
// A search that hangs runs on a daemon thread that cannot be stopped; it is left behind on purpose.
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestPhrasePositionOverflow extends LuceneTestCase {

  private static final Term A = new Term("f", "a");
  private static final Term B = new Term("f", "b");

  /** Indexes one document with the given field, default codec. */
  private static DirectoryReader index(Directory dir, Field field) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(field);
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  /** Analyzed text: "x a b" puts x at position 0, a at 1 and b at 2. */
  private static Field text(String s) {
    return new TextField("f", s, Field.Store.NO);
  }

  /** Token "a" at document position posA and token "b" at position posB, posB > posA. */
  private static Field tokens(int posA, int posB) {
    Token a = new Token("a", 0, 1);
    a.setPositionIncrement(posA + 1);
    Token b = new Token("b", 2, 3);
    b.setPositionIncrement(posB - posA);
    return new TextField("f", new CannedTokenStream(a, b));
  }

  /** Runs the search on a daemon thread and fails if it does not return within 10 seconds. */
  private static <T> T within10s(String what, Callable<T> search) throws Exception {
    ExecutorService executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "phrase-search");
              t.setDaemon(true);
              return t;
            });
    try {
      return executor.submit(search).get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError(what + " did not return within 10 s", e);
    } finally {
      executor.shutdown();
    }
  }

  /** Control: an offset of MAX_POSITION is meaningful, this document matches. */
  public void testMaxPositionOffsetMatches() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, tokens(0, IndexWriter.MAX_POSITION))) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = new PhraseQuery.Builder().add(A, 0).add(B, IndexWriter.MAX_POSITION).build();
      assertEquals(1, (int) within10s("count(a@0 b@MAX_POSITION)", () -> s.count(q)));
    }
  }

  /** Exact phrase a@0 b@Integer.MAX_VALUE on "x a b": 1 + Integer.MAX_VALUE wraps. */
  public void testExactPhraseHugeOffsetTerminates() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("x a b"))) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = new PhraseQuery.Builder().add(A, 0).add(B, Integer.MAX_VALUE).build();
      assertEquals(0, (int) within10s("count(a@0 b@Integer.MAX_VALUE)", () -> s.count(q)));
    }
  }

  /** Offset MAX_POSITION: correct while "a" is at position 128 or lower, hangs from 129 on. */
  public void testExactPhraseMaxPositionOffsetTerminates() throws Exception {
    Query q = new PhraseQuery.Builder().add(A, 0).add(B, IndexWriter.MAX_POSITION).build();
    for (int posA : new int[] {128, 129}) {
      try (Directory dir = new ByteBuffersDirectory();
          DirectoryReader r = index(dir, tokens(posA, posA + 1))) {
        IndexSearcher s = new IndexSearcher(r);
        String what = "count(a@0 b@MAX_POSITION) with a at position " + posA;
        assertEquals(0, (int) within10s(what, () -> s.count(q)));
      }
    }
  }

  /** A small phrase gap is enough when indexed positions are close to MAX_POSITION. */
  public void testExactPhraseSmallGapNearMaxPositionTerminates() throws Exception {
    int posA = IndexWriter.MAX_POSITION - 100; // legal; posA + 300 passes Integer.MAX_VALUE
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, tokens(posA, posA + 1))) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = new PhraseQuery.Builder().add(A, 0).add(B, 300).build();
      assertEquals(
          0, (int) within10s("count(a@0 b@300) with a near MAX_POSITION", () -> s.count(q)));
    }
  }

  /** A 1 s timeout does not stop it: the loop never leaves the document. */
  public void testExactPhraseHugeOffsetHonoursTimeout() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("x a b"))) {
      IndexSearcher s = new IndexSearcher(r);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      s.setTimeout(() -> System.nanoTime() > deadline);
      Query q = new PhraseQuery.Builder().add(A, 0).add(B, Integer.MAX_VALUE).build();
      TopDocs td =
          within10s("search(a@0 b@Integer.MAX_VALUE) with a 1 s timeout", () -> s.search(q, 10));
      assertEquals(0, td.scoreDocs.length);
    }
  }

  /** Sloppy phrase a@0 b@Integer.MAX_VALUE~1 on "b x x a": b is 3 positions before a. */
  public void testSloppyPhraseHugeOffsetNoFalseHit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("b x x a"))) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = new PhraseQuery.Builder().add(A, 0).add(B, Integer.MAX_VALUE).setSlop(1).build();
      assertEquals(0, (int) within10s("count(a@0 b@Integer.MAX_VALUE~1)", () -> s.count(q)));
    }
  }

  /** MultiPhraseQuery takes negative positions: a@Integer.MIN_VALUE b@0~1 on "a x x b". */
  public void testSloppyMultiPhraseMinValueOffsetNoFalseHit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, text("a x x b"))) {
      IndexSearcher s = new IndexSearcher(r);
      Query q =
          new MultiPhraseQuery.Builder()
              .add(new Term[] {A}, Integer.MIN_VALUE)
              .add(new Term[] {B}, 0)
              .setSlop(1)
              .build();
      assertEquals(0, (int) within10s("count(a@Integer.MIN_VALUE b@0~1)", () -> s.count(q)));
    }
  }
}

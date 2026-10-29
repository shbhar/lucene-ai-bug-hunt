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
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * The same int overflow, reached from the document side: the document's positions are legal (all
 * below IndexWriter.MAX_POSITION, produced by a standard tokenizer and a large position increment
 * gap between field values), and the phrase query is parsed by the classic QueryParser from plain
 * text (299 stopwords give a phrase gap of 300). A lead position plus the gap exceeds
 * Integer.MAX_VALUE.
 */
// the hanging search thread cannot be stopped; it is a daemon thread and is left behind on purpose
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestPhraseOverflowDocSide extends LuceneTestCase {

  private static final int GAP = 1_073_741_700;

  private static Analyzer gapAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new StandardTokenizer());
      }

      @Override
      public int getPositionIncrementGap(String fieldName) {
        return GAP;
      }
    };
  }

  private static Query parse(int stopwords, int slop) throws Exception {
    StringBuilder sb = new StringBuilder("\"a");
    for (int i = 0; i < stopwords; i++) {
      sb.append(" the");
    }
    sb.append(" b\"");
    if (slop > 0) {
      sb.append('~').append(slop);
    }
    StandardAnalyzer an = new StandardAnalyzer(new CharArraySet(Arrays.asList("the"), false));
    Query q = new QueryParser("f", an).parse(sb.toString());
    an.close();
    return q;
  }

  private static DirectoryReader index(Directory dir, String... values) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig(gapAnalyzer());
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document d = new Document();
      for (String v : values) {
        d.add(new TextField("f", v, Field.Store.NO));
      }
      w.addDocument(d);
    }
    return DirectoryReader.open(dir);
  }

  private static int position(DirectoryReader r, String term) throws Exception {
    LeafReader leaf = r.leaves().get(0).reader();
    PostingsEnum pe = leaf.postings(new Term("f", term), PostingsEnum.POSITIONS);
    assertEquals(0, pe.nextDoc());
    assertEquals(1, pe.freq());
    return pe.nextPosition();
  }

  private static int countWithin10s(IndexSearcher s, Query q) throws Exception {
    ExecutorService ex =
        Executors.newSingleThreadExecutor(
            rr -> {
              Thread t = new Thread(rr, "phrase-search-docside");
              t.setDaemon(true);
              return t;
            });
    Future<Integer> f = ex.submit(() -> s.count(q));
    try {
      return f.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError("IndexSearcher#count(" + q + ") did not return within 10 s", e);
    } finally {
      ex.shutdown();
    }
  }

  /** Exact phrase "a <299 stopwords> b" on a document with "a b" as its third value. */
  public void testExactParsedPhraseLegalDocPositionsTerminates() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, "x", "y", "a b")) {
      int posA = position(r, "a");
      int posB = position(r, "b");
      // preconditions: legal positions, and posA + 300 exceeds Integer.MAX_VALUE
      assertTrue(posB <= IndexWriter.MAX_POSITION);
      assertTrue((long) posA + 300 > Integer.MAX_VALUE);
      Query q = parse(299, 0);
      assertArrayEquals(new int[] {0, 300}, ((PhraseQuery) q).getPositions());
      // "b" is right after "a", not 300 positions later: no match
      assertEquals(0, countWithin10s(new IndexSearcher(r), q));
    }
  }

  /** Sloppy phrase "a <299 stopwords> b"~1 on a document with "b" first and "a" ~2^31 later. */
  public void testSloppyParsedPhraseLegalDocPositionsNoFalseMatch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader r = index(dir, "b", "y", "a")) {
      int posA = position(r, "a");
      int posB = position(r, "b");
      assertEquals(0, posB);
      assertTrue(posA <= IndexWriter.MAX_POSITION);
      assertTrue((long) posA + 300 > Integer.MAX_VALUE);
      Query q = parse(299, 1);
      assertArrayEquals(new int[] {0, 300}, ((PhraseQuery) q).getPositions());
      assertEquals(1, ((PhraseQuery) q).getSlop());
      // "b" is ~2^31 positions before "a"; the query wants it 300 after "a" with slop 1: no match
      assertEquals(0, countWithin10s(new IndexSearcher(r), q));
    }
  }
}

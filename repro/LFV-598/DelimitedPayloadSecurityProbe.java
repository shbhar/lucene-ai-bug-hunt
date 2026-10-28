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
package secprobe;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.QueryBuilder;

/**
 * Security probe for the shared custom PayloadEncoder in DelimitedPayloadTokenFilterFactory. Public API only, default
 * codec, no test framework. Each fact prints a "PROBE" line. The program exits non-zero only if a stock-behaviour fact
 * that the security assessment relies on does not hold (facts 1 and 2); facts 3 and 4 are observations.
 *
 * <ul>
 *   <li>fact1: the three built-in encoders (float, integer, identity) through the factory under concurrent indexing
 *       store every document's own payload (who can trigger it: not document text alone).
 *   <li>fact2: with a custom encoder that reuses one BytesRef, a payload value chosen by one document author lands on
 *       another author's document, and CheckIndex does not detect it.
 *   <li>fact3: with a custom variable-length encoder that reuses a BytesRefBuilder, what else happens: exceptions from
 *       addDocument, IndexWriter tragic events, CheckIndex failures, wrong positions/frequencies. Controls: the
 *       built-in identity encoder through the factory, and the same reusing encoder with one instance per filter.
 *   <li>fact4: the same, but the only other user of the shared encoder is search-time analysis (QueryBuilder over the
 *       same Analyzer, as query parsers do; 4c: the classic QueryParser itself) running while documents are indexed.
 * </ul>
 */
public class DelimitedPayloadSecurityProbe {

  static final AtomicInteger STACKS_PRINTED = new AtomicInteger();

  /** Fixed-length float encoder that returns the same BytesRef on every call (as in the candidate repro). */
  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    public ReusingFloatEncoder() {}

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      int bits = Float.floatToIntBits(Float.parseFloat(new String(buffer, offset, length)));
      scratch.bytes[0] = (byte) (bits >>> 24);
      scratch.bytes[1] = (byte) (bits >>> 16);
      scratch.bytes[2] = (byte) (bits >>> 8);
      scratch.bytes[3] = (byte) bits;
      scratch.offset = 0;
      scratch.length = 4;
      return scratch;
    }
  }

  /** Variable-length identity-style encoder that reuses one BytesRefBuilder (allocation-free). */
  public static final class ReusingBytesEncoder extends AbstractEncoder {
    private final BytesRefBuilder scratch = new BytesRefBuilder();

    public ReusingBytesEncoder() {}

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      scratch.copyChars(buffer, offset, length);
      return scratch.get();
    }
  }

  static Analyzer factoryAnalyzer(String encoder) throws Exception {
    return CustomAnalyzer.builder()
        .withTokenizer("whitespace")
        .addTokenFilter("delimitedPayload", "encoder", encoder, "delimiter", "|")
        .build();
  }

  interface DocText {
    String text(int thread, int i);
  }

  /** Result of one concurrent indexing round. */
  static final class Round {
    final Map<String, Integer> addExceptions = new TreeMap<>();
    String tragic = "none";
    boolean writerOpenAfter;
    String closeException = "none";
    String tragicAfterCommit = "n/a";
    boolean writerOpenAfterCommit = true;
    String closeStack = "";
    int committedDocs = -1;
    boolean checkIndexClean;
    String checkIndexDetail = "";
    Directory dir;
  }

  static Round index(Analyzer a, int threads, int perThread, DocText dt) throws Exception {
    Round r = new Round();
    r.dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(r.dir, new IndexWriterConfig(a));
    // one earlier commit (a document without payloads), to see what survives a failed later commit
    Document base = new Document();
    base.add(new TextField("f", "base", Field.Store.NO));
    w.addDocument(base);
    w.commit();
    CyclicBarrier start = new CyclicBarrier(threads);
    Thread[] ts = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      final int tt = t;
      ts[t] =
          new Thread(
              () -> {
                try {
                  start.await();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
                for (int i = 0; i < perThread; i++) {
                  Document d = new Document();
                  d.add(new TextField("f", dt.text(tt, i), Field.Store.NO));
                  try {
                    w.addDocument(d);
                  } catch (Throwable e) {
                    synchronized (r.addExceptions) {
                      r.addExceptions.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    }
                    if (w.isOpen() == false) {
                      return;
                    }
                  }
                }
              });
      ts[t].start();
    }
    for (Thread t : ts) {
      t.join();
    }
    Throwable tragic = w.getTragicException();
    if (tragic != null) {
      r.tragic = tragic.getClass().getSimpleName() + ": " + String.valueOf(tragic.getMessage());
    }
    r.writerOpenAfter = w.isOpen();
    try {
      if (w.isOpen()) {
        w.commit();
      }
      w.close();
    } catch (Throwable e) {
      r.closeException = e.getClass().getSimpleName() + ": " + e.getMessage();
      Throwable t2 = w.getTragicException();
      r.tragicAfterCommit =
          t2 == null ? "none" : (t2 == e ? "same as commit exception" : t2.getClass().getSimpleName());
      r.writerOpenAfterCommit = w.isOpen();
      StringBuilder sb = new StringBuilder();
      StackTraceElement[] st = e.getStackTrace();
      for (int k = 0; k < Math.min(12, st.length); k++) {
        sb.append(" at ").append(st[k]);
      }
      Throwable c = e.getCause();
      if (c != null) {
        sb.append(" cause=").append(c);
      }
      r.closeStack = sb.toString();
      try {
        w.rollback();
      } catch (Throwable ignore) {
        // already closed
      }
    }
    try (DirectoryReader lr = DirectoryReader.open(r.dir)) {
      r.committedDocs = lr.numDocs();
    } catch (Throwable e) {
      r.committedDocs = -1;
    }
    try {
      if (DirectoryReader.indexExists(r.dir)) {
        try (CheckIndex ci = new CheckIndex(r.dir)) {
          ci.setLevel(CheckIndex.Level.MAX_VALUE);
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
          CheckIndex.Status st = ci.checkIndex();
          r.checkIndexClean = st.clean;
          if (st.clean == false) {
            String out = bos.toString(StandardCharsets.UTF_8);
            int k = out.indexOf("ERROR");
            r.checkIndexDetail =
                (k >= 0 ? out.substring(k, Math.min(out.length(), k + 300)) : "not clean")
                    .replace('\n', ' ');
          }
        }
      } else {
        r.checkIndexDetail = "no commit";
      }
    } catch (Throwable e) {
      r.checkIndexClean = false;
      r.checkIndexDetail = "CheckIndex threw " + e;
    }
    return r;
  }

  static PostingsEnum postings(DirectoryReader r, String term) throws Exception {
    PostingsEnum pe = MultiTerms.getTermPostingsEnum(r, "f", new BytesRef(term), PostingsEnum.ALL);
    if (pe == null || pe.nextDoc() == PostingsEnum.NO_MORE_DOCS) {
      return null;
    }
    pe.nextPosition();
    return pe;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // fact 1: built-in encoders are not affected
  // ---------------------------------------------------------------------------------------------------------------
  static int fact1(String encoder) throws Exception {
    final int threads = 4, perThread = 5000;
    Analyzer a = factoryAnalyzer(encoder);
    DocText dt =
        (t, i) -> {
          int id = t * perThread + i;
          String v = encoder.equals("identity") ? "v" + id : String.valueOf(id);
          return "t" + id + "|" + v;
        };
    Round rd = index(a, threads, perThread, dt);
    int wrong = 0;
    try (DirectoryReader r = DirectoryReader.open(rd.dir)) {
      for (int id = 0; id < threads * perThread; id++) {
        PostingsEnum pe = postings(r, "t" + id);
        BytesRef p = pe == null ? null : pe.getPayload();
        boolean ok;
        if (p == null) {
          ok = false;
        } else if (encoder.equals("float")) {
          ok = PayloadHelper.decodeFloat(p.bytes, p.offset) == (float) id;
        } else if (encoder.equals("integer")) {
          ok = PayloadHelper.decodeInt(p.bytes, p.offset) == id;
        } else {
          ok = p.utf8ToString().equals("v" + id);
        }
        if (ok == false) {
          wrong++;
        }
      }
    }
    System.out.println(
        "PROBE fact1 encoder="
            + encoder
            + " docs="
            + threads * perThread
            + " wrong_payloads="
            + wrong
            + " add_exceptions="
            + rd.addExceptions
            + " tragic="
            + rd.tragic
            + " checkindex_clean="
            + rd.checkIndexClean);
    a.close();
    return wrong;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // fact 2: one author's chosen payload value lands on another author's documents
  // ---------------------------------------------------------------------------------------------------------------
  static int[] fact2(String encoder, int round) throws Exception {
    final int threads = 4, perThread = 5000;
    Analyzer a = factoryAnalyzer(encoder);
    // threads 0,1 = "victim" author: payload 1.0 on every token; threads 2,3 = "other" author: payload 666.0
    DocText dt =
        (t, i) -> (t < 2 ? "v" + (t * perThread + i) + "|1.0" : "o" + (t * perThread + i) + "|666.0");
    Round rd = index(a, threads, perThread, dt);
    int got666 = 0, otherWrong = 0;
    try (DirectoryReader r = DirectoryReader.open(rd.dir)) {
      for (int t = 0; t < 2; t++) {
        for (int i = 0; i < perThread; i++) {
          PostingsEnum pe = postings(r, "v" + (t * perThread + i));
          BytesRef p = pe == null ? null : pe.getPayload();
          float f = p == null ? Float.NaN : PayloadHelper.decodeFloat(p.bytes, p.offset);
          if (f == 666.0f) {
            got666++;
          } else if (f != 1.0f) {
            otherWrong++;
          }
        }
      }
    }
    System.out.println(
        "PROBE fact2 encoder="
            + encoder
            + " round="
            + round
            + " victim_docs="
            + 2 * perThread
            + " victim_docs_with_other_authors_value_666="
            + got666
            + " victim_docs_with_other_wrong_value="
            + otherWrong
            + " add_exceptions="
            + rd.addExceptions
            + " tragic="
            + rd.tragic
            + " checkindex_clean="
            + rd.checkIndexClean
            + (rd.checkIndexClean ? "" : " detail=" + rd.checkIndexDetail));
    a.close();
    return new int[] {got666, otherWrong, rd.checkIndexClean ? 1 : 0};
  }

  // ---------------------------------------------------------------------------------------------------------------
  // fact 3: variable-length reusing encoder; observe exceptions, tragic events, CheckIndex, positions
  // ---------------------------------------------------------------------------------------------------------------
  static String randomValue(Random rnd) {
    int len = 1 + rnd.nextInt(64);
    StringBuilder sb = new StringBuilder(len);
    for (int k = 0; k < len; k++) {
      sb.append((char) ('a' + rnd.nextInt(26)));
    }
    return sb.toString();
  }

  /** The same filter through its public constructor: one encoder instance per filter (per thread). */
  static Analyzer perFilterAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new DelimitedPayloadTokenFilter(t, '|', new ReusingBytesEncoder()));
      }
    };
  }

  static void fact3(String encoder, int round, Map<String, AtomicInteger> totals) throws Exception {
    final int threads = 4, perThread = 3000;
    Analyzer a = encoder.equals("per-filter-ReusingBytesEncoder") ? perFilterAnalyzer() : factoryAnalyzer(encoder);
    final String[][] expected = new String[threads * perThread][];
    for (int t = 0; t < threads; t++) {
      Random rnd = new Random(31L * round + t);
      for (int i = 0; i < perThread; i++) {
        expected[t * perThread + i] = new String[] {randomValue(rnd), randomValue(rnd), randomValue(rnd)};
      }
    }
    DocText dt =
        (t, i) -> {
          int id = t * perThread + i;
          String[] e = expected[id];
          return "c|" + e[0] + " c|" + e[1] + " u" + id + "|" + e[2];
        };
    Round rd = index(a, threads, perThread, dt);
    int wrongUnique = 0, wrongStructure = 0, missingDocs = 0, readerError = 0;
    String readerErrorDetail = "";
    if (rd.closeException.equals("none")) { // the concurrent documents were committed: check them
      try (DirectoryReader r = DirectoryReader.open(rd.dir)) {
        for (int id = 0; id < threads * perThread; id++) {
          try {
            PostingsEnum pe = postings(r, "u" + id);
            if (pe == null) {
              missingDocs++;
              continue;
            }
            if (pe.freq() != 1 || pe.startOffset() < -1) {
              wrongStructure++;
            }
            BytesRef p = pe.getPayload();
            if (p == null || p.utf8ToString().equals(expected[id][2]) == false) {
              wrongUnique++;
            }
          } catch (Throwable e) {
            readerError++;
            readerErrorDetail = e.toString();
          }
        }
        // shared term "c": every document must have freq 2 at positions 0 and 1
        for (LeafReaderContext ctx : r.leaves()) {
          Terms terms = ctx.reader().terms("f");
          if (terms == null) {
            continue;
          }
          TermsEnum te = terms.iterator();
          if (te.seekExact(new BytesRef("c")) == false) {
            continue;
          }
          try {
            PostingsEnum pe = te.postings(null, PostingsEnum.ALL);
            while (pe.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
              if (pe.freq() != 2) {
                wrongStructure++;
                continue;
              }
              int p0 = pe.nextPosition();
              int p1 = pe.nextPosition();
              if (p0 != 0 || p1 != 1) {
                wrongStructure++;
              }
            }
          } catch (Throwable e) {
            readerError++;
            readerErrorDetail = e.toString();
          }
        }
      }
    }
    boolean anyProblem =
        rd.closeException.equals("none") == false
            || rd.addExceptions.isEmpty() == false
            || rd.tragic.equals("none") == false
            || rd.checkIndexClean == false
            || wrongStructure > 0
            || readerError > 0
            || missingDocs > 0;
    totals.computeIfAbsent("rounds", k -> new AtomicInteger()).incrementAndGet();
    if (wrongUnique > 0) totals.computeIfAbsent("rounds_wrong_payload", k -> new AtomicInteger()).incrementAndGet();
    if (rd.addExceptions.isEmpty() == false)
      totals.computeIfAbsent("rounds_add_exception", k -> new AtomicInteger()).incrementAndGet();
    if (rd.tragic.equals("none") == false)
      totals.computeIfAbsent("rounds_tragic", k -> new AtomicInteger()).incrementAndGet();
    if (rd.checkIndexClean == false)
      totals.computeIfAbsent("rounds_checkindex_not_clean", k -> new AtomicInteger()).incrementAndGet();
    if (rd.closeException.equals("none") == false)
      totals
          .computeIfAbsent("rounds_commit_failed_" + rd.closeException.replaceAll(":.*", ""), k -> new AtomicInteger())
          .incrementAndGet();
    if (rd.tragicAfterCommit.equals("n/a") == false && rd.tragicAfterCommit.equals("none") == false)
      totals.computeIfAbsent("rounds_tragic_after_commit", k -> new AtomicInteger()).incrementAndGet();
    if (wrongStructure > 0 || readerError > 0 || missingDocs > 0)
      totals.computeIfAbsent("rounds_wrong_structure", k -> new AtomicInteger()).incrementAndGet();
    System.out.println(
        "PROBE fact3 encoder="
            + encoder
            + " round="
            + round
            + " docs="
            + threads * perThread
            + " wrong_unique_payloads="
            + wrongUnique
            + " wrong_freq_or_positions="
            + wrongStructure
            + " missing_docs="
            + missingDocs
            + " reader_errors="
            + readerError
            + (readerError > 0 ? " (" + readerErrorDetail + ")" : "")
            + " add_exceptions="
            + rd.addExceptions
            + " tragic="
            + rd.tragic
            + " writer_open_after="
            + rd.writerOpenAfter
            + " close="
            + rd.closeException
            + (rd.closeException.equals("none")
                ? ""
                : " tragic_after_commit="
                    + rd.tragicAfterCommit
                    + " writer_open_after_commit="
                    + rd.writerOpenAfterCommit)
            + " last_commit_docs="
            + rd.committedDocs
            + " checkindex_clean="
            + rd.checkIndexClean
            + (rd.checkIndexClean ? "" : " detail=" + rd.checkIndexDetail)
            + (anyProblem ? " BEYOND_WRONG_VALUES" : ""));
    if (rd.closeStack.isEmpty() == false && STACKS_PRINTED.getAndIncrement() < 3) {
      System.out.println("PROBE fact3 round=" + round + " commit_failure_stack:" + rd.closeStack);
    }
    a.close();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // fact 4: search-time analysis with the same Analyzer (QueryBuilder, as query parsers do) while documents are indexed
  // ---------------------------------------------------------------------------------------------------------------
  interface QueryText {
    String text(Random rnd);
  }

  /** Runs index() while queryThreads threads keep analysing query text with the same Analyzer through QueryBuilder. */
  static volatile boolean useClassicParser = false;
  static final AtomicInteger PARSE_ERRORS = new AtomicInteger();

  static Round indexWhileQuerying(
      Analyzer a, int threads, int perThread, DocText dt, int queryThreads, QueryText qt, int[] queriesOut)
      throws Exception {
    final boolean classic = useClassicParser;
    java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
    AtomicInteger queries = new AtomicInteger();
    Thread[] qs = new Thread[queryThreads];
    for (int q = 0; q < queryThreads; q++) {
      final long seed = 1000L + q;
      qs[q] =
          new Thread(
              () -> {
                Random rnd = new Random(seed);
                QueryBuilder qb = new QueryBuilder(a);
                org.apache.lucene.queryparser.classic.QueryParser qp =
                    classic ? new org.apache.lucene.queryparser.classic.QueryParser("f", a) : null;
                while (done.get() == false) {
                  if (classic) {
                    try {
                      qp.parse(qt.text(rnd));
                    } catch (Exception e) {
                      PARSE_ERRORS.incrementAndGet();
                    }
                  } else {
                    qb.createBooleanQuery("f", qt.text(rnd));
                  }
                  queries.incrementAndGet();
                }
              });
      qs[q].start();
    }
    Round rd;
    try {
      rd = index(a, threads, perThread, dt);
    } finally {
      done.set(true);
      for (Thread q : qs) {
        q.join();
      }
    }
    queriesOut[0] = queries.get();
    return rd;
  }

  static int fact4a(String encoder, int round) throws Exception {
    final int threads = 2, perThread = 5000;
    Analyzer a = factoryAnalyzer(encoder);
    int[] nq = new int[1];
    Round rd =
        indexWhileQuerying(
            a, threads, perThread, (t, i) -> "v" + (t * perThread + i) + "|1.0", 2, rnd -> "q|666.0", nq);
    int got666 = 0, otherWrong = 0;
    try (DirectoryReader r = DirectoryReader.open(rd.dir)) {
      for (int id = 0; id < threads * perThread; id++) {
        PostingsEnum pe = postings(r, "v" + id);
        BytesRef p = pe == null ? null : pe.getPayload();
        float f = p == null ? Float.NaN : PayloadHelper.decodeFloat(p.bytes, p.offset);
        if (f == 666.0f) {
          got666++;
        } else if (f != 1.0f) {
          otherWrong++;
        }
      }
    }
    System.out.println(
        "PROBE fact4a encoder="
            + encoder
            + " round="
            + round
            + " indexed_docs="
            + threads * perThread
            + " queries_analysed="
            + nq[0]
            + " indexed_docs_with_query_value_666="
            + got666
            + " indexed_docs_with_other_wrong_value="
            + otherWrong
            + " add_exceptions="
            + rd.addExceptions
            + " close="
            + rd.closeException
            + " checkindex_clean="
            + rd.checkIndexClean);
    a.close();
    return got666 + otherWrong;
  }

  static String fact4b(String encoder, int round) throws Exception {
    final int threads = 2, perThread = 3000;
    Analyzer a = factoryAnalyzer(encoder);
    int[] nq = new int[1];
    Round rd =
        indexWhileQuerying(
            a,
            threads,
            perThread,
            (t, i) -> "c|abcd c|abcd u" + (t * perThread + i) + "|abcd",
            2,
            rnd -> "q|" + randomValue(rnd),
            nq);
    System.out.println(
        "PROBE fact4b encoder="
            + encoder
            + " round="
            + round
            + " indexed_docs="
            + threads * perThread
            + " (all payloads 4 bytes) queries_analysed="
            + nq[0]
            + " add_exceptions="
            + rd.addExceptions
            + " close="
            + rd.closeException
            + (rd.closeException.equals("none")
                ? ""
                : " tragic_after_commit="
                    + rd.tragicAfterCommit
                    + " writer_open_after_commit="
                    + rd.writerOpenAfterCommit)
            + " last_commit_docs="
            + rd.committedDocs
            + " checkindex_clean="
            + rd.checkIndexClean);
    a.close();
    return rd.closeException.replaceAll(":.*", "");
  }

  public static void main(String[] args) throws Exception {
    String reuseFloat = ReusingFloatEncoder.class.getName();
    String reuseBytes = ReusingBytesEncoder.class.getName();
    boolean ok = true;

    // fact 1
    for (String enc : new String[] {"float", "integer", "identity"}) {
      if (fact1(enc) != 0) {
        System.out.println("PROBE fact1 UNEXPECTED: built-in encoder " + enc + " produced wrong payloads");
        ok = false;
      }
    }

    // fact 2 (control first, then the stateful custom encoder, up to 5 rounds until a hit)
    int[] ctl = fact2("float", 0);
    if (ctl[0] != 0 || ctl[1] != 0) {
      System.out.println("PROBE fact2 UNEXPECTED: built-in float encoder control produced wrong payloads");
      ok = false;
    }
    int hits = 0, cleanRounds = 0, rounds = 0;
    for (int round = 1; round <= 5; round++) {
      int[] res = fact2(reuseFloat, round);
      rounds++;
      hits += res[0];
      cleanRounds += res[2];
    }
    System.out.println(
        "PROBE fact2 summary rounds="
            + rounds
            + " total_victim_docs_with_666="
            + hits
            + " rounds_checkindex_clean="
            + cleanRounds);
    if (hits == 0) {
      System.out.println("PROBE fact2 UNEXPECTED: no cross-author payload observed in 5 rounds");
      ok = false;
    }

    // fact 3 (control with built-in identity, then the variable-length reusing encoder)
    Map<String, AtomicInteger> ctlTotals = new TreeMap<>();
    fact3("identity", 0, ctlTotals);
    Map<String, AtomicInteger> perFilterTotals = new TreeMap<>();
    for (int round = 1; round <= 5; round++) {
      fact3("per-filter-ReusingBytesEncoder", round, perFilterTotals);
    }
    Map<String, AtomicInteger> totals = new TreeMap<>();
    int n3 = args.length > 0 ? Integer.parseInt(args[0]) : 10;
    for (int round = 1; round <= n3; round++) {
      fact3(reuseBytes, round, totals);
    }
    System.out.println(
        "PROBE fact3 control_totals="
            + ctlTotals
            + " per_filter_control_totals="
            + perFilterTotals
            + " stateful_totals="
            + totals);
    if (perFilterTotals.size() != 1 || ctlTotals.size() != 1) {
      System.out.println("PROBE fact3 UNEXPECTED: a control round showed a problem");
      ok = false;
    }

    // fact 4: search-time analysis through the same Analyzer
    if (fact4a("float", 0) != 0) {
      System.out.println("PROBE fact4a UNEXPECTED: built-in float control produced wrong payloads");
      ok = false;
    }
    int f4a = 0;
    for (int round = 1; round <= 5; round++) {
      f4a += fact4a(reuseFloat, round);
    }
    System.out.println("PROBE fact4a summary rounds=5 total_wrong_indexed_payloads=" + f4a);
    // fact 4c: the same as 4a, but the query threads parse the query string "f:x|666.0" style text with the classic
    // QueryParser (what an application would hand a search box string to)
    useClassicParser = true;
    String singleTerm =
        new org.apache.lucene.queryparser.classic.QueryParser("f", factoryAnalyzer("float")).parse("x|666.0").toString();
    System.out.println("PROBE fact4c classic QueryParser.parse(\"x|666.0\") on the payload field gives: " + singleTerm);
    int f4c = 0;
    for (int round = 1; round <= 3; round++) {
      f4c += fact4a(reuseFloat, 100 + round);
    }
    useClassicParser = false;
    System.out.println(
        "PROBE fact4c summary (classic QueryParser) rounds=3 total_wrong_indexed_payloads="
            + f4c
            + " parse_errors="
            + PARSE_ERRORS.get());
    String ctl4b = fact4b("identity", 0);
    if (ctl4b.equals("none") == false) {
      System.out.println("PROBE fact4b UNEXPECTED: built-in identity control commit failed");
      ok = false;
    }
    Map<String, Integer> f4b = new TreeMap<>();
    for (int round = 1; round <= 5; round++) {
      f4b.merge(fact4b(reuseBytes, round), 1, Integer::sum);
    }
    System.out.println("PROBE fact4b summary rounds=5 commit_outcomes=" + f4b);

    if (ok == false) {
      System.exit(1);
    }
  }
}

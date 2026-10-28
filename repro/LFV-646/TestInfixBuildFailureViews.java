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
package org.apache.lucene.search.suggest.analyzing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Independent check: after AnalyzingInfixSuggester.build() throws, do the suggestions served by the
 * live instance (after refresh()) agree with the suggestions stored in its directory (what a
 * reopened suggester serves), and does a later add() + refresh() add exactly one suggestion?
 *
 * <p>Trigger A: an IOException while the new searcher opens the freshly built segment (the first
 * open of its compound file after the test arms the directory). Trigger B: no I/O fault; a
 * suggestion was added but not yet refreshed, then a rebuild's InputIterator throws.
 *
 * <p>Every assertion is outcome-independent: it accepts "the failed build is applied" and "the
 * failed build is not applied" alike, and only rejects a split between the live and the stored
 * view.
 */
public class TestInfixBuildFailureViews extends LuceneTestCase {

  /**
   * Logs commits and compound-file opens. Once armed, fails either the next compound-file open, or
   * (deleteMode) the first file deletion after a new segments_N became visible.
   */
  static final class FaultDirectory extends FilterDirectory {
    final List<String> events = Collections.synchronizedList(new ArrayList<>());
    volatile boolean armed;
    volatile boolean fired;
    volatile boolean deleteMode;
    volatile boolean sawCommit;

    FaultDirectory(Directory in) {
      super(in);
    }

    void arm(boolean deleteMode) {
      events.clear();
      fired = false;
      sawCommit = false;
      this.deleteMode = deleteMode;
      armed = true;
    }

    @Override
    public void deleteFile(String name) throws IOException {
      if (armed && deleteMode && sawCommit) {
        armed = false;
        fired = true;
        events.add("INJECTED IOException on delete of " + name);
        throw new IOException("injected: cannot delete " + name);
      }
      super.deleteFile(name);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      if (name.endsWith(".cfe") || name.endsWith(".cfs")) {
        if (armed && deleteMode == false) {
          armed = false;
          fired = true;
          // logging only (no assertion uses it): where the failed open came from
          boolean inSearcherManagerCtor = false;
          for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            if (f.getClassName().endsWith(".SearcherManager") && f.getMethodName().equals("<init>")) {
              inSearcherManagerCtor = true;
            }
          }
          events.add(
              "INJECTED IOException on open of "
                  + name
                  + " (inside new SearcherManager: "
                  + inSearcherManagerCtor
                  + ")");
          throw new IOException("injected: cannot open " + name);
        }
        events.add("open " + name);
      }
      return super.openInput(name, context);
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      super.rename(source, dest);
      if (dest.startsWith("segments_")) {
        sawCommit = true;
      }
      events.add("rename " + source + " -> " + dest);
    }

    /** True if a new commit point became visible (segments_N rename) before the injected fault. */
    boolean committedBeforeFault() {
      synchronized (events) {
        for (String e : events) {
          if (e.startsWith("INJECTED")) {
            return false;
          }
          if (e.startsWith("rename ") && e.contains("-> segments_")) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /** View of a directory whose close() does nothing, to reopen the stored index read-only. */
  static final class NoCloseDirectory extends FilterDirectory {
    NoCloseDirectory(Directory in) {
      super(in);
    }

    @Override
    public void close() {}
  }

  /** Minimal InputIterator over fixed texts; optionally throws from next() instead. */
  static final class Source implements InputIterator {
    private final String[] texts;
    private final boolean failing;
    private int upto;

    Source(boolean failing, String... texts) {
      this.failing = failing;
      this.texts = texts;
    }

    @Override
    public BytesRef next() throws IOException {
      if (failing) {
        throw new IOException("injected: suggestion source failed");
      }
      return upto < texts.length ? new BytesRef(texts[upto++]) : null;
    }

    @Override
    public long weight() {
      return 1;
    }

    @Override
    public BytesRef payload() {
      return null;
    }

    @Override
    public boolean hasPayloads() {
      return false;
    }

    @Override
    public Set<BytesRef> contexts() {
      return null;
    }

    @Override
    public boolean hasContexts() {
      return false;
    }
  }

  private static Analyzer newAnalyzer() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
  }

  /** All suggestions with a token starting with "a" (every text used here has one). */
  private static List<String> served(AnalyzingInfixSuggester s) throws IOException {
    List<String> out = new ArrayList<>();
    for (LookupResult r : s.lookup("a", 100, true, false)) {
      out.add(r.key.toString());
    }
    Collections.sort(out);
    return out;
  }

  /** What a new suggester opened on the same directory serves (the stored suggestions). */
  private static List<String> stored(Directory base) throws IOException {
    try (AnalyzingInfixSuggester reopened =
        new AnalyzingInfixSuggester(new NoCloseDirectory(base), newAnalyzer())) {
      return served(reopened);
    }
  }

  private static List<String> plus(List<String> list, String extra) {
    List<String> out = new ArrayList<>(list);
    out.add(extra);
    Collections.sort(out);
    return out;
  }

  private static boolean hasInjected(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c.getMessage() != null && c.getMessage().startsWith("injected")) {
        return true;
      }
      for (Throwable sup : c.getSuppressed()) {
        if (hasInjected(sup)) {
          return true;
        }
      }
    }
    return false;
  }

  /** The live suggester after a failed build, plus what it served and stored at that moment. */
  private static final class AfterFailure {
    final String config;
    final AnalyzingInfixSuggester live;
    final Directory base;
    final List<String> servedAfterRefresh;
    final List<String> stored;

    AfterFailure(
        String config,
        AnalyzingInfixSuggester live,
        Directory base,
        List<String> servedAfterRefresh,
        List<String> stored) {
      this.config = config;
      this.live = live;
      this.base = base;
      this.servedAfterRefresh = servedAfterRefresh;
      this.stored = stored;
    }
  }

  /**
   * Trigger A: build "old apple" (made durable), then rebuild with "new avocado" while the first
   * compound-file open of the rebuild fails.
   */
  private AfterFailure triggerA(Directory base, boolean commitOnBuild, boolean closeOnBuild)
      throws IOException {
    return triggerA(base, commitOnBuild, closeOnBuild, false);
  }

  private AfterFailure triggerA(
      Directory base, boolean commitOnBuild, boolean closeOnBuild, boolean deleteMode)
      throws IOException {
    String config =
        "commitOnBuild="
            + commitOnBuild
            + ",closeIndexWriterOnBuild="
            + closeOnBuild
            + (deleteMode ? ",fault=delete-after-commit" : ",fault=open-new-segment");
    FaultDirectory dir = new FaultDirectory(base);
    Analyzer a = newAnalyzer();
    AnalyzingInfixSuggester s =
        new AnalyzingInfixSuggester(dir, a, a, 4, commitOnBuild, true, true, closeOnBuild);
    s.build(new Source(false, "old apple"));
    if (commitOnBuild == false && closeOnBuild == false) {
      s.commit(); // make the first build durable so that both views start equal
    }
    assertEquals("HARNESS: live view after first build", List.of("old apple"), served(s));
    assertEquals("HARNESS: stored view after first build", List.of("old apple"), stored(base));

    dir.arm(deleteMode);
    IOException e = expectThrows(IOException.class, () -> s.build(new Source(false, "new avocado")));
    assertTrue("HARNESS: build() must fail with the injected fault, got " + e, hasInjected(e));
    assertTrue("HARNESS: fault did not fire", dir.fired);
    System.out.println(
        "[v1] "
            + config
            + " build() threw: "
            + e
            + "\n[v1]   directory events during the failed build: "
            + dir.events
            + "\n[v1]   commit point published before the fault: "
            + dir.committedBeforeFault());

    s.refresh();
    List<String> served = served(s);
    List<String> stored = stored(base);
    System.out.println("[v1]   after refresh(): served=" + served + " stored=" + stored);
    return new AfterFailure(config, s, base, served, stored);
  }

  private void checkServedEqualsStored(AfterFailure f) throws IOException {
    try {
      assertEquals(
          f.config
              + ": after build() threw and refresh(), the served suggestions must equal the stored"
              + " ones (what a reopened suggester serves)",
          f.stored,
          f.servedAfterRefresh);
    } finally {
      IOUtils.close(f.live);
    }
  }

  private void checkAddAddsExactlyOne(AfterFailure f) throws IOException {
    try {
      f.live.add(new BytesRef("another apricot"), null, 1, null);
      f.live.refresh();
      List<String> after = served(f.live);
      System.out.println(
          "[v1]   " + f.config + " after add('another apricot') + refresh(): served=" + after);
      assertEquals(
          f.config
              + ": after build() threw, add() + refresh() must add exactly the new suggestion to"
              + " the served ones",
          plus(f.servedAfterRefresh, "another apricot"),
          after);
    } finally {
      IOUtils.close(f.live);
    }
  }

  // ---- Trigger A, default configuration (closeIndexWriterOnBuild=true, commitOnBuild=false) ----

  public void testA_defaultConfig_servedEqualsStored() throws IOException {
    checkServedEqualsStored(triggerA(new ByteBuffersDirectory(), false, true));
  }

  public void testA_defaultConfig_addAddsExactlyOne() throws IOException {
    checkAddAddsExactlyOne(triggerA(new ByteBuffersDirectory(), false, true));
  }

  /** Same as the first test, on a file-system directory from the test framework. */
  public void testA_defaultConfig_fsDirectory_servedEqualsStored() throws IOException {
    checkServedEqualsStored(triggerA(newFSDirectory(createTempDir("v1infix")), false, true));
  }

  /** Variant: commit() itself throws after its commit point is durable (old-file deletion). */
  public void testA_defaultConfig_commitThrowsAfterDurable_servedEqualsStored()
      throws IOException {
    checkServedEqualsStored(triggerA(new ByteBuffersDirectory(), false, true, true));
  }

  // ---- Trigger A, commitOnBuild=true without closing the writer ----

  public void testA_commitOnBuild_servedEqualsStored() throws IOException {
    checkServedEqualsStored(triggerA(new ByteBuffersDirectory(), true, false));
  }

  // ---- Control for trigger A: no commit inside build(); same fault at the same place ----

  public void testControlA_noCommitInBuild_servedEqualsStored() throws IOException {
    checkServedEqualsStored(triggerA(new ByteBuffersDirectory(), false, false));
  }

  public void testControlA_noCommitInBuild_addAddsExactlyOne() throws IOException {
    checkAddAddsExactlyOne(triggerA(new ByteBuffersDirectory(), false, false));
  }

  // ---- Trigger B: default configuration, no I/O fault ----

  private void triggerB(boolean pendingAdd) throws IOException {
    Directory base = new ByteBuffersDirectory();
    AnalyzingInfixSuggester s = new AnalyzingInfixSuggester(base, newAnalyzer());
    try {
      s.build(new Source(false, "old apple"));
      if (pendingAdd) {
        s.add(new BytesRef("added apricot"), null, 1, null); // not refreshed yet
      }
      IOException e = expectThrows(IOException.class, () -> s.build(new Source(true)));
      assertTrue("HARNESS: unexpected exception " + e, hasInjected(e));
      s.refresh();
      List<String> served = served(s);
      List<String> stored = stored(base);
      System.out.println(
          "[v1] trigger B pendingAdd="
              + pendingAdd
              + ": after the failed rebuild and refresh(): served="
              + served
              + " stored="
              + stored);
      if (pendingAdd) {
        // descriptive only: when does the committed addition become visible?
        s.add(new BytesRef("another apricot"), null, 1, null);
        s.refresh();
        System.out.println("[v1]   after a later add('another apricot') + refresh(): " + served(s));
      }
      assertEquals(
          "default config, pendingAdd="
              + pendingAdd
              + ", rebuild whose InputIterator throws: served after refresh() vs. stored",
          stored,
          served);
    } finally {
      IOUtils.close(s);
    }
  }

  public void testB_pendingAddThenFailingRebuild_servedEqualsStored() throws IOException {
    triggerB(true);
  }

  public void testControlB_noPendingAddThenFailingRebuild_servedEqualsStored()
      throws IOException {
    triggerB(false);
  }
}

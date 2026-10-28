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
package infixhistory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Version-neutral probe (core + suggest + analysis-common only, no test framework), so that the
 * same source runs on old and new releases. For each scenario it records what the suggester serves
 * after a failed build() and refresh(), what a fresh suggester opened on the same directory serves,
 * and what is served after one add() + refresh(). It exits non-zero when the "silent" form is
 * observed: refresh() and lookup() succeed after the failed build, but serve something other than
 * the committed index, or one add() replaces the served suggestions.
 *
 * <p>Fault: once armed, the first open of a compound file (.cfe/.cfs) throws an IOException. In
 * build() that is the reader that the new SearcherManager opens right after the commit.
 */
public class InfixBuildFailureTimeline {

  /** Fails the first compound-file open after arm(); records whether it was inside a SearcherManager constructor. */
  static final class FaultyDirectory extends FilterDirectory {
    boolean armed;
    String injected = "none";

    FaultyDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      if (armed && (name.endsWith(".cfe") || name.endsWith(".cfs"))) {
        armed = false;
        boolean inSearcherManagerInit = false;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
          if (e.getClassName().endsWith(".SearcherManager") && e.getMethodName().equals("<init>")) {
            inSearcherManagerInit = true;
          }
        }
        injected = name + (inSearcherManagerInit ? " (inside new SearcherManager)" : " (NOT inside new SearcherManager)");
        throw new IOException("simulated I/O error opening " + name);
      }
      return super.openInput(name, context);
    }
  }

  static InputIterator suggestions(String... texts) {
    return iterator(false, texts);
  }

  /** Returns the first text, then throws from next(): a failing suggestion source. */
  static InputIterator failingAfterFirst(String text) {
    return iterator(true, text);
  }

  private static InputIterator iterator(boolean failAfterFirst, String... texts) {
    Iterator<String> it = List.of(texts).iterator();
    return new InputIterator() {
      int returned;

      @Override
      public BytesRef next() throws IOException {
        if (failAfterFirst && returned == 1) {
          throw new IOException("simulated read error in the suggestion source");
        }
        if (it.hasNext()) {
          returned++;
          return new BytesRef(it.next());
        }
        return null;
      }

      @Override
      public long weight() {
        return 10;
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
    };
  }

  static String lookupA(AnalyzingInfixSuggester s) {
    try {
      List<String> out = new ArrayList<>();
      for (LookupResult r : s.lookup("a", 10, true, false)) {
        out.add(r.key.toString());
      }
      out.sort(null);
      return out.toString();
    } catch (Throwable t) {
      return "THROWS " + t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  static String refreshThenLookup(AnalyzingInfixSuggester s) {
    try {
      s.refresh();
    } catch (Throwable t) {
      return "THROWS " + t.getClass().getSimpleName() + ": " + t.getMessage();
    }
    return lookupA(s);
  }

  interface Factory {
    AnalyzingInfixSuggester create(Directory dir, Analyzer a) throws Exception;
  }

  static int silent = 0;

  static void runScenario(String name, Factory factory, boolean pendingAdd, boolean ioFault)
      throws Exception {
    runScenario(name, factory, pendingAdd, ioFault, false);
  }

  static void runScenario(
      String name, Factory factory, boolean pendingAdd, boolean ioFault, boolean commitFirstBuild)
      throws Exception {
    Path path = Files.createTempDirectory("infix-history");
    try {
      Analyzer analyzer = new WhitespaceAnalyzer();
      FaultyDirectory dir = new FaultyDirectory(new NIOFSDirectory(path));
      AnalyzingInfixSuggester s = factory.create(dir, analyzer);
      s.build(suggestions("old apple"));
      if (commitFirstBuild) {
        s.commit(); // so that the first build is durable when build() itself does not commit
      }
      String before = lookupA(s);
      if (pendingAdd) {
        s.add(new BytesRef("added apricot"), null, 5, null); // not refreshed
      }
      String buildResult = "returned normally";
      try {
        if (ioFault) {
          dir.armed = true;
          s.build(suggestions("new avocado"));
        } else {
          s.build(failingAfterFirst("new avocado"));
        }
      } catch (Throwable t) {
        buildResult = "threw " + t.getClass().getSimpleName() + ": " + t.getMessage();
      }
      dir.armed = false;
      String served = refreshThenLookup(s);

      AnalyzingInfixSuggester reopened =
          new AnalyzingInfixSuggester(new NIOFSDirectory(path), new WhitespaceAnalyzer());
      String persisted = lookupA(reopened);
      reopened.close();

      String afterAdd;
      try {
        s.add(new BytesRef("another apricot"), null, 5, null);
        afterAdd = refreshThenLookup(s);
      } catch (Throwable t) {
        afterAdd = "add THROWS " + t.getClass().getSimpleName() + ": " + t.getMessage();
      }
      s.close();
      analyzer.close();

      String verdict;
      if (!buildResult.startsWith("threw")) {
        verdict = "NO_FAILURE (setup did not trigger)";
      } else if (served.startsWith("THROWS")) {
        verdict = "LOUD (refresh()/lookup() throw after the failed build)";
      } else if (!served.equals(persisted)) {
        verdict = "SILENT_SPLIT (served != committed after refresh())";
        silent++;
      } else {
        verdict = "CONSISTENT (served == committed after refresh())";
      }
      // add() of one suggestion must add exactly that one to what is served
      if (!served.startsWith("THROWS") && !afterAdd.startsWith("THROWS") && !afterAdd.startsWith("add THROWS")) {
        List<String> exp = new ArrayList<>();
        String inner = served.substring(1, served.length() - 1);
        if (!inner.isEmpty()) {
          for (String x : inner.split(", ")) exp.add(x);
        }
        exp.add("another apricot");
        exp.sort(null);
        if (!exp.toString().equals(afterAdd)) {
          verdict += " + ADD_NOT_EXACTLY_ONE (one add() changed the served set by more than that suggestion)";
          silent++;
        }
      }
      System.out.println(
          "PROBE lucene="
              + Version.LATEST
              + " scenario="
              + name
              + "\n    before="
              + before
              + "\n    failing build: "
              + buildResult
              + (ioFault ? " [fault: " + dir.injected + "]" : "")
              + "\n    served after refresh()="
              + served
              + "\n    committed (fresh instance)="
              + persisted
              + "\n    after add(another apricot)+refresh()="
              + afterAdd
              + "\n    verdict="
              + verdict);
    } finally {
      try (Stream<Path> w = Files.walk(path)) {
        w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  /** The 8-argument constructor (closeIndexWriterOnBuild) exists only in newer releases. */
  static Constructor<AnalyzingInfixSuggester> eightArgCtor() {
    try {
      return AnalyzingInfixSuggester.class.getConstructor(
          Directory.class,
          Analyzer.class,
          Analyzer.class,
          int.class,
          boolean.class,
          boolean.class,
          boolean.class,
          boolean.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("PROBE lucene=" + Version.LATEST);
    // A: default constructor; I/O error opening the new reader at the end of build()
    runScenario("A-default-ctor", (d, a) -> new AnalyzingInfixSuggester(d, a), false, true);
    // A: commitOnBuild=true (5-argument constructor)
    runScenario(
        "A-commitOnBuild=true",
        (d, a) -> new AnalyzingInfixSuggester(d, a, a, 4, true),
        false,
        true);
    Constructor<AnalyzingInfixSuggester> c8 = eightArgCtor();
    if (c8 != null) {
      // control: no commit inside build() (commitOnBuild=false, closeIndexWriterOnBuild=false)
      runScenario(
          "A-control-no-commit-in-build",
          (d, a) -> c8.newInstance(d, a, a, 4, false, true, false, false),
          false,
          true,
          true);
    } else {
      System.out.println("PROBE scenario=A-control-no-commit-in-build skipped: no closeIndexWriterOnBuild constructor in this release");
    }
    // B: default constructor; unrefreshed add(), then a rebuild whose InputIterator throws
    runScenario("B-default-ctor-pending-add", (d, a) -> new AnalyzingInfixSuggester(d, a), true, false);
    System.out.println("PROBE silent_observations=" + silent);
    if (silent > 0) {
      throw new AssertionError("silent served-vs-committed divergence after a failed build: " + silent);
    }
  }
}

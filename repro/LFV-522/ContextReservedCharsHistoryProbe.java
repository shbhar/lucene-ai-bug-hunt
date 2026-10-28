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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.CompletionAnalyzer;
import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.search.suggest.document.ContextQuery;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.apache.lucene.search.suggest.document.PrefixCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;

/**
 * Version-portable probe (main() mode, public API only) for: ContextSuggestField documents
 * "@throws IllegalArgumentException ... if value or contexts contains any reserved characters", but
 * contexts are checked only for U+001D, so a context containing U+001E or U+001F is accepted and
 * then rewritten by the completion analysis chain.
 *
 * <p>Same scenarios as the candidate's JUnit repro (TestContextReservedCharacters), written so one
 * source compiles and runs on every release that has ContextSuggestField (5.3.0 onward) and on
 * main. Reflection is used only to pick public API that differs between releases: the in-memory
 * Directory class (RAMDirectory or ByteBuffersDirectory) and the SuggestIndexSearcher#suggest
 * overload (with or without skipDuplicates).
 *
 * <p>Exit codes: 0 = bug absent (every claim check correct), 1 = bug present (at least one claim
 * check wrong), 3 = a control failed or the probe could not run (result not usable).
 */
public class ContextReservedCharsHistoryProbe {

  static final String FIELD = "suggest";
  static final List<String> controlFailures = new ArrayList<>();
  static final List<String> bugs = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    int exit;
    try {
      exit = run();
    } catch (Throwable t) {
      System.out.println("PROBE_ERROR " + t);
      t.printStackTrace(System.out);
      exit = 3;
    }
    System.out.flush();
    System.exit(exit);
  }

  static int run() throws Exception {
    System.out.println("completion postings format: " + completionFormat().getName());
    System.out.println("directory: " + newDir().getClass().getSimpleName());

    // ---- controls -------------------------------------------------------------------------
    // C1/C2: U+001E and U+001F are rejected in the suggestion value (SuggestField reserved chars).
    control("C1 value with U+001E rejected", "rejected", valueCheck("a\u001Eb"));
    control("C2 value with U+001F rejected", "rejected", valueCheck("a\u001Fb"));
    // C3: U+001D (CONTEXT_SEPARATOR) in a context is rejected (construction or addDocument).
    control("C3 context with U+001D rejected", "rejected", contextCheck("a\u001Db"));
    // C4: ordinary contexts round-trip and an exact context filters exactly.
    control(
        "C4 exact(ab) on {ab, axb}",
        "[doc0 ctx=ab]",
        String.valueOf(suggest(new String[] {"ab", "axb"}, "ab", true)));
    control(
        "C5 all contexts on {ab, axb}",
        "[doc0 ctx=ab, doc1 ctx=axb]",
        String.valueOf(suggest(new String[] {"ab", "axb"}, null, true)));

    // ---- claim checks (expected = documented behaviour) ------------------------------------
    // B1/B2: the documented IllegalArgumentException for reserved characters in contexts.
    claim("B1 context with U+001E rejected", "rejected", contextCheck("a\u001Eb"));
    claim("B2 context with U+001F rejected", "rejected", contextCheck("a\u001Fb"));
    // B3: if accepted, a U+001E context must not collapse into another context.
    List<String> b3 = suggest(new String[] {"ab", "a\u001Eb"}, "ab", true);
    claim("B3 exact(ab) on {ab, a<1E>b}", b3 == null ? "rejected" : "[doc0 ctx=ab]", show(b3));
    // B4/B5: if accepted, a U+001F context must round-trip and match itself exactly.
    List<String> b4 = suggest(new String[] {"a\u001Fb"}, null, true);
    claim("B4 all contexts on {a<1F>b}", b4 == null ? "rejected" : "[doc0 ctx=a<1F>b]", show(b4));
    List<String> b5 = suggest(new String[] {"a\u001Fb"}, "a\u001Fb", true);
    claim("B5 exact(a<1F>b) on {a<1F>b}", b5 == null ? "rejected" : "[doc0 ctx=a<1F>b]", show(b5));

    // ---- informational (not part of the verdict) -------------------------------------------
    info("I1 exact(a<1E>b) on {ab, a<1E>b}", show(suggest(new String[] {"ab", "a\u001Eb"}, "a\u001Eb", true)));
    info("I2 all contexts on {ab, a<1E>b}", show(suggest(new String[] {"ab", "a\u001Eb"}, null, true)));
    info("I0 preserveSep=false control: all contexts on {ab, axb}", show(suggest(new String[] {"ab", "axb"}, null, false)));
    info("I3 preserveSep=false: all contexts on {a<1F>b}", show(suggest(new String[] {"a\u001Fb"}, null, false)));
    info("I4 preserveSep=false: exact(ab) on {ab, a<1F>b}", show(suggest(new String[] {"ab", "a\u001Fb"}, "ab", false)));
    info("I5 context with U+0000", contextCheck("a\u0000b") + " / all=" + show(suggest(new String[] {"a\u0000b"}, null, true)));

    if (!controlFailures.isEmpty()) {
      System.out.println("VERDICT CONTROL_FAILED " + controlFailures);
      return 3;
    }
    if (!bugs.isEmpty()) {
      System.out.println("VERDICT BUG_PRESENT " + bugs.size() + "/5 claim checks wrong: " + bugs);
      return 1;
    }
    System.out.println("VERDICT BUG_ABSENT 0/5 claim checks wrong");
    return 0;
  }

  static void control(String name, String expected, String actual) {
    boolean ok = expected.equals(actual);
    System.out.println((ok ? "OK      " : "CTRL_BAD") + " " + name + ": expected " + expected + ", actual " + actual);
    if (!ok) controlFailures.add(name);
  }

  static void claim(String name, String expected, String actual) {
    boolean ok = expected.equals(actual);
    System.out.println((ok ? "OK      " : "WRONG   ") + " " + name + ": expected " + expected + ", actual " + actual);
    if (!ok) bugs.add(name.substring(0, 2));
  }

  static void info(String name, String actual) {
    System.out.println("INFO     " + name + ": " + actual);
  }

  static String valueCheck(String value) {
    try {
      new ContextSuggestField(FIELD, value, 1, "c");
      return "accepted";
    } catch (IllegalArgumentException e) {
      return "rejected";
    }
  }

  /** "rejected" if construction or addDocument throws IllegalArgumentException, else "accepted". */
  static String contextCheck(String context) throws Exception {
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer());
      iwc.setCodec(codec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        try {
          Document d = new Document();
          d.add(new ContextSuggestField(FIELD, "value", 1, context));
          w.addDocument(d);
          return "accepted";
        } catch (IllegalArgumentException e) {
          return "rejected";
        }
      }
    }
  }

  /**
   * Indexes one document per context (suggestion "value", weight 10 - i) in a single segment and
   * queries prefix "val" with an exact context, or with all contexts when exact == null. Returns
   * null if indexing rejected a context with IllegalArgumentException.
   */
  static List<String> suggest(String[] contexts, String exact, boolean preserveSep) throws Exception {
    Analyzer analyzer =
        preserveSep ? new WhitespaceAnalyzer() : new CompletionAnalyzer(new WhitespaceAnalyzer(), false, true);
    List<String> out = new ArrayList<>();
    try (Directory dir = newDir()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(codec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < contexts.length; i++) {
          try {
            Document d = new Document();
            d.add(new ContextSuggestField(FIELD, "value", 10 - i, contexts[i]));
            w.addDocument(d);
          } catch (IllegalArgumentException e) {
            return null;
          }
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        if (reader.leaves().size() != 1 || reader.maxDoc() != contexts.length) {
          throw new IllegalStateException("expected one segment with docIDs in insertion order");
        }
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        ContextQuery query = new ContextQuery(new PrefixCompletionQuery(analyzer, new Term(FIELD, "val")));
        if (exact == null) {
          query.addAllContexts();
        } else {
          query.addContext(exact);
        }
        for (TopSuggestDocs.SuggestScoreDoc hit : topSuggest(searcher, query, 10).scoreLookupDocs()) {
          out.add("doc" + hit.doc + " ctx=" + (hit.context == null ? "null" : escape(hit.context.toString())));
        }
      }
    }
    Collections.sort(out);
    return out;
  }

  static String show(List<String> hits) {
    return hits == null ? "rejected" : hits.toString();
  }

  static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20) {
        sb.append('<').append(String.format("%02X", (int) c)).append('>');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static TopSuggestDocs topSuggest(SuggestIndexSearcher s, CompletionQuery q, int n) throws Exception {
    try {
      try {
        Method m = SuggestIndexSearcher.class.getMethod("suggest", CompletionQuery.class, int.class, boolean.class);
        return (TopSuggestDocs) m.invoke(s, q, n, false);
      } catch (NoSuchMethodException e) {
        Method m = SuggestIndexSearcher.class.getMethod("suggest", CompletionQuery.class, int.class);
        return (TopSuggestDocs) m.invoke(s, q, n);
      }
    } catch (InvocationTargetException e) {
      Throwable c = e.getCause();
      if (c instanceof Exception) throw (Exception) c;
      throw (Error) c;
    }
  }

  static Directory newDir() throws Exception {
    for (String cn : Arrays.asList("org.apache.lucene.store.ByteBuffersDirectory", "org.apache.lucene.store.RAMDirectory")) {
      try {
        return (Directory) Class.forName(cn).getConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        // try the next one
      }
    }
    throw new IllegalStateException("no in-memory Directory class");
  }

  /** The completion postings format that wraps this release's default postings format. */
  static PostingsFormat completionFormat() {
    PostingsFormat pf = Codec.getDefault().postingsFormat();
    if (pf instanceof PerFieldPostingsFormat) {
      pf = ((PerFieldPostingsFormat) pf).getPostingsFormatForField(FIELD);
    }
    List<String> names = new ArrayList<>();
    if (pf.getName().startsWith("Lucene")) {
      names.add("Completion" + pf.getName().substring("Lucene".length()));
    }
    // No suffix match (e.g. 10.3.x, where Completion101PostingsFormat wraps the default Lucene103
    // postings, as the upstream TestSuggestField of that release uses it): newest first. 5.x-8.3
    // only have "completion" (Completion50PostingsFormat). A wrong pick fails loudly (controls).
    names.addAll(
        Arrays.asList(
            "Completion104", "Completion101", "Completion912", "Completion99", "Completion90",
            "Completion84", "completion"));
    for (String name : names) {
      try {
        return PostingsFormat.forName(name);
      } catch (IllegalArgumentException e) {
        // try the next one
      }
    }
    throw new IllegalStateException("no completion postings format for " + pf.getName());
  }

  static Codec codec() {
    final PostingsFormat completion = completionFormat();
    final Codec base = Codec.getDefault();
    final PostingsFormat basePf = base.postingsFormat();
    final PostingsFormat perField =
        new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            if (field.equals(FIELD)) {
              return completion;
            }
            return basePf instanceof PerFieldPostingsFormat
                ? ((PerFieldPostingsFormat) basePf).getPostingsFormatForField(field)
                : basePf;
          }
        };
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        return perField;
      }
    };
  }
}

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
package sfhist;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Version-portable probe (Lucene 5.0 .. 10.x, one source) for the document suggester's unchecked
 * analyzed form: SuggestField rejects U+0000 (the FST end byte) only in the raw value, while the FST
 * input is built from the analyzed form. HTMLStripCharFilter decodes "&#0;" to U+0000.
 *
 * <p>NIOFSDirectory is used instead of FSDirectory.open because the 5.x MMapDirectory unmap hack
 * fails on current JDKs (InaccessibleObjectException), which is unrelated to this bug.
 *
 * <p>The suggest classes are reached by reflection only because their API changed across releases
 * (5.1/5.2 field+key lookup, 5.3+ CompletionQuery, 6.5+ skipDuplicates flag, one completion
 * postings format per codec generation). Everything else is plain public API.
 *
 * <p>Oracle (per scenario): every document whose addDocument was accepted is returned by a prefix
 * lookup for its own value (n = 100, at most 4 documents), and commit succeeds with the writer open.
 * Controls use "&#1;" (U+0001, not reserved). Output: one line per scenario, then a VERDICT line.
 * Exit 0 = not affected (or API absent), 1 = bug reproduced, 2 = inconclusive (a control failed).
 */
public class SuggestFieldEndByteHistoryProbe {

  static final String PKG = "org.apache.lucene.search.suggest.document.";
  static final String[] COMPLETION_FORMATS_NEWEST_FIRST = {
    "Completion104PostingsFormat",
    "Completion101PostingsFormat",
    "Completion912PostingsFormat",
    "Completion99PostingsFormat",
    "Completion90PostingsFormat",
    "Completion84PostingsFormat",
    "Completion50PostingsFormat"
  };

  static String apiUsed = "";

  static Analyzer htmlStripKeyword() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new KeywordTokenizer());
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
      }
    };
  }

  static PostingsFormat completionFormat() throws Exception {
    for (String n : COMPLETION_FORMATS_NEWEST_FIRST) {
      try {
        Class<?> c = Class.forName(PKG + n);
        return (PostingsFormat) c.getConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        // older or newer generation; try the next one
      }
    }
    throw new IllegalStateException("no completion postings format found");
  }

  static IndexWriterConfig iwc(Analyzer analyzer, final PostingsFormat completion) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    final Codec def = Codec.getDefault();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (field.equals("suggest")) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  static Field newSuggestField(String value, int weight) throws Exception {
    Class<?> c = Class.forName(PKG + "SuggestField");
    try {
      Constructor<?> k = c.getConstructor(String.class, String.class, int.class);
      return (Field) k.newInstance("suggest", value, weight);
    } catch (NoSuchMethodException e) {
      Constructor<?> k = c.getConstructor(String.class, String.class, long.class);
      return (Field) k.newInstance("suggest", value, (long) weight);
    }
  }

  static TopDocs lookup(IndexReader reader, Analyzer analyzer, String key) throws Exception {
    Class<?> sis = Class.forName(PKG + "SuggestIndexSearcher");
    Class<?> cq;
    try {
      cq = Class.forName(PKG + "CompletionQuery");
    } catch (ClassNotFoundException e) {
      cq = null;
    }
    if (cq == null) {
      // 5.1 / 5.2: SuggestIndexSearcher(IndexReader, Analyzer).suggest(field, key, num)
      Object s = sis.getConstructor(IndexReader.class, Analyzer.class).newInstance(reader, analyzer);
      Method m = sis.getMethod("suggest", String.class, CharSequence.class, int.class);
      apiUsed = "SuggestIndexSearcher(reader, analyzer).suggest(field, key, 100)";
      return (TopDocs) m.invoke(s, "suggest", key, 100);
    }
    Object s = sis.getConstructor(IndexReader.class).newInstance(reader);
    Object q =
        Class.forName(PKG + "PrefixCompletionQuery")
            .getConstructor(Analyzer.class, Term.class)
            .newInstance(analyzer, new Term("suggest", key));
    try {
      Method m = sis.getMethod("suggest", cq, int.class, boolean.class);
      apiUsed = "SuggestIndexSearcher.suggest(PrefixCompletionQuery, 100, skipDuplicates=false)";
      return (TopDocs) m.invoke(s, q, 100, false);
    } catch (NoSuchMethodException e) {
      Method m = sis.getMethod("suggest", cq, int.class);
      apiUsed = "SuggestIndexSearcher.suggest(PrefixCompletionQuery, 100)";
      return (TopDocs) m.invoke(s, q, 100);
    }
  }

  static int storedId(IndexReader reader, int doc) throws Exception {
    Document d;
    try {
      Object sf = IndexReader.class.getMethod("storedFields").invoke(reader);
      d = (Document) sf.getClass().getMethod("document", int.class).invoke(sf, doc);
    } catch (NoSuchMethodException e) {
      d = (Document) IndexReader.class.getMethod("document", int.class).invoke(reader, doc);
    }
    return d.getField("id").numericValue().intValue();
  }

  static String analyzedForm(Analyzer a, String value) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (TokenStream ts = a.tokenStream("suggest", value)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        if (sb.length() > 0) sb.append(' ');
        for (int i = 0; i < t.length(); i++) {
          char ch = t.charAt(i);
          if (ch < 0x20) sb.append(String.format("<U+%04X>", (int) ch));
          else sb.append(ch);
        }
      }
      ts.end();
    }
    return sb.toString();
  }

  static String show(String v) {
    StringBuilder sb = new StringBuilder();
    for (char ch : v.toCharArray()) {
      if (ch < 0x20) sb.append(String.format("\\u%04X", (int) ch));
      else sb.append(ch);
    }
    return sb.toString();
  }

  /** IndexWriter#isOpen exists from 5.1 on; 5.0 has no document suggester anyway. */
  static String isOpen(IndexWriter w) {
    try {
      return String.valueOf(IndexWriter.class.getMethod("isOpen").invoke(w));
    } catch (Exception e) {
      return "unknown";
    }
  }

  /** Returns "OK", "REJECTED_AT_ADD ...", "MISS ...", or "FLUSH_FAILED ...". */
  static String scenario(String... values) throws Exception {
    Analyzer analyzer = htmlStripKeyword();
    PostingsFormat completion = completionFormat();
    Path tmp = Files.createTempDirectory("sfhist");
    StringBuilder analyzed = new StringBuilder();
    for (String v : values) {
      if (analyzed.length() > 0) analyzed.append(", ");
      analyzed.append(analyzedForm(analyzer, v));
    }
    try (Directory dir = new NIOFSDirectory(tmp)) {
      IndexWriter writer = new IndexWriter(dir, iwc(analyzer, completion));
      List<Integer> accepted = new ArrayList<>();
      List<String> rejected = new ArrayList<>();
      for (int i = 0; i < values.length; i++) {
        try {
          Document doc = new Document();
          doc.add(new StoredField("id", i));
          doc.add(newSuggestField(values[i], i + 1));
          writer.addDocument(doc);
          accepted.add(i);
        } catch (java.lang.reflect.InvocationTargetException e) {
          if (e.getCause() instanceof IllegalArgumentException) {
            rejected.add(i + ":" + e.getCause().getMessage().replace('\u0000', '0'));
          } else {
            throw e;
          }
        } catch (IllegalArgumentException e) {
          rejected.add(i + ":" + e.getMessage().replace('\u0000', '0'));
        }
      }
      String prefix =
          "[format=" + completion.getName() + "; analyzed: " + analyzed + "; accepted=" + accepted
              + (rejected.isEmpty() ? "" : "; rejected=" + rejected) + "] ";
      try {
        writer.commit();
      } catch (Throwable t) {
        String msg = String.valueOf(t);
        Throwable c = t.getCause();
        if (c != null) msg += " / cause " + c;
        return prefix + "FLUSH_FAILED writerOpen=" + isOpen(writer) + " " + msg.replace('\n', ' ');
      }
      List<String> misses = new ArrayList<>();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        for (int i : accepted) {
          TopDocs hits = lookup(reader, analyzer, values[i]);
          TreeSet<Integer> found = new TreeSet<>();
          for (ScoreDoc sd : hits.scoreDocs) {
            found.add(storedId(reader, sd.doc));
          }
          if (!found.contains(i)) {
            misses.add("doc " + i + " '" + show(values[i]) + "' not found, got " + found);
          }
        }
      }
      writer.close();
      if (!misses.isEmpty()) return prefix + "MISS " + misses;
      if (!rejected.isEmpty()) return prefix + "REJECTED_AT_ADD (all accepted docs found)";
      return prefix + "OK";
    }
  }

  public static void main(String[] args) throws Exception {
    try {
      Class.forName(PKG + "SuggestField");
    } catch (ClassNotFoundException e) {
      System.out.println("API_ABSENT: no " + PKG + "SuggestField in this release");
      System.out.println("VERDICT: API_ABSENT");
      return;
    }
    Map<String, String[]> bug = new LinkedHashMap<>();
    bug.put("S1_hidden_plain(a, a&#0;)", new String[] {"a", "a&#0;"});
    bug.put("S2_flush_order(a&#0;&#0;, a, a, b)", new String[] {"a&#0;&#0;", "a", "a", "b"});
    Map<String, String[]> ctl = new LinkedHashMap<>();
    ctl.put("C1_control(a, a&#1;)", new String[] {"a", "a&#1;"});
    ctl.put("C2_control(a&#1;&#1;, a, a, b)", new String[] {"a&#1;&#1;", "a", "a", "b"});
    Map<String, String[]> raw = new LinkedHashMap<>();
    raw.put("R1_raw_U+0000(a, a\\u0000)", new String[] {"a", "a\u0000"});

    boolean bugSeen = false;
    boolean controlsOk = true;
    for (Map.Entry<String, String[]> e : bug.entrySet()) {
      String r = scenario(e.getValue());
      System.out.println(e.getKey() + " -> " + r);
      if (r.contains("] MISS") || r.contains("] FLUSH_FAILED")) bugSeen = true;
    }
    for (Map.Entry<String, String[]> e : ctl.entrySet()) {
      String r = scenario(e.getValue());
      System.out.println(e.getKey() + " -> " + r);
      if (!r.endsWith("] OK")) controlsOk = false;
    }
    for (Map.Entry<String, String[]> e : raw.entrySet()) {
      String r = scenario(e.getValue());
      System.out.println(e.getKey() + " -> " + r);
    }
    System.out.println("API: " + apiUsed);
    if (!controlsOk) {
      System.out.println("VERDICT: INCONCLUSIVE (a U+0001 control failed)");
      System.exit(2);
    }
    if (bugSeen) {
      System.out.println("VERDICT: BUG (analyzed U+0000 hides or breaks suggestions; controls OK)");
      System.exit(1);
    }
    System.out.println("VERDICT: NOT_AFFECTED");
  }
}

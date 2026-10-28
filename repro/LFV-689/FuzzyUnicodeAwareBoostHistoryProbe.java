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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.search.suggest.document.FuzzyCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestField;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;

/**
 * Version-portable history probe (Lucene 5.3 .. main) for: FuzzyCompletionQuery with
 * unicodeAware=true compares the query's code points with the UTF-8 byte labels of the matched
 * prefix, so its similarity boost is wrong for non-ASCII text.
 *
 * <p>Oracle (no absolute numbers needed): in unicodeAware mode every letter is one unit, so
 * consistently renaming ASCII letters to single non-ASCII code points must not change any score.
 * Each case runs an ASCII instance and its renamed non-ASCII twin and compares the (key, score)
 * multisets after mapping the keys back. Exit 0 = every twin agrees (no bug), exit 1 = a twin
 * differs (bug present).
 *
 * <p>Only public API is used. API differences between releases (newest completion postings format,
 * the 2- or 3-argument SuggestIndexSearcher#suggest) are bridged by reflection; the last
 * constructor argument (maxDeterminizedStates before 9.0, determinizeWorkLimit after) is 10000 in
 * both cases, the default of either constant.
 */
public class FuzzyUnicodeAwareBoostHistoryProbe {

  static final String FIELD = "suggest_field";

  static PostingsFormat newestCompletionFormat() throws Exception {
    String[] names = {
      "Completion104PostingsFormat",
      "Completion101PostingsFormat",
      "Completion912PostingsFormat",
      "Completion99PostingsFormat",
      "Completion90PostingsFormat",
      "Completion84PostingsFormat",
      "Completion50PostingsFormat"
    };
    for (String n : names) {
      try {
        Class<?> c = Class.forName("org.apache.lucene.search.suggest.document." + n);
        return (PostingsFormat) c.getDeclaredConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        // try the next older one
      }
    }
    throw new IllegalStateException("no completion postings format found");
  }

  static TopSuggestDocs suggest(SuggestIndexSearcher s, CompletionQuery q, int n)
      throws Exception {
    try {
      Method m =
          SuggestIndexSearcher.class.getMethod(
              "suggest", CompletionQuery.class, int.class, boolean.class);
      return (TopSuggestDocs) m.invoke(s, q, n, false);
    } catch (NoSuchMethodException e) {
      Method m = SuggestIndexSearcher.class.getMethod("suggest", CompletionQuery.class, int.class);
      return (TopSuggestDocs) m.invoke(s, q, n);
    }
  }

  /** Indexes the suggestions, runs a unicode-aware fuzzy query (maxEdits 1), returns key/score. */
  static List<String> run(String[] values, int[] weights, String key) throws Exception {
    final PostingsFormat completion = newestCompletionFormat();
    final Codec def = Codec.getDefault();
    Codec codec =
        new FilterCodec(def.getName(), def) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return completion;
              }
            };
          }
        };
    Analyzer analyzer = new WhitespaceAnalyzer();
    Path tmp = Files.createTempDirectory("fuzzyua");
    List<String> out = new ArrayList<>();
    Directory dir = new NIOFSDirectory(tmp);
    try {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(codec);
      IndexWriter w = new IndexWriter(dir, iwc);
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new SuggestField(FIELD, values[i], weights[i]));
        w.addDocument(doc);
      }
      w.commit();
      w.close();
      DirectoryReader reader = DirectoryReader.open(dir);
      try {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        FuzzyCompletionQuery query =
            new FuzzyCompletionQuery(
                analyzer,
                new Term(FIELD, key),
                null,
                1, // maxEdits
                true, // transpositions
                1, // nonFuzzyPrefix
                3, // minFuzzyLength
                true, // unicodeAware
                10000); // maxDeterminizedStates (<9.0) / determinizeWorkLimit (>=9.0)
        for (TopSuggestDocs.SuggestScoreDoc hit : suggest(searcher, query, 10).scoreLookupDocs()) {
          out.add(hit.key + "/" + hit.score);
        }
      } finally {
        reader.close();
      }
    } finally {
      dir.close();
    }
    return out;
  }

  static String mapBack(String keyScore, Map<Integer, Integer> back) {
    StringBuilder sb = new StringBuilder();
    keyScore
        .codePoints()
        .forEach(
            cp -> {
              Integer m = back.get(cp);
              sb.appendCodePoint(m == null ? cp : m);
            });
    return sb.toString();
  }

  /** Runs one ASCII case and its renamed twin; returns true if the twin agrees. */
  static boolean twin(
      String name,
      String[] asciiValues,
      int[] weights,
      String asciiKey,
      String[] uniValues,
      String uniKey,
      Map<Integer, Integer> back)
      throws Exception {
    List<String> ascii = run(asciiValues, weights, asciiKey);
    List<String> uni = run(uniValues, weights, uniKey);
    List<String> uniMapped = new ArrayList<>();
    for (String s : uni) {
      uniMapped.add(mapBack(s, back));
    }
    List<String> a = new ArrayList<>(ascii);
    List<String> b = new ArrayList<>(uniMapped);
    Collections.sort(a);
    Collections.sort(b);
    boolean ok = a.equals(b);
    System.out.println(
        "CASE "
            + name
            + ": ascii query '"
            + asciiKey
            + "' -> "
            + ascii
            + " | unicode query '"
            + uniKey
            + "' -> "
            + uni
            + " (mapped back: "
            + uniMapped
            + ") => "
            + (ok ? "SAME" : "DIFFERENT"));
    return ok;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Lucene Version.LATEST = " + Version.LATEST);
    System.out.println("completion postings format = " + newestCompletionFormat().getName());

    // case 1: 'b' -> 'é' (U+00E9, 2 UTF-8 bytes), one non-ASCII letter inside an ASCII word
    Map<Integer, Integer> back1 = new HashMap<>();
    back1.put((int) 'é', (int) 'b');
    boolean ok1 =
        twin(
            "latin-e-acute",
            new String[] {"abcd", "axcd"},
            new int[] {3, 4},
            "abc",
            new String[] {"aécd", "axcd"},
            "aéc",
            back1);

    // case 2: every letter non-ASCII (3 UTF-8 bytes each): 日->a 本->b 語->c 大->x
    Map<Integer, Integer> back2 = new HashMap<>();
    back2.put((int) '日', (int) 'a');
    back2.put((int) '本', (int) 'b');
    back2.put((int) '語', (int) 'c');
    back2.put((int) '大', (int) 'x');
    boolean ok2 =
        twin(
            "cjk",
            new String[] {"abc", "axc"},
            new int[] {1, 1},
            "abc",
            new String[] {"日本語", "日大語"},
            "日本語",
            back2);

    if (ok1 && ok2) {
      System.out.println("RESULT: PASS (unicode-aware boost is invariant under letter renaming)");
      System.exit(0);
    }
    System.out.println(
        "RESULT: FAIL (unicode-aware boost changes when an ASCII letter is renamed to one non-ASCII"
            + " code point: query code points are compared with UTF-8 byte labels)");
    System.exit(1);
  }
}

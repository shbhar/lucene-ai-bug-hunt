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
package org.apache.lucene.analysis.bn;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;

/**
 * Prints, for a fixed list of words, the BengaliNormalizationFilter output (and a second pass over
 * it), the BengaliAnalyzer term, and a tiny search over the three spellings of one word. Run it
 * with the stock jar and with a patched BengaliNormalizer to compare fix options. Public API only.
 * Each normalization runs with a 2 s timeout, so a variant that loops forever is reported as HANG.
 * Usage: VisargaOptionsProbe [label]
 */
public class VisargaOptionsProbe {
  static final ExecutorService POOL =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
          });

  static String cps(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) b.append(' ');
      b.append(String.format("%04X", (int) s.charAt(i)));
    }
    return b.toString();
  }

  static String filter(String in) throws Exception {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(in));
    try (TokenStream ts = new BengaliNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      if (!ts.incrementToken()) return "";
      String s = term.toString();
      ts.end();
      return s;
    }
  }

  static String terms(Analyzer a, String in) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", in)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return String.join(",", out);
  }

  static String timed(java.util.concurrent.Callable<String> c) throws Exception {
    Future<String> f = POOL.submit(c);
    try {
      return f.get(2, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      f.cancel(true);
      return "HANG (no result after 2 s)";
    }
  }

  public static void main(String[] args) throws Exception {
    String label = args.length > 0 ? args[0] : "run";
    String[][] words = {
      {"নিঃশব্দ", "visarga + SHA (silent)"},
      {"নিঃষব্দ", "visarga + SSA"},
      {"নিঃসব্দ", "visarga + SA"},
      {"নিঃশেষ", "visarga + SHA (exhausted)"},
      {"নিঃশ্বাস", "visarga + SHA + Ba-phalaa (breath)"},
      {"নিঃস্বাস", "visarga + SA + Ba-phalaa"},
      {"দুঃণা", "visarga + NNA (synthetic)"},
      {"দুঃনা", "visarga + NA (synthetic)"},
      {"বিঃশ", "visarga gemination of SHA"},
      {"বিশ্ব", "Ba-phalaa gemination of SHA (upstream test)"},
      {"শব্দ", "SHA, no visarga"},
      {"সব্দ", "SA, no visarga"},
      {"দুঃখ", "visarga + KHA (upstream test)"},
      {"উঃ", "final visarga (upstream test)"},
      {"পুনঃ", "final visarga (upstream test)"},
      {"১৬ঃ৯", "visarga used as a colon between digits"},
      {"কঃঃক", "doubled visarga (malformed)"},
    };
    Analyzer a = new BengaliAnalyzer();
    System.out.println("PROBE " + label + " (filter = BengaliNormalizationFilter on one keyword token)");
    for (String[] w : words) {
      String in = w[0];
      String once = timed(() -> filter(in));
      String twice = once.startsWith("HANG") ? "-" : timed(() -> filter(once));
      String term = timed(() -> terms(a, in));
      System.out.println(
          String.format(
              "%-10s [%s] %s%n    filter: %s [%s]   second pass: %s%n    BengaliAnalyzer: %s [%s]",
              in,
              cps(in),
              w[1],
              once,
              once.startsWith("HANG") ? "-" : cps(once),
              twice.equals(once) ? "unchanged" : twice + " [" + cps(twice) + "]",
              term,
              term.startsWith("HANG") ? "-" : cps(term)));
    }

    // tiny index: the three spellings of "silent", one document each, searched with each spelling
    String[] spellings = {"নিঃশব্দ", "নিঃষব্দ", "নিঃসব্দ"};
    String[] names = {"SHA", "SSA", "SA"};
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (int i = 0; i < spellings.length; i++) {
          Document d = new Document();
          d.add(new StringField("id", names[i], Field.Store.YES));
          d.add(new TextField("body", spellings[i], Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        StoredFields sf = s.storedFields();
        QueryBuilder qb = new QueryBuilder(a);
        for (int i = 0; i < spellings.length; i++) {
          Query q = qb.createBooleanQuery("body", spellings[i]);
          List<String> hits = new ArrayList<>();
          for (ScoreDoc sd : s.search(q, 10).scoreDocs) hits.add(sf.document(sd.doc).get("id"));
          System.out.println(
              "SEARCH " + label + " query " + names[i] + " spelling " + spellings[i] + " -> "
                  + hits.size() + " of 3 documents " + hits);
        }
      }
    }
    a.close();
    System.exit(0);
  }
}

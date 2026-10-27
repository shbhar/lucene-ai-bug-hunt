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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * StandardAnalyzer (default settings) on a text value that ends with an unpaired high surrogate,
 * the first half of an emoji. Public API, lucene-core only. Exits 1 if the words are lost.
 */
public class TrailingHighSurrogate {

  static final String HALF_EMOJI = "\uD83D"; // first half of U+1F600, which is "😀"

  public static void main(String[] args) throws Exception {
    boolean ok = true;
    try (Analyzer a = new StandardAnalyzer()) {
      ok &= check(a, "hello world"); // control
      ok &= check(a, "hello world" + HALF_EMOJI);
      ok &= check(a, "hello world 😀 more words".substring(0, 13)); // cut inside the emoji
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
          for (String title : new String[] {"hello world", "hello world" + HALF_EMOJI}) {
            Document doc = new Document();
            doc.add(new TextField("title", title, Field.Store.YES));
            w.addDocument(doc);
          }
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          int hits = new IndexSearcher(r).count(new TermQuery(new Term("title", "hello")));
          System.out.println("2 documents, both titles start with 'hello'; title:hello matches " + hits);
          ok &= hits == 2;
        }
      }
    }
    System.out.println(ok ? "OK" : "BUG: words lost");
    System.exit(ok ? 0 : 1);
  }

  static boolean check(Analyzer a, String text) throws Exception {
    List<String> terms = new ArrayList<>();
    int finalOffset;
    try (TokenStream ts = a.tokenStream("title", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
      finalOffset = offset.endOffset();
    }
    System.out.println(show(text) + " (" + text.length() + " chars) -> " + terms + ", final offset " + finalOffset);
    return terms.equals(List.of("hello", "world")) && finalOffset == text.length();
  }

  /** Prints unpaired surrogates as <U+XXXX>. */
  static String show(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isSurrogate(c)) {
        b.append(String.format("<U+%04X>", (int) c));
      } else {
        b.append(c);
      }
    }
    return b.append('"').toString();
  }
}

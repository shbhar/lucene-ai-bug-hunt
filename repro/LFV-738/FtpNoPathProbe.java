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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.email.UAX29URLEmailAnalyzer;
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
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
import org.apache.lucene.util.QueryBuilder;

/**
 * Prints the tokens UAX29URLEmailTokenizer emits for ftp URLs with and without a path (and http
 * controls), then counts hits for an exact-URL term query and for the same URL analyzed as a phrase
 * query, over a one-document index. Public API only; run it against the stock jars and against
 * jars with the grammar fix.
 */
public class FtpNoPathProbe {
  static final String[] INPUTS = {
    "get it from ftp://ftp.example.com today",
    "ftp://ftp.example.com",
    "ftp://ftp.example.com/",
    "ftp://ftp.example.com/pub/x.txt",
    "ftp://mirror.example.org:2121",
    "ftp://localhost",
    "ftp://192.0.2.10",
    "see ftp://192.0.2.10 now",
    "ftp://anonymous@ftp.example.org",
    "ftp://user:secret@ftp.example.com:21",
    "ftp://ftp.example.com#top",
    "ftp://[::1]",
    "http://localhost",
    "http://192.0.2.10",
    "file://host",
    "file://host/",
    // edge cases around the authority (compare with the http lines)
    "see ftp://ftp.example.com.",
    "see http://ftp.example.com.",
    "(ftp://ftp.example.com), next",
    "(http://ftp.example.com), next",
    "ftp://",
    "ftp://-x",
    "ftp://ftp.example.com;type=i",
  };

  static String tokens(Analyzer analyzer, String text) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        sb.append('[').append(term).append(' ').append(type.type()).append("] ");
      }
      ts.end();
    }
    return sb.toString().trim();
  }

  public static void main(String[] args) throws Exception {
    Analyzer tokenizerOnly =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new UAX29URLEmailTokenizer());
          }
        };
    for (String in : INPUTS) {
      System.out.println("TOKENIZER " + in + " -> " + tokens(tokenizerOnly, in));
    }
    try (Analyzer analyzer = new UAX29URLEmailAnalyzer()) {
      for (String in : new String[] {"FTP://ftp.example.org", "HTTP://www.example.org"}) {
        System.out.println("ANALYZER  " + in + " -> " + tokens(analyzer, in));
      }
      // One document per URL; an exact-URL term query, as a link lookup would issue it.
      String[] urls = {"ftp://ftp.example.org", "ftp://ftp.example.org/", "http://www.example.org"};
      for (String url : urls) {
        try (Directory dir = new ByteBuffersDirectory();
            IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
          Document doc = new Document();
          doc.add(new TextField("body", new StringReader("mirror at " + url + " today")));
          w.addDocument(doc);
          w.commit();
          try (DirectoryReader r = DirectoryReader.open(dir)) {
            int hits = new IndexSearcher(r).count(new TermQuery(new Term("body", url)));
            System.out.println("SEARCH    TermQuery(body:" + url + ") -> " + hits + " of 1");
            // The same URL analyzed as a query (what a query parser does): the split repeats.
            int phraseHits =
                new IndexSearcher(r).count(new QueryBuilder(analyzer).createPhraseQuery("body", url));
            System.out.println("SEARCH    analyzed phrase query \"" + url + "\" -> " + phraseHits + " of 1");
          }
        }
      }
    }
    tokenizerOnly.close();
  }
}

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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The class-javadoc example of {@link TermInSetQuery} must compile against the current Lucene jars. It is
 * compiled verbatim with the JDK compiler API; only API-level errors fail a test.
 */
public class TestTermInSetQueryJavadocExample extends LuceneTestCase {

  static final String DOC = "TermInSetQuery.java";

  /** The class-javadoc example (TermInSetQuery.java lines 51-57). */
  public void testClassJavadocExample() throws Exception {
    assertCompiles(
        "TermInSetQuery class-javadoc example",
        new Unit("ex/Ex51.java",
      "package ex;\nimport java.util.List;\nimport org.apache.lucene.index.Term;\nimport org.apache.lucene.search.*;\nimport org.apache.lucene.search.BooleanClause.Occur;\nimport org.apache.lucene.util.BytesRef;\nclass Ex51 {\n  void example() {\n",
      51,
      new String[] {
        "Query q1 = new TermInSetQuery(\"field\", new BytesRef(\"foo\"), new BytesRef(\"bar\"));",
        "",
        "BooleanQuery bq = new BooleanQuery();",
        "bq.add(new TermQuery(new Term(\"field\", \"foo\")), Occur.SHOULD);",
        "bq.add(new TermQuery(new Term(\"field\", \"bar\")), Occur.SHOULD);",
        "Query q2 = new ConstantScoreQuery(bq);"
      },
      "  }\n}\n"));
  }

  /** Control: the same example written against the current API compiles in the same harness. */
  public void testCorrectedExampleControl() throws Exception {
    assertCompiles(
        "corrected example (control)",
        new Unit("ex/Fixed.java",
      "package ex;\nimport java.util.List;\nimport org.apache.lucene.index.Term;\nimport org.apache.lucene.search.*;\nimport org.apache.lucene.search.BooleanClause.Occur;\nimport org.apache.lucene.util.BytesRef;\nclass Fixed {\n  void example() {\n",
      51,
      new String[] {
        "Query q1 = new TermInSetQuery(\"field\", List.of(new BytesRef(\"foo\"), new BytesRef(\"bar\")));",
        "",
        "BooleanQuery.Builder bq = new BooleanQuery.Builder();",
        "bq.add(new TermQuery(new Term(\"field\", \"foo\")), Occur.SHOULD);",
        "bq.add(new TermQuery(new Term(\"field\", \"bar\")), Occur.SHOULD);",
        "Query q2 = new ConstantScoreQuery(bq.build());"
      },
      "  }\n}\n"));
  }

  /** Control: the corrected q1 and q2 give the same hits and scores, as the javadoc says. */
  public void testCorrectedExampleSameScoresControl() throws Exception {
    try (org.apache.lucene.store.Directory dir = newDirectory();
        org.apache.lucene.tests.index.RandomIndexWriter w =
            new org.apache.lucene.tests.index.RandomIndexWriter(random(), dir)) {
      for (String v : new String[] {"foo", "bar", "baz", "foo"}) {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        doc.add(new org.apache.lucene.document.StringField("field", v, org.apache.lucene.document.Field.Store.NO));
        w.addDocument(doc);
      }
      try (org.apache.lucene.index.IndexReader r = w.getReader()) {
        IndexSearcher s = newSearcher(r);
        Query q1 = new TermInSetQuery("field", List.of(new BytesRef("foo"), new BytesRef("bar")));
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term("field", "foo")), BooleanClause.Occur.SHOULD);
        bq.add(new TermQuery(new Term("field", "bar")), BooleanClause.Occur.SHOULD);
        Query q2 = new ConstantScoreQuery(bq.build());
        org.apache.lucene.tests.search.CheckHits.checkEqual(q1, s.search(q1, 10).scoreDocs, s.search(q2, 10).scoreDocs);
        assertEquals(3, s.count(q1));
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // compile harness: javac (javax.tools) in-process, against this JVM's class path
  // ---------------------------------------------------------------------------------------------

  /** One compilation unit: header + verbatim documentation lines + footer. */
  record Unit(String path, String header, int docFirstLine, String[] doc, String footer) {
    String text() {
      return header + String.join("\n", doc) + "\n" + footer;
    }

    int firstDocLine() {
      return (int) header.chars().filter(c -> c == '\n').count() + 1;
    }
  }

  static boolean isApiError(String msg) {
    return msg.matches("(?s).*location: (class|interface|variable \\w+ of type) org\\.apache\\.lucene\\..*")
        || (msg.matches("(?s).*no suitable (constructor|method) found for.*") && msg.contains("org.apache.lucene"))
        || msg.matches("(?s).*(constructor|method) \\w+ in (class|interface) org\\.apache\\.lucene\\S* cannot be applied.*")
        || msg.matches("(?s).*cannot inherit from final org\\.apache\\.lucene\\..*")
        || msg.matches("(?s).*does not override abstract method .* in org\\.apache\\.lucene\\..*")
        || msg.matches("(?s).*incompatible types: org\\.apache\\.lucene\\S* cannot be converted to.*");
  }

  /** Compiles the units together; fails if javac reports an API-level error. */
  static void assertCompiles(String what, Unit... units) throws IOException {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertNotNull("this JVM has no system Java compiler", javac);
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    Path out = createTempDir("repro-classes");
    List<JavaFileObject> srcs = new ArrayList<>();
    Map<String, Unit> byUri = new HashMap<>();
    for (Unit u : units) {
      URI uri = URI.create("string:///" + u.path());
      byUri.put(uri.toString(), u);
      String text = u.text();
      srcs.add(
          new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
              return text;
            }
          });
    }
    try (StandardJavaFileManager fm = javac.getStandardFileManager(diags, Locale.ROOT, null)) {
      List<String> opts =
          List.of(
              "-classpath", System.getProperty("java.class.path"),
              "-d", out.toString(),
              "-proc:none", "-nowarn", "-Xlint:none", "-Xmaxerrs", "200");
      javac.getTask(null, fm, diags, opts, null, srcs).call();
    }
    List<String> api = new ArrayList<>();
    List<String> other = new ArrayList<>();
    for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
      if (d.getKind() != Diagnostic.Kind.ERROR) {
        continue;
      }
      Unit u = d.getSource() == null ? null : byUri.get(d.getSource().toUri().toString());
      long line = d.getLineNumber();
      String where =
          u != null && line >= u.firstDocLine() && line < u.firstDocLine() + u.doc().length
              ? DOC + ":" + (u.docFirstLine() + (line - u.firstDocLine()))
              : (u == null ? "?" : u.path()) + "(harness line " + line + ")";
      String msg = d.getMessage(Locale.ROOT).replace('\n', ' ');
      (isApiError(msg) ? api : other).add(where + ": " + msg);
    }
    if (api.isEmpty() == false) {
      fail(
          what
              + " names API that does not exist on this Lucene ("
              + api.size()
              + " API-level errors):\n  "
              + String.join("\n  ", api)
              + (other.isEmpty()
                  ? ""
                  : "\n  (other, non-API errors: " + String.join(" | ", other) + ")"));
    }
  }
}

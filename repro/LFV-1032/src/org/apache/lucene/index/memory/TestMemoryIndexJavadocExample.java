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
package org.apache.lucene.index.memory;

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMemoryIndexJavadocExample extends LuceneTestCase {

  // Expected: the class-javadoc example compiles; actual: no SimpleAnalyzer(Version) / QueryParser(Version, String, Analyzer).
  public void testClassJavadocExampleCompiles() throws Exception {
    String source =
        """
        import org.apache.lucene.analysis.Analyzer;
        import org.apache.lucene.analysis.core.SimpleAnalyzer;
        import org.apache.lucene.index.memory.MemoryIndex;
        import org.apache.lucene.queryparser.classic.QueryParser;
        import org.apache.lucene.util.Version;
        class Example {
          void example(Version version) throws Exception {
        Analyzer analyzer = new SimpleAnalyzer(version);
        MemoryIndex index = new MemoryIndex();
        index.addField("content", "Readings about Salmons and other select Alaska fishing Manuals", analyzer);
        index.addField("author", "Tales of James", analyzer);
        QueryParser parser = new QueryParser(version, "content", analyzer);
        float score = index.search(parser.parse("+author:james +salmon~ +fish* manual~"));
        if (score > 0.0f) {
            System.out.println("it's a match");
        } else {
            System.out.println("no match found");
        }
        System.out.println("indexData=" + index.toString());
          }
        }
        """;
    JavaFileObject unit =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-classpath", System.getProperty("java.class.path"),
            "-d", createTempDir().toString(),
            "-proc:none");
    boolean compiled =
        ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, options, null, List.of(unit))
            .call();
    assertTrue(diagnostics.getDiagnostics().toString(), compiled);
  }
}

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
package org.apache.lucene.queryparser.flexible.standard;

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStandardQueryParserJavadocExamples extends LuceneTestCase {

  // expected: the class and constructor javadoc examples compile; actual: javac rejects 4 lines
  public void testJavadocExamplesCompile() throws Exception {
    String src =
        """
        import org.apache.lucene.analysis.Analyzer;
        import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
        import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
        import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler;
        import org.apache.lucene.search.Query;
        class Example {
          void classExample() throws Exception {
            StandardQueryParser qpHelper = new StandardQueryParser();
            StandardQueryConfigHandler config =  qpHelper.getQueryConfigHandler();
            config.setAllowLeadingWildcard(true);
            config.setAnalyzer(new WhitespaceAnalyzer());
            Query query = qpHelper.parse("apache AND lucene", "defaultField");
          }
          void constructorExample(Analyzer analyzer) {
            StandardQueryParser qp = new StandardQueryParser();
            qp.getQueryConfigHandler().setAnalyzer(analyzer);
          }
        }
        """;
    JavaFileObject unit =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return src;
          }
        };
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-cp", System.getProperty("java.class.path"),
            "-d", createTempDir().toString(),
            "-proc:none");
    boolean compiled =
        ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, options, null, List.of(unit))
            .call();
    assertTrue(diagnostics.getDiagnostics().toString(), compiled);
  }
}

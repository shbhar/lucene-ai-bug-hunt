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
package org.apache.lucene.monitor;

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestConcurrentQueryLoaderJavadocExample extends LuceneTestCase {

  // Expected: the class-javadoc example compiles; actual: javac says cannot find symbol class QueryError.
  public void testClassJavadocExampleCompiles() throws Exception {
    String source =
        """
        import java.util.ArrayList;
        import java.util.List;
        import org.apache.lucene.monitor.*;
        abstract class Example {
          abstract List<MonitorQuery> getQueries();
          void example(Monitor monitor) throws Exception {
            List<QueryError> errors = new ArrayList<>();
            try (ConcurrentQueryLoader loader = new ConcurrentQueryLoader(monitor, errors)) {
                for (MonitorQuery mq : getQueries()) {
                    loader.add(mq);
                }
            }
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

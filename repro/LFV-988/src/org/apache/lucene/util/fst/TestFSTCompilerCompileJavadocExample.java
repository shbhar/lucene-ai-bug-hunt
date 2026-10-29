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
package org.apache.lucene.util.fst;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFSTCompilerCompileJavadocExample extends LuceneTestCase {

  public void testOffHeapExampleCompiles() throws Exception {
    String source =
        """
        import java.io.IOException;
        import org.apache.lucene.store.DataInput;
        import org.apache.lucene.util.fst.*;

        class Example {
          void example(FSTCompiler<Long> fstCompiler, DataInput dataInput) throws IOException {
            FST.FSTMetadata<Long> fstMetadata;
            FST<Long> fst;
            fstMetadata = fstCompiler.compile();
            fst = new FST<>(fstMetadata, dataInput, new OffHeapFSTStore());
          }
        }
        """;
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaFileObject file =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    List<String> options =
        List.of(
            "-classpath", System.getProperty("java.class.path"), "-d", createTempDir().toString());
    javac.getTask(null, null, diagnostics, options, null, List.of(file)).call();
    List<String> errors =
        diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(d -> d.getMessage(Locale.ROOT))
            .toList();
    // expected: the example at FSTCompiler.java:992-993 compiles; actual: no OffHeapFSTStore()
    assertEquals("javac errors in the FSTCompiler.compile() javadoc example", List.of(), errors);
  }
}

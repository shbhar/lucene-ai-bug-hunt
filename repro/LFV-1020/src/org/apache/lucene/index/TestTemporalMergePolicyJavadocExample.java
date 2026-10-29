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
package org.apache.lucene.index;

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTemporalMergePolicyJavadocExample extends LuceneTestCase {

  // Expected: the TemporalMergePolicy class-javadoc example compiles; actual: javac cannot find setBaseTimeSeconds(int).
  public void testClassJavadocExampleCompiles() throws Exception {
    String source =
        """
        import org.apache.lucene.analysis.Analyzer;
        import org.apache.lucene.index.IndexWriterConfig;
        import org.apache.lucene.index.TemporalMergePolicy;
        class Example {
          void example(Analyzer analyzer) {
        TemporalMergePolicy policy = new TemporalMergePolicy()
            .setTemporalField("timestamp")           // Required: name of the timestamp field
            .setBaseTimeSeconds(3600)                // Base window size: 1 hour
            .setMinThreshold(4)                      // Merge when 4+ segments in a window
            .setMaxThreshold(8)                      // Merge at most 8 segments at once
            .setCompactionRatio(1.2);                // Size ratio threshold for merging
            // By default, exponential buckets are enabled. Use .disableExponentialBuckets() to disable.

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setMergePolicy(policy);
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

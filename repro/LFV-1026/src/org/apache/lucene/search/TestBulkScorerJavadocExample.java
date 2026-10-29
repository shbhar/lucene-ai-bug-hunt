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

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBulkScorerJavadocExample extends LuceneTestCase {

  // Expected: the BulkScorer#score javadoc example compiles; actual: javac cannot find Scorer.advance(int) and Scorer.nextDoc().
  public void testScoreJavadocExampleCompiles() throws Exception {
    String source =
        """
        import java.io.IOException;
        import org.apache.lucene.search.*;
        import org.apache.lucene.util.Bits;
        abstract class Example extends BulkScorer {
          Example(Scorer scorer) {
            this.scorer = scorer;
          }
        private final Scorer scorer; // set via constructor

        public int score(LeafCollector collector, Bits acceptDocs, int min, int max) throws IOException {
          collector.setScorer(scorer);
          int doc = scorer.docID();
          if (doc < min) {
            doc = scorer.advance(min);
          }
          while (doc < max) {
            if (acceptDocs == null || acceptDocs.get(doc)) {
              collector.collect(doc);
            }
            doc = scorer.nextDoc();
          }
          return doc;
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

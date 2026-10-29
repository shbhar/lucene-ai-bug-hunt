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
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFstPackageExamples extends LuceneTestCase {

  // Expected: the package-info.java construction, by-value and shortest-paths examples compile; actual: 6 javac errors.
  public void testPackageOverviewExamplesCompile() throws Exception {
    String source =
        """
        import java.util.Comparator;
        import org.apache.lucene.util.*;
        import org.apache.lucene.util.fst.*;
        import org.apache.lucene.util.fst.FST.Arc;
        import org.apache.lucene.util.fst.FST.INPUT_TYPE;
        class Example {
          void example() throws Exception {
        String inputValues[] = {"cat", "dog", "dogs"};
        long outputValues[] = {5, 7, 12};

        PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
        FSTCompiler<Long> fstCompiler = new FSTCompiler<Long>(INPUT_TYPE.BYTE1, outputs);
        BytesRefBuilder scratchBytes = new BytesRefBuilder();
        IntsRefBuilder scratchInts = new IntsRefBuilder();
        for (int i = 0; i < inputValues.length; i++) {
          scratchBytes.copyChars(inputValues[i]);
          fstCompiler.add(Util.toIntsRef(scratchBytes.toBytesRef(), scratchInts), outputValues[i]);
        }
        FST<Long> fst = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader());

        IntsRef key = Util.getByOutput(fst, 12);
        System.out.println(Util.toBytesRef(key, scratchBytes).utf8ToString()); // dogs

        Comparator<Long> comparator = new Comparator<Long>() {
          public int compare(Long left, Long right) {
            return left.compareTo(right);
          }
        };
        Arc<Long> firstArc = fst.getFirstArc(new Arc<Long>());
        Util.TopResults<Long> paths = Util.shortestPaths(fst, firstArc, fst.outputs.getNoOutput(), comparator, 3, true);
        System.out.println(Util.toBytesRef(paths.topN.get(0).input, scratchBytes).utf8ToString()); // cat
        System.out.println(paths.topN.get(0).output); // 5
        System.out.println(Util.toBytesRef(paths.topN.get(1).input, scratchBytes).utf8ToString()); // dog
        System.out.println(paths.topN.get(1).output); // 7
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

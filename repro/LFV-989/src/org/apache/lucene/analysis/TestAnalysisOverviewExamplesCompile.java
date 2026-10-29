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
package org.apache.lucene.analysis;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAnalysisOverviewExamplesCompile extends LuceneTestCase {

  private static final String EXAMPLES =
      """
      import org.apache.lucene.analysis.Analyzer;
      import org.apache.lucene.analysis.FilteringTokenFilter;
      import org.apache.lucene.analysis.TokenStream;
      import org.apache.lucene.analysis.Tokenizer;
      import org.apache.lucene.analysis.core.WhitespaceTokenizer;
      import org.apache.lucene.analysis.miscellaneous.LengthFilter;
      import org.apache.lucene.analysis.standard.StandardAnalyzer;
      import org.apache.lucene.util.AttributeImpl;
      import org.apache.lucene.util.Version;

      abstract class Examples extends Analyzer {
        Version matchVersion = Version.LATEST;
        Analyzer analyzer = new StandardAnalyzer(matchVersion);
        Analyzer myAnalyzer = new StandardAnalyzer(matchVersion) {
          public int getPositionIncrementGap(String fieldName) {
            return 10;
          }
        };

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          final Tokenizer source = new WhitespaceTokenizer(matchVersion);
          TokenStream result = new LengthFilter(true, source, 3, Integer.MAX_VALUE);
          return new TokenStreamComponents(source, result);
        }
      }

      class Listing {
        abstract class LengthFilter extends FilteringTokenFilter {
          public LengthFilter(Version version, TokenStream in, int min, int max) {
            super(version, in);
          }
        }
      }

      final class PartOfSpeechAttributeImpl extends AttributeImpl {
        @Override
        public void clear() {}

        @Override
        public void copyTo(AttributeImpl target) {}
      }
      """;

  public void testExamplesCompile() throws Exception {
    JavaFileObject source =
        new SimpleJavaFileObject(URI.create("string:///Examples.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return EXAMPLES;
          }
        };
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options = List.of("-proc:none", "-d", createTempDir().toString());
    ToolProvider.getSystemJavaCompiler()
        .getTask(null, null, diagnostics, options, null, List.of(source))
        .call();
    List<String> errors =
        diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT))
            .toList();
    // lines copied from the analysis package-info.java examples (LUCENE_XY -> LATEST) must compile
    assertEquals(List.of(), errors);
  }
}

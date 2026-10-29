#!/usr/bin/env python3
"""Generate the compile-harness JUnit repros for javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904 from the main sources (verbatim example text).

Usage: gen_tests.py <lucene-checkout> <unit-dir>
Writes:
  candidates/icu-overview-examples-call-removed-apis/repro/src/org/apache/lucene/analysis/icu/TestIcuOverviewExamples.java
  candidates/porterstemfilter-javadoc-example-uses-removed-lowercasetokenizer/repro/src/org/apache/lucene/analysis/en/TestPorterStemFilterJavadocExample.java
The example text is embedded as Java string literals produced mechanically from extract_blocks.py output, so the
repro needs no source files at run time; check_embedded.py re-extracts and compares.
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def jlit(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def jarray(lines, indent='      '):
    return '{\n' + ',\n'.join(indent + jlit(l) for l in lines) + '\n    }'


def blocks(lucene, rel):
    out = subprocess.check_output([sys.executable, '-I', os.path.join(HERE, 'extract_blocks.py'), rel], cwd=lucene)
    return json.loads(out)


HARNESS = r'''
  // ---------------------------------------------------------------------------------------------
  // compile harness: javac (javax.tools) in-process, against this JVM's class path
  // ---------------------------------------------------------------------------------------------

  /** Compiles one compilation unit; returns the error diagnostics mapped to documentation lines. */
  static List<String> compile(
      String className, String unit, int unitFirstSnippetLine, int docFirstLine, String docFile)
      throws IOException {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertNotNull("this JVM has no system Java compiler", javac);
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    Path out = createTempDir("repro-classes");
    try (StandardJavaFileManager fm = javac.getStandardFileManager(diags, Locale.ROOT, null)) {
      JavaFileObject src =
          new SimpleJavaFileObject(
              URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
              return unit;
            }
          };
      List<String> opts =
          List.of(
              "-classpath", System.getProperty("java.class.path"),
              "-d", out.toString(),
              "-proc:none", "-nowarn", "-Xlint:none", "-Xmaxerrs", "200");
      javac.getTask(null, fm, diags, opts, null, List.of(src)).call();
    }
    List<String> errors = new ArrayList<>();
    for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
      if (d.getKind() != Diagnostic.Kind.ERROR) {
        continue;
      }
      long line = d.getLineNumber();
      String where =
          line >= unitFirstSnippetLine
              ? docFile + ":" + (docFirstLine + (line - unitFirstSnippetLine))
              : "harness-line-" + line;
      errors.add(where + ": " + d.getMessage(Locale.ROOT).replace('\n', ' '));
    }
    return errors;
  }

  static void assertNoErrors(String what, List<String> errors) {
    if (errors.isEmpty() == false) {
      fail(what + " does not compile on this Lucene (" + errors.size() + " errors):\n  "
          + String.join("\n  ", errors));
    }
  }
'''


def gen_icu(lucene, unit):
    rel = 'lucene/analysis/icu/src/java/overview.html'
    bl = blocks(lucene, rel)
    by_line = {b['start_line']: b for b in bl}
    # start_line -> (method name, wrapper parameters, expectation)
    plan = [
        (74, 'testIcuTokenizerExample', 'java.io.Reader reader', 'stale'),
        (114, 'testFarsiRangeQueryExample', '', 'stale'),
        (141, 'testDanishSortingExample', '', 'stale'),
        (169, 'testTurkishCaseNormalizationExample', '', 'stale'),
        (245, 'testNormalizer2ExampleControl', 'Tokenizer tokenizer', 'control'),
        (280, 'testCaseFoldingExampleControl', 'Tokenizer tokenizer', 'control'),
        (305, 'testSearchFoldingExampleControl', 'Tokenizer tokenizer', 'control'),
        (333, 'testTraditionalSimplifiedExampleControl', 'Tokenizer tokenizer', 'control'),
        (338, 'testSerbianLatinExample', 'Tokenizer tokenizer', 'stale'),
        (354, 'testUnicode50NormalizationExampleControl', 'Tokenizer tokenizer', 'control'),
    ]
    assert sorted(by_line) == sorted(p[0] for p in plan), sorted(by_line)
    consts, tests = [], []
    for line, name, params, kind in plan:
        b = by_line[line]
        cname = 'EX_L%d' % line
        consts.append('  /** overview.html lines %d-%d, verbatim. */\n  static final String[] %s = %s;\n'
                      % (line, line + len(b['lines']) - 1, cname, jarray(b['lines'])))
        doc = ('  /** Control: this example uses only current API and must compile. */\n' if kind == 'control'
               else '  /** overview.html example at line %d must compile on main. */\n' % line)
        tests.append(doc + '  public void %s() throws Exception {\n    assertExampleCompiles(%d, %s, %s);\n  }\n'
                     % (name, line, jlit(params), cname))
    imports = [
        'com.ibm.icu.text.Collator', 'com.ibm.icu.text.FilteredNormalizer2', 'com.ibm.icu.text.Normalizer2',
        'com.ibm.icu.text.Transliterator', 'com.ibm.icu.text.UnicodeSet', 'com.ibm.icu.util.ULocale',
        'java.nio.file.Files', 'java.nio.file.Path',
        'org.apache.lucene.analysis.Analyzer', 'org.apache.lucene.analysis.TokenStream',
        'org.apache.lucene.analysis.Tokenizer', 'org.apache.lucene.analysis.icu.ICUCollationKeyAnalyzer',
        'org.apache.lucene.analysis.icu.ICUFoldingFilter', 'org.apache.lucene.analysis.icu.ICUNormalizer2Filter',
        'org.apache.lucene.analysis.icu.ICUTransformFilter',
        'org.apache.lucene.analysis.icu.segmentation.ICUTokenizer', 'org.apache.lucene.document.Document',
        'org.apache.lucene.document.Field', 'org.apache.lucene.index.IndexWriter',
        'org.apache.lucene.index.IndexWriterConfig', 'org.apache.lucene.queryparser.classic.QueryParser',
        'org.apache.lucene.search.IndexSearcher', 'org.apache.lucene.search.MatchAllDocsQuery',
        'org.apache.lucene.search.Query', 'org.apache.lucene.search.ScoreDoc', 'org.apache.lucene.search.Sort',
        'org.apache.lucene.search.SortField', 'org.apache.lucene.store.Directory',
        'org.apache.lucene.store.FSDirectory',
    ]
    src = '''/*
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
package org.apache.lucene.analysis.icu;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The code examples on the analysis-icu module overview page
 * (lucene/analysis/icu/src/java/overview.html) must compile against the current Lucene jars.
 *
 * <p>Each example is compiled verbatim with the JDK compiler API against this JVM's class path
 * (the analysis-icu, analysis-common, queryparser and core jars plus ICU4J). It becomes the body
 * of a method whose parameters declare the illustrative variables the overview leaves undeclared
 * ({@code reader}, {@code tokenizer}), and the compilation unit imports every class the examples
 * name, so the only errors left are the examples' own. The control examples on the same page use
 * current API and compile in the same harness, which shows the harness itself is not the cause.
 */
public class TestIcuOverviewExamples extends LuceneTestCase {

  static final String DOC = "overview.html";

  static final String IMPORTS =
      String.join(
          "\\n",
          ''' + ',\n          '.join(jlit('import %s;' % i) for i in imports) + ''',
          "import static org.junit.Assert.assertEquals;");

''' + '\n'.join(consts) + '\n' + '\n'.join(tests) + '''
  static void assertExampleCompiles(int docFirstLine, String params, String[] example)
      throws IOException {
    String header =
        IMPORTS
            + "\\npublic class Example_L"
            + docFirstLine
            + " {\\n  void example("
            + params
            + ") throws Exception {\\n";
    int firstSnippetLine = (int) header.chars().filter(c -> c == '\\n').count() + 1;
    String unit = header + String.join("\\n", example) + "\\n  }\\n}\\n";
    List<String> errors = compile("Example_L" + docFirstLine, unit, firstSnippetLine, docFirstLine, DOC);
    assertNoErrors(DOC + " example at line " + docFirstLine, errors);
  }
''' + HARNESS + '}\n'
    d = os.path.join(unit, 'candidates/icu-overview-examples-call-removed-apis/repro/src/org/apache/lucene/analysis/icu')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'TestIcuOverviewExamples.java'), 'w', encoding='utf-8') as f:
        f.write(src)


def gen_porter(lucene, unit):
    rel = 'lucene/analysis/common/src/java/org/apache/lucene/analysis/en/PorterStemFilter.java'
    bl = blocks(lucene, rel)
    assert len(bl) == 1 and bl[0]['start_line'] == 35, [(b['start_line']) for b in bl]
    b = bl[0]
    src = '''/*
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
package org.apache.lucene.analysis.en;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The class-javadoc example of {@link PorterStemFilter} (PorterStemFilter.java lines 35-41) must
 * compile against the current Lucene jars.
 *
 * <p>The example (a class {@code MyAnalyzer}) is compiled verbatim with the JDK compiler API. It
 * is nested in an outer class that declares the two names the example leaves undeclared
 * ({@code version} and {@code reader}), and the unit imports the analysis classes it names plus
 * the whole {@code org.apache.lucene.analysis.core} package (where LowerCaseTokenizer lived in
 * Lucene 4.0-8.x), so that the only error left is the example's own. A control compiles the same example with the tokenizer
 * replaced by current API, and checks that the replacement analyzer lower-cases and stems.
 */
public class TestPorterStemFilterJavadocExample extends BaseTokenStreamTestCase {

  static final String DOC = "PorterStemFilter.java";

  /** PorterStemFilter.java lines 35-41, verbatim (javadoc rendering of the example). */
  static final String[] EXAMPLE = ''' + jarray(b['lines']) + ''';

  static final String HEADER =
      String.join(
          "\\n",
          "import java.io.Reader;",
          "import org.apache.lucene.analysis.Analyzer;",
          "import org.apache.lucene.analysis.LowerCaseFilter;",
          "import org.apache.lucene.analysis.TokenStream;",
          "import org.apache.lucene.analysis.Tokenizer;",
          "import org.apache.lucene.analysis.core.*;",
          "import org.apache.lucene.analysis.en.PorterStemFilter;",
          "import org.apache.lucene.util.Version;",
          "public class Outer {",
          "  Version version = Version.LATEST;",
          "  Reader reader = null;",
          "");

  /** The javadoc example must compile on main. */
  public void testJavadocExampleCompiles() throws Exception {
    assertNoErrors(DOC + " class-javadoc example", compileExample(EXAMPLE));
  }

  /**
   * Control: the same example with only the tokenizer line replaced by current API compiles in the
   * same harness, so the harness is not the cause of the failure above.
   */
  public void testExampleWithCurrentTokenizerCompilesControl() throws Exception {
    String[] fixed = EXAMPLE.clone();
    int idx = -1;
    for (int i = 0; i < fixed.length; i++) {
      if (fixed[i].contains("new LowerCaseTokenizer(")) {
        idx = i;
      }
    }
    assertTrue("example no longer contains the tokenizer line", idx >= 0);
    fixed[idx] = "    Tokenizer source = new LetterTokenizer();";
    fixed[idx + 1] =
        "    return new TokenStreamComponents(source, new PorterStemFilter(new LowerCaseFilter(source)));";
    assertNoErrors("control (current tokenizer API)", compileExample(fixed));
  }

  /** Control: the replacement chain lower-cases before stemming, as the javadoc requires. */
  public void testReplacementChainStemsLowerCasedTokensControl() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new LetterTokenizer();
            return new TokenStreamComponents(
                source, new PorterStemFilter(new LowerCaseFilter(source)));
          }
        };
    assertAnalyzesTo(a, "Running Dogs", new String[] {"run", "dog"});
    a.close();
  }

  static List<String> compileExample(String[] example) throws IOException {
    int firstSnippetLine = (int) HEADER.chars().filter(c -> c == '\\n').count() + 1;
    String unit = HEADER + String.join("\\n", example) + "\\n}\\n";
    return compile("Outer", unit, firstSnippetLine, 35, DOC);
  }
''' + HARNESS + '}\n'
    d = os.path.join(unit, 'candidates/porterstemfilter-javadoc-example-uses-removed-lowercasetokenizer/repro/src/org/apache/lucene/analysis/en')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'TestPorterStemFilterJavadocExample.java'), 'w', encoding='utf-8') as f:
        f.write(src)


if __name__ == '__main__':
    lucene, unit = sys.argv[1], sys.argv[2]
    gen_icu(lucene, unit)
    gen_porter(lucene, unit)
    print('ok')

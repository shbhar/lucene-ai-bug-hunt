#!/usr/bin/env python3
"""Generate compile-harness JUnit repros for the sweep-found documentation rows of javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904 (verbatim example text).

Usage: gen_generic.py <lucene-checkout> <unit-dir>
Each test case compiles one or more compilation units built from verbatim documentation blocks (extract_blocks.py)
plus harness context that declares the illustrative names the documentation leaves undeclared. A case fails when
javac reports an API-level error (an error located in, or about, a Lucene type: missing member, no suitable
constructor/method, inheriting from a final Lucene class, missing implementation of a Lucene abstract method).
Other errors (illustrative names) are listed in the message but do not fail the case.
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LICENSE = '''/*
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
'''


def jlit(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


def jarr(lines, ind='        '):
    return 'new String[] {\n' + ',\n'.join(ind + jlit(l) for l in lines) + '\n      }'


def blocks(lucene, rel):
    out = subprocess.check_output([sys.executable, '-I', os.path.join(HERE, 'extract_blocks.py'), rel], cwd=lucene)
    return {b['start_line']: b['lines'] for b in json.loads(out)}


HARNESS = r'''
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
'''

IMPORTS_HDR = '''import java.io.IOException;
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
'''


def unit(path, header, line, lines, footer):
    return ('new Unit(%s,\n      %s,\n      %d,\n      %s,\n      %s)'
            % (jlit(path), jlit(header), line, jarr(lines), jlit(footer)))


def write(unitdir, slug, pkg, cls, doc, javadoc, extra_imports, cases, extra_members=''):
    tests = []
    for name, what, units, comment in cases:
        tests.append('  /** %s */\n  public void %s() throws Exception {\n    assertCompiles(\n        %s,\n        %s);\n  }\n'
                     % (comment, name, jlit(what), ',\n        '.join(units)))
    src = (LICENSE + 'package %s;\n\n' % pkg + IMPORTS_HDR + extra_imports + '\n' + javadoc
           + 'public class %s extends LuceneTestCase {\n\n  static final String DOC = %s;\n\n' % (cls, jlit(doc))
           + '\n'.join(tests) + extra_members + HARNESS + '}\n')
    d = os.path.join(unitdir, 'candidates', slug, 'repro', 'src', *pkg.split('.'))
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, cls + '.java'), 'w', encoding='utf-8') as f:
        f.write(src)


def sub_xy(lines):
    # the documentation says "Substitute desired Lucene version for XY": do that, with the current version
    return [l.replace('Version.LUCENE_XY', 'Version.LUCENE_11_0_0') for l in lines]


def gen_analysis_pkg(lucene, unitdir):
    rel = 'lucene/core/src/java/org/apache/lucene/analysis/package-info.java'
    b = blocks(lucene, rel)
    for k in (266, 607, 670, 692, 738, 784, 809, 842, 874):
        assert k in b, (k, sorted(b))
    imp = ('import java.io.IOException;\nimport java.io.StringReader;\nimport org.apache.lucene.analysis.*;\n'
           'import org.apache.lucene.analysis.core.WhitespaceTokenizer;\n'
           'import org.apache.lucene.analysis.miscellaneous.LengthFilter;\n'
           'import org.apache.lucene.analysis.standard.StandardAnalyzer;\n'
           'import org.apache.lucene.analysis.tokenattributes.*;\nimport org.apache.lucene.util.*;\n')
    pos_imp = imp.replace('import org.apache.lucene.analysis.miscellaneous.LengthFilter;\n', '') + \
        'import ex.PartOfSpeechAttribute.PartOfSpeech;\n'
    cases = [
        ('testPositionIncrementGapExampleL266', 'package-info.java example at line 266',
         [unit('ex/Ex266.java', 'package ex;\n' + imp + 'class Ex266 {\n  void example() throws Exception {\n', 266,
               sub_xy(b[266]), '  }\n}\n')],
         'Example at line 266 (getPositionIncrementGap), with LUCENE_XY substituted as its comment says.'),
        ('testWhitespaceTokenizationExampleL607', 'package-info.java example at line 607',
         [unit('ex/MyAnalyzer.java', 'package ex;\n' + imp, 607, sub_xy(b[607]), '')],
         'Example at line 607 (MyAnalyzer, whitespace tokenization), LUCENE_XY substituted.'),
        ('testLengthFilterUsageExampleL670', 'package-info.java example at line 670',
         [unit('ex/Ex670.java', 'package ex;\n' + imp
               + 'abstract class Ex670 extends Analyzer {\n  Version matchVersion = Version.LATEST;\n', 670, b[670], '}\n')],
         'Example at line 670 (adding a LengthFilter), inside an Analyzer with the matchVersion field of the line-607 example.'),
        ('testLengthFilterImplementationExampleL692', 'package-info.java example at line 692',
         [unit('ex/LengthFilter.java', 'package ex;\n' + imp.replace('import org.apache.lucene.analysis.miscellaneous.LengthFilter;\n', ''),
               692, b[692], '')],
         'Example at line 692 (how LengthFilter is implemented).'),
        ('testCustomAttributeExampleL784L809L842', 'package-info.java examples at lines 784, 809 and 842',
         [unit('ex/PartOfSpeechAttribute.java', 'package ex;\n' + imp, 784, b[784], ''),
          unit('ex/PartOfSpeechAttributeImpl.java', 'package ex;\n' + pos_imp, 809, b[809], ''),
          unit('ex/Ex842.java', 'package ex;\n' + pos_imp + 'public class Ex842 {\n', 842, b[842], '}\n')],
         'Examples at lines 784, 809 and 842 (the PartOfSpeech attribute, its implementation and the tagging filter), compiled together.'),
        ('testPartOfSpeechChainExampleL874', 'package-info.java example at line 874',
         [unit('ex/PartOfSpeechAttribute.java', 'package ex;\n' + imp, 784, b[784], ''),
          unit('ex/Ex842.java', 'package ex;\n' + pos_imp + 'public class Ex842 {\n', 842, b[842], '}\n'),
          unit('ex/Ex874.java', 'package ex;\n' + imp + 'import ex.Ex842.PartOfSpeechTaggingFilter;\n'
               + 'abstract class Ex874 extends Analyzer {\n  Version matchVersion = Version.LATEST;\n', 874, b[874], '}\n')],
         'Example at line 874 (LengthFilter plus the tagging filter), with the line-784/842 classes it uses.'),
        ('testFilteringTokenFilterExampleL738Control', 'package-info.java example at line 738 (control)',
         [unit('ex/FilteringTokenFilter.java', 'package ex;\n' + imp.replace('import org.apache.lucene.analysis.*;\n',
               'import org.apache.lucene.analysis.TokenFilter;\nimport org.apache.lucene.analysis.TokenStream;\n'), 738, b[738], '')],
         'Control: the example at line 738 (FilteringTokenFilter) uses current API and compiles in the same harness.'),
    ]
    javadoc = ('/**\n * The code examples of the analysis package overview (lucene/core/src/java/org/apache/lucene/analysis/\n'
               ' * package-info.java) must compile against the current Lucene jars. Each example is compiled verbatim with\n'
               ' * the JDK compiler API; the harness supplies imports and the illustrative context the page leaves out, and\n'
               ' * only API-level errors (a missing or changed Lucene member or constructor) fail a test.\n */\n')
    write(unitdir, 'analysis-package-info-examples-use-pre-5-0-analysis-api', 'org.apache.lucene.analysis',
          'TestAnalysisPackageDocExamples', 'package-info.java', javadoc,
          'import org.apache.lucene.tests.util.LuceneTestCase;\n', cases)


def gen_terminset(lucene, unitdir):
    rel = 'lucene/core/src/java/org/apache/lucene/search/TermInSetQuery.java'
    b = blocks(lucene, rel)
    assert 51 in b, sorted(b)
    imp = ('import java.util.List;\nimport org.apache.lucene.index.Term;\nimport org.apache.lucene.search.*;\n'
           'import org.apache.lucene.search.BooleanClause.Occur;\nimport org.apache.lucene.util.BytesRef;\n')
    fixed = [
        'Query q1 = new TermInSetQuery("field", List.of(new BytesRef("foo"), new BytesRef("bar")));',
        '',
        'BooleanQuery.Builder bq = new BooleanQuery.Builder();',
        'bq.add(new TermQuery(new Term("field", "foo")), Occur.SHOULD);',
        'bq.add(new TermQuery(new Term("field", "bar")), Occur.SHOULD);',
        'Query q2 = new ConstantScoreQuery(bq.build());',
    ]
    cases = [
        ('testClassJavadocExample', 'TermInSetQuery class-javadoc example',
         [unit('ex/Ex51.java', 'package ex;\n' + imp + 'class Ex51 {\n  void example() {\n', 51, b[51], '  }\n}\n')],
         'The class-javadoc example (TermInSetQuery.java lines 51-57).'),
        ('testCorrectedExampleControl', 'corrected example (control)',
         [unit('ex/Fixed.java', 'package ex;\n' + imp + 'class Fixed {\n  void example() {\n', 51, fixed, '  }\n}\n')],
         'Control: the same example written against the current API compiles in the same harness.'),
    ]
    extra = r'''
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
'''
    javadoc = ('/**\n * The class-javadoc example of {@link TermInSetQuery} must compile against the current Lucene jars. It is\n'
               ' * compiled verbatim with the JDK compiler API; only API-level errors fail a test.\n */\n')
    write(unitdir, 'terminsetquery-javadoc-example-uses-removed-ctors', 'org.apache.lucene.search',
          'TestTermInSetQueryJavadocExample', 'TermInSetQuery.java', javadoc,
          'import java.util.List;\nimport org.apache.lucene.index.Term;\nimport org.apache.lucene.tests.util.LuceneTestCase;\n'
          'import org.apache.lucene.util.BytesRef;\n', cases, extra)


def gen_fstcompiler(lucene, unitdir):
    rel = 'lucene/core/src/java/org/apache/lucene/util/fst/FSTCompiler.java'
    b = blocks(lucene, rel)
    ks = sorted(k for k in b if any('OffHeapFSTStore' in l or 'fromFSTReader' in l for l in b[k]))
    assert len(ks) == 2, ks
    on_heap, off_heap = ks
    imp = 'import java.io.IOException;\nimport org.apache.lucene.store.DataInput;\nimport org.apache.lucene.util.fst.*;\n'
    ctx = ('class Ex%d {\n  void example(FSTCompiler<Long> fstCompiler, DataInput dataInput) throws IOException {\n'
           '    FST.FSTMetadata<Long> fstMetadata;\n    FST<Long> fst;\n')
    cases = [
        ('testOffHeapExample', 'FSTCompiler.compile() javadoc example at line %d' % off_heap,
         [unit('ex/Ex%d.java' % off_heap, 'package ex;\n' + imp + ctx % off_heap, off_heap, b[off_heap], '  }\n}\n')],
         'The second compile() javadoc example (a non-FSTReader DataOutput).'),
        ('testCorrectedOffHeapExampleControl', 'corrected line %d example (control)' % off_heap,
         [unit('ex/Fixed%d.java' % off_heap, 'package ex;\n' + imp + 'import org.apache.lucene.store.IndexInput;\n'
               + (ctx % off_heap).replace('class Ex', 'class Fixed').replace('DataInput dataInput', 'IndexInput indexInput, long offset'),
               off_heap, ['    fstMetadata = fstCompiler.compile();',
                          '    fst = FST.fromFSTReader(fstMetadata, new OffHeapFSTStore(indexInput, offset, fstMetadata));'],
               '  }\n}\n')],
         'Control: an off-heap form written against the current API compiles in the same harness.'),
        ('testOnHeapExampleControl', 'FSTCompiler.compile() javadoc example at line %d (control)' % on_heap,
         [unit('ex/Ex%d.java' % on_heap, 'package ex;\n' + imp + ctx % on_heap, on_heap, b[on_heap], '  }\n}\n')],
         'Control: the first compile() javadoc example (FSTReader DataOutput) uses current API and compiles.'),
    ]
    javadoc = ('/**\n * The {@link FSTCompiler#compile()} javadoc examples must compile against the current Lucene jars. Each\n'
               ' * is compiled verbatim with the JDK compiler API, in a method that declares the names it leaves\n'
               ' * undeclared (fstCompiler, dataInput, fstMetadata, fst); only API-level errors fail a test.\n */\n')
    write(unitdir, 'fstcompiler-compile-javadoc-offheap-example-stale', 'org.apache.lucene.util.fst',
          'TestFSTCompilerJavadocExamples', 'FSTCompiler.java', javadoc,
          'import org.apache.lucene.tests.util.LuceneTestCase;\n', cases)
    return on_heap, off_heap


def gen_bp(lucene, unitdir):
    rel = 'lucene/misc/src/java/org/apache/lucene/misc/index/BPIndexReorderer.java'
    b = blocks(lucene, rel)
    assert 76 in b, sorted(b)
    imp = ('import java.nio.file.Path;\nimport java.util.Collections;\nimport java.util.concurrent.ForkJoinPool;\n'
           'import java.util.concurrent.ForkJoinWorkerThread;\nimport org.apache.lucene.index.*;\n'
           'import org.apache.lucene.index.IndexWriterConfig.OpenMode;\nimport org.apache.lucene.misc.index.BPIndexReorderer;\n'
           'import org.apache.lucene.store.Directory;\nimport org.apache.lucene.store.FSDirectory;\n')
    cases = [
        ('testClassJavadocExample', 'BPIndexReorderer class-javadoc example',
         [unit('ex/Ex76.java', 'package ex;\n' + imp + 'class Ex76 {\n  void example(Path targetPath) throws Exception {\n',
               76, b[76], '  }\n}\n')],
         'The class-javadoc example (BPIndexReorderer.java lines 76-90), in a method that declares targetPath.'),
    ]
    javadoc = ('/**\n * The class-javadoc example of {@link BPIndexReorderer} must compile against the current Lucene jars. It\n'
               ' * is compiled verbatim with the JDK compiler API; only API-level errors fail the test (the example\n'
               ' * declares targetDir twice, which is reported but is not an API error).\n */\n')
    write(unitdir, 'bpindexreorderer-javadoc-example-uses-removed-forkjoinpool-api', 'org.apache.lucene.misc.index',
          'TestBPIndexReordererJavadocExample', 'BPIndexReorderer.java', javadoc,
          'import org.apache.lucene.tests.util.LuceneTestCase;\n', cases)


if __name__ == '__main__':
    lucene, unitdir = sys.argv[1], sys.argv[2]
    gen_analysis_pkg(lucene, unitdir)
    gen_terminset(lucene, unitdir)
    print('fst blocks', gen_fstcompiler(lucene, unitdir))
    gen_bp(lucene, unitdir)
    print('ok')

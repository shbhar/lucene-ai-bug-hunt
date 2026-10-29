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
package org.apache.lucene.analysis.icu;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIcuOverviewExamplesCompile extends LuceneTestCase {

  private static final String HEADER =
      """
      import com.ibm.icu.text.*;
      import com.ibm.icu.util.ULocale;
      import java.nio.file.*;
      import org.apache.lucene.analysis.*;
      import org.apache.lucene.analysis.icu.*;
      import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
      import org.apache.lucene.document.*;
      import org.apache.lucene.index.*;
      import org.apache.lucene.queryparser.classic.QueryParser;
      import org.apache.lucene.search.*;
      import org.apache.lucene.store.*;
      import static org.junit.Assert.assertEquals;
      class Example {
        java.io.Reader reader;
        Tokenizer tokenizer;
        void example() throws Exception {
      """;

  private static final int[] OVERVIEW_HTML_FIRST_LINE = {74, 114, 141, 169, 338};
  private static final String[] OVERVIEW_HTML_BLOCK = {
    """
      // This tokenizer will work well in general for most languages.
      Tokenizer tokenizer = new ICUTokenizer(reader);
    """,
    """
      Collator collator = Collator.getInstance(new ULocale("ar"));
      ICUCollationKeyAnalyzer analyzer = new ICUCollationKeyAnalyzer(collator);
      Path indexPath = Files.createTempDirectory("tempIndex");
      Directory dir = FSDirectory.open(indexPath);
      IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
      Document doc = new Document();
      doc.add(new Field("content", "\\u0633\\u0627\\u0628",
                        Field.Store.YES, Field.Index.ANALYZED));
      writer.addDocument(doc);
      writer.close();
      IndexSearcher is = new IndexSearcher(dir, true);

      QueryParser aqp = new QueryParser("content", analyzer);
      aqp.setAnalyzeRangeTerms(true);

      // Unicode order would include U+0633 in [ U+062F - U+0698 ], but Farsi
      // orders the U+0698 character before the U+0633 character, so the single
      // indexed Term above should NOT be returned by a ConstantScoreRangeQuery
      // with a Farsi Collator (or an Arabic one for the case when Farsi is not
      // supported).
      ScoreDoc[] result
        = is.search(aqp.parse("[ \\u062F TO \\u0698 ]"), null, 1000).scoreDocs;
      assertEquals("The index Term should not be included.", 0, result.length);
    """,
    """
      Analyzer analyzer
        = new ICUCollationKeyAnalyzer(Collator.getInstance(new ULocale("da", "dk")));
      Path indexPath = Files.createTempDirectory("tempIndex");
      Directory dir = FSDirectory.open(indexPath);
      IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
      String[] tracer = new String[] { "A", "B", "C", "D", "E" };
      String[] data = new String[] { "HAT", "HUT", "H\\u00C5T", "H\\u00D8T", "HOT" };
      String[] sortedTracerOrder = new String[] { "A", "E", "B", "D", "C" };
      for (int i = 0 ; i < data.length ; ++i) {
        Document doc = new Document();
        doc.add(new Field("tracer", tracer[i], Field.Store.YES, Field.Index.NO));
        doc.add(new Field("contents", data[i], Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(doc);
      }
      writer.close();
      IndexSearcher searcher = new IndexSearcher(dir, true);
      Sort sort = new Sort();
      sort.setSort(new SortField("contents", SortField.STRING));
      Query query = new MatchAllDocsQuery();
      ScoreDoc[] result = searcher.search(query, null, 1000, sort).scoreDocs;
      for (int i = 0 ; i < result.length ; ++i) {
        Document doc = searcher.doc(result[i].doc);
        assertEquals(sortedTracerOrder[i], doc.getValues("tracer")[0]);
      }
    """,
    """
      Collator collator = Collator.getInstance(new ULocale("tr", "TR"));
      collator.setStrength(Collator.PRIMARY);
      Analyzer analyzer = new ICUCollationKeyAnalyzer(collator);
      Path indexPath = Files.createTempDirectory("tempIndex");
      Directory dir = FSDirectory.open(indexPath);
      IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
      Document doc = new Document();
      doc.add(new Field("contents", "DIGY", Field.Store.NO, Field.Index.ANALYZED));
      writer.addDocument(doc);
      writer.close();
      IndexSearcher is = new IndexSearcher(dir, true);
      QueryParser parser = new QueryParser("contents", analyzer);
      Query query = parser.parse("d\\u0131gy");   // U+0131: dotless i
      ScoreDoc[] result = is.search(query, null, 1000).scoreDocs;
      assertEquals("The index Term should be included.", 1, result.length);
    """,
    """
      //
      This filter will map Serbian Cyrillic to Serbian Latin according to BGN rules
      TokenStream tokenstream = new ICUTransformFilter(tokenizer, Transliterator.getInstance("Serbian-Latin/BGN"));
    """
  };

  public void testOverviewExamplesCompile() throws Exception {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    String classpath = System.getProperty("java.class.path");
    List<String> opts = List.of("-cp", classpath, "-d", createTempDir().toString(), "-proc:none");
    int headerLines = (int) HEADER.lines().count();
    Set<String> errors = new LinkedHashSet<>();
    for (int b = 0; b < OVERVIEW_HTML_BLOCK.length; b++) {
      String unit = HEADER + OVERVIEW_HTML_BLOCK[b] + "  }\n}\n";
      JavaFileObject src =
          new SimpleJavaFileObject(URI.create("string:///Example.java"), Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
              return unit;
            }
          };
      DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
      javac.getTask(null, null, diags, opts, null, List.of(src)).call();
      for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
        if (d.getKind() == Diagnostic.Kind.ERROR) {
          long line = d.getLineNumber() - headerLines - 1;
          String where =
              line < 0 ? "test header" : "overview.html:" + (OVERVIEW_HTML_FIRST_LINE[b] + line);
          errors.add(where + ": " + d.getMessage(Locale.ROOT).lines().limit(2).toList());
        }
      }
    }
    // expected: the overview.html "Example Usages" blocks copied above compile; actual: errors
    assertTrue(
        "overview.html examples fail to compile:\n" + String.join("\n", errors),
        errors.isEmpty());
  }
}

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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/** Reach probe: Intervals.analyzedText with a multi-word synonym; prints hit counts; always passes. */
public class TestAnalyzedTextReach extends LuceneTestCase {

  private static final String FIELD = "f";

  private static int count(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
      }
    }
  }

  private static Analyzer synonymAnalyzer() throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    CharsRefBuilder multi = new CharsRefBuilder();
    SynonymMap.Builder.join(new String[] {"new", "york"}, multi);
    b.add(new CharsRef("york"), multi.get(), true); // york -> "new york", keep original
    SynonymMap map = b.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        TokenStream ts = new SynonymGraphFilter(t, map, true);
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private static void p(String label, IntervalsSource s, String doc) throws IOException {
    System.out.println("PROBE\t" + label + "\tdoc='" + doc + "'\thits=" + count(s, doc) + "\tsrc=" + s);
  }

  public void testReach() throws IOException {
    Analyzer a = synonymAnalyzer();
    String doc = "new york x new york";
    // york@1 then the phrase 'new york' at [3,4]: one gap (position 2)
    p("A1 analyzedText('york york', maxGaps=1, ordered)", Intervals.analyzedText("york york", a, FIELD, 1, true), doc);
    p("A2 analyzedText('york york', maxGaps=1, unordered)", Intervals.analyzedText("york york", a, FIELD, 1, false), doc);
    p("A3 analyzedText('york york', maxGaps=0, ordered) [phrase path]", Intervals.analyzedText("york york", a, FIELD, 0, true), "new york new york");
    p("A4 analyzedText('york york', maxGaps=0, unordered)", Intervals.analyzedText("york york", a, FIELD, 0, false), "new york new york");
    IntervalsSource y = Intervals.or(Intervals.term("york"), Intervals.phrase("new", "york"));
    p("A5 manual maxgaps(1, ordered(york, or(york,'new york')))", Intervals.maxgaps(1, Intervals.ordered(Intervals.term("york"), y)), doc);
    a.close();
  }
}

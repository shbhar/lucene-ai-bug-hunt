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
package org.apache.lucene.search.matchhighlight;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * PassageFormatter resolves overlapping markers by closing the markers opened after the one that
 * ends and re-opening them. When markers cross, it closes them in opening order instead of the
 * reverse, so markers whose end strings differ come out mis-nested.
 */
public class TestPassageFormatterCrossingMarkers extends LuceneTestCase {

  private static final Pattern TAG = Pattern.compile("<(/?)([a-z]+)>");

  /** Asserts that the tags in {@code s} are well nested. */
  private static void assertWellNested(String s) {
    ArrayDeque<String> open = new ArrayDeque<>();
    Matcher m = TAG.matcher(s);
    while (m.find()) {
      if (m.group(1).isEmpty()) {
        open.push(m.group(2));
      } else {
        String top = open.poll();
        assertEquals("mis-nested end tag </" + m.group(2) + "> in: " + s, m.group(2), top);
      }
    }
    assertTrue("unclosed tags " + open + " in: " + s, open.isEmpty());
  }

  /** Two crossing markers, each with its own start and end string. */
  public void testCrossingMarkersAreClosedInReverseOrder() {
    String value = "foo bar baz";
    OffsetRange first = new OffsetRange(0, 7); // "foo bar"
    OffsetRange second = new OffsetRange(4, 11); // "bar baz"
    Map<OffsetRange, String> tag = new java.util.IdentityHashMap<>();
    tag.put(first, "b");
    tag.put(second, "i");
    PassageFormatter formatter =
        new PassageFormatter("...", r -> "<" + tag.get(r) + ">", r -> "</" + tag.get(r) + ">");

    String out =
        formatter
            .format(new StringBuilder(), value, new Passage(0, 11, List.of(first, second)))
            .toString();

    // Stripping the markers gives the passage text back in both cases; only the nesting differs.
    assertEquals(value, out.replaceAll("</?[a-z]+>", ""));
    assertEquals("<b>foo <i>bar</i></b><i> baz</i>", out);
    assertWellNested(out);
  }

  /** The same through MatchHighlighter, marking each query's matches with its own tag. */
  public void testMatchHighlighterPerQueryTags() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", "foo bar baz", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q1 = new PhraseQuery("body", "foo", "bar");
        Query q2 = new PhraseQuery("body", "bar", "baz");
        Query both =
            new BooleanQuery.Builder()
                .add(q1, BooleanClause.Occur.SHOULD)
                .add(q2, BooleanClause.Occur.SHOULD)
                .build();
        TopDocs topDocs = searcher.search(both, 10);
        assertEquals(1, topDocs.scoreDocs.length);

        Map<Query, String> tags = Map.of(q1, "b", q2, "i");
        Function<OffsetRange, String> tag =
            r -> tags.get(((MatchHighlighter.QueryOffsetRange) r).query);
        MatchHighlighter highlighter =
            new MatchHighlighter(searcher, analyzer)
                .appendFieldHighlighter(
                    FieldValueHighlighters.highlighted(
                        80,
                        1,
                        new PassageFormatter(
                            "...", r -> "<" + tag.apply(r) + ">", r -> "</" + tag.apply(r) + ">"),
                        "body"::equals))
                .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());

        List<String> out =
            highlighter.highlight(topDocs, q1, q2).findFirst().orElseThrow().fields.get("body");
        assertEquals(1, out.size());
        assertEquals("foo bar baz", out.get(0).replaceAll("</?[a-z]+>", ""));
        assertWellNested(out.get(0));
      }
    }
  }
}

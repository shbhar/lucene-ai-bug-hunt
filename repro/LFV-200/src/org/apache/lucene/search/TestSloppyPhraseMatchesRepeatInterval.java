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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * For a sloppy phrase with a repeated term, SloppyPhraseMatcher resolves a collision between the
 * repeating PhrasePositions by advancing one of them past the match before nextMatch() returns, so
 * Matches#endPosition (and endOffset) report that later occurrence instead of the end of the match.
 */
public class TestSloppyPhraseMatchesRepeatInterval extends LuceneTestCase {

  private static List<String> intervals(String text, PhraseQuery q) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
      iwc.setCodec(TestUtil.getDefaultCodec());
      FieldType ft = new FieldType(TextField.TYPE_STORED);
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d = new Document();
        d.add(new Field("body", text, ft));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Weight weight = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = weight.matches(r.leaves().get(0), 0);
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        MatchesIterator it = m.getMatches("body");
        while (it.next()) {
          out.add(
              "["
                  + it.startPosition()
                  + ","
                  + it.endPosition()
                  + "] '"
                  + text.substring(it.startOffset(), it.endOffset())
                  + "'");
        }
        return out;
      }
    }
  }

  public void testRepeatedTermIntervalEnd() throws IOException {
    // positions:    0    1    2   3
    String text = "very very good very";
    // "very very" with slop 1: the first match is the exact occurrence at positions 0-1
    List<String> got = intervals(text, new PhraseQuery(1, "body", "very", "very"));
    assertEquals("first match of \"very very\"~1", "[0,1] 'very very'", got.isEmpty() ? "none" : got.get(0));
  }

  public void testToBeOrNotToBe() throws IOException {
    // positions:    0  1  2  3   4  5  6    7  8  9
    String text = "to be or not to be that is to be";
    // exact occurrence of the query at positions 0-5; with slop 1 the first match must still end at 5
    List<String> got =
        intervals(text, new PhraseQuery(1, "body", "to", "be", "or", "not", "to", "be"));
    assertEquals("first match", "[0,5] 'to be or not to be'", got.isEmpty() ? "none" : got.get(0));
  }
}

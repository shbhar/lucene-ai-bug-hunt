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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SloppyPhraseMatcher detects repetition groups once per segment, on the first candidate document,
 * by grouping PhrasePositions whose first positions coincide. When that document has two different
 * repeated query terms stacked at one position (synonyms), the two terms end up in one group, and
 * every later document of the segment is matched with the wrong groups.
 */
public class TestSloppyPhraseRepeatGroupsFirstDoc extends LuceneTestCase {

  private static Token tok(String term, int posInc) {
    Token t = new Token(term, 0, 1);
    t.setPositionIncrement(posInc);
    return t;
  }

  private static Document doc(Token... tokens) {
    Document d = new Document();
    d.add(new TextField("f", new CannedTokenStream(tokens)));
    return d;
  }

  private static int count(Directory dir, Query q) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  private static String hits(Directory dir, Query q) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      StringBuilder sb = new StringBuilder();
      for (ScoreDoc sd : new IndexSearcher(r).search(q, 10).scoreDocs) sb.append(sd.doc).append(' ');
      return sb.toString().trim();
    }
  }

  private static String sortedHits(Directory dir, Query q) throws IOException {
    String[] h = hits(dir, q).split(" ");
    java.util.Arrays.sort(h);
    return String.join(" ", h).trim();
  }

  private static Directory index(Document... docs) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (Document d : docs) w.addDocument(d);
      w.forceMerge(1);
    }
    return dir;
  }

  public void testStackedTermsInFirstDocumentHideLaterExactPhrase() throws IOException {
    // doc 0: a and b stacked at position 0 (e.g. synonyms), then a, b: contains every query term
    Document first = doc(tok("a", 1), tok("b", 0), tok("a", 1), tok("b", 1));
    // doc 1: exactly "a b a b"
    Document exact = doc(tok("a", 1), tok("b", 1), tok("a", 1), tok("b", 1));
    PhraseQuery exactQuery = new PhraseQuery(0, "f", "a", "b", "a", "b");
    PhraseQuery sloppyQuery = new PhraseQuery(1, "f", "a", "b", "a", "b");

    try (Directory alone = index(exact)) {
      assertEquals("control: exact phrase on doc 1 alone", 1, count(alone, exactQuery));
      assertEquals("control: slop 1 on doc 1 alone", 1, count(alone, sloppyQuery));
    }
    try (Directory both = index(first, exact)) {
      assertEquals("exact phrase matches doc 1", "1", hits(both, exactQuery));
      // Slop 1 allows at least what slop 0 allows (PhraseQuery#getSlop: the slop is the maximum edit
      // distance). Doc 0 matches too: a->0, b->0, a->1, b->2 is 1 move away from "a b a b".
      assertEquals("slop 1 must match doc 1 (exact phrase) and doc 0", "0 1", sortedHits(both, sloppyQuery));
    }
  }
}

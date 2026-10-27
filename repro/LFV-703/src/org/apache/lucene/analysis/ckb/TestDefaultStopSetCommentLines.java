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
package org.apache.lucene.analysis.ckb;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The bundled stopword files of SoraniAnalyzer, LatvianAnalyzer and GalicianAnalyzer use '#'
 * comment lines, like the files of the other language analyzers. The other analyzers load their
 * file with WordlistLoader.getWordSet(Reader, "#"), which skips comment lines; these three call
 * WordlistLoader.getWordSet(Reader), which "adds every line as an entry", so the comment lines end
 * up in getDefaultStopSet().
 */
public class TestDefaultStopSetCommentLines extends LuceneTestCase {

  private static List<String> commentEntries(CharArraySet set) {
    List<String> out = new ArrayList<>();
    for (Object o : set) {
      String s = new String((char[]) o);
      if (s.startsWith("#")) {
        out.add(s);
      }
    }
    return out;
  }

  /** Control: a sibling analyzer whose file has the same '#' header loads no comment line. */
  public void testControlBulgarian() {
    assertEquals(List.of(), commentEntries(BulgarianAnalyzer.getDefaultStopSet()));
  }

  public void testSorani() {
    CharArraySet set = SoraniAnalyzer.getDefaultStopSet();
    List<String> bad = commentEntries(set);
    assertEquals(
        "comment lines in SoraniAnalyzer.getDefaultStopSet() (" + bad.size() + " of " + set.size()
            + "): " + bad,
        0,
        bad.size());
  }

  public void testLatvian() {
    CharArraySet set = LatvianAnalyzer.getDefaultStopSet();
    List<String> bad = commentEntries(set);
    assertEquals(
        "comment lines in LatvianAnalyzer.getDefaultStopSet() (" + bad.size() + " of " + set.size()
            + "): " + bad,
        0,
        bad.size());
  }

  public void testGalician() {
    CharArraySet set = GalicianAnalyzer.getDefaultStopSet();
    List<String> bad = commentEntries(set);
    assertEquals(
        "comment lines in GalicianAnalyzer.getDefaultStopSet() (" + bad.size() + " of "
            + set.size() + "): " + bad,
        0,
        bad.size());
  }
}

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

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDefaultStopSetCommentLines extends LuceneTestCase {

  public void testNoCommentLinesInDefaultStopSets() {
    // expected: no entry starts with '#' (the '#' lines of the bundled stopwords.txt are comments)
    assertEquals(
        "Sorani 0, Latvian 0, Galician 0",
        "Sorani " + comments(SoraniAnalyzer.getDefaultStopSet())
            + ", Latvian " + comments(LatvianAnalyzer.getDefaultStopSet())
            + ", Galician " + comments(GalicianAnalyzer.getDefaultStopSet()));
  }

  private static int comments(CharArraySet set) {
    int n = 0;
    for (Object entry : set) {
      if (new String((char[]) entry).startsWith("#")) {
        n++;
      }
    }
    return n;
  }
}

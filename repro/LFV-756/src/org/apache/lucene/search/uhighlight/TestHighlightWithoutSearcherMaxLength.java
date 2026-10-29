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
package org.apache.lucene.search.uhighlight;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHighlightWithoutSearcherMaxLength extends LuceneTestCase {

  public void testMaxLength() throws Exception {
    try (StandardAnalyzer analyzer = new StandardAnalyzer()) {
      UnifiedHighlighter highlighter =
          UnifiedHighlighter.builderWithoutSearcher(analyzer).withMaxLength(20).build();
      // the only match, "Lucene", starts at offset 33, past maxLength 20
      Object snippet =
          highlighter.highlightWithoutSearcher(
              "body",
              new TermQuery(new Term("body", "lucene")),
              "Alpha beta gamma. Delta epsilon. Lucene is here.",
              1);
      assertFalse(
          "expected no match past maxLength 20, got: " + snippet,
          snippet.toString().contains("<b>Lucene</b>"));
    }
  }
}

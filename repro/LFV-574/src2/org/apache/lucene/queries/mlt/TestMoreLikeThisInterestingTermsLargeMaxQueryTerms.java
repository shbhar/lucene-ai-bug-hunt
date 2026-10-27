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
package org.apache.lucene.queries.mlt;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.util.LuceneTestCase;

/** (4.x/8.x variant: old test-framework packages, like(Reader, String).)
 * like(...) treats maxQueryTerms as an upper bound (it sizes its queue to min(maxQueryTerms, #terms)),
 * so setMaxQueryTerms(Integer.MAX_VALUE) works there. retrieveInterestingTerms(...) instead allocates an
 * ArrayList with maxQueryTerms slots up front and throws OutOfMemoryError for the same setting, even for
 * a one-word text.
 */
public class TestMoreLikeThisInterestingTermsLargeMaxQueryTerms extends LuceneTestCase {

  public void testUnboundedMaxQueryTerms() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    Document doc = new Document();
    doc.add(newTextField("text", "lucene release", Field.Store.YES));
    w.addDocument(doc);
    IndexReader reader = w.getReader();
    w.close();

    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setMaxQueryTerms(Integer.MAX_VALUE);

    // works: one clause
    assertNotNull(mlt.like(new StringReader("lucene"), "text"));
    // must work too, returning the single interesting word
    String[] terms = null;
    try {
      terms = mlt.retrieveInterestingTerms(new StringReader("lucene"), "text");
    } catch (OutOfMemoryError e) {
      fail("retrieveInterestingTerms threw " + e);
    }
    assertArrayEquals(new String[] {"lucene"}, terms);

    reader.close();
    dir.close();
    analyzer.close();
  }
}

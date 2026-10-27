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

import java.util.Arrays;
import java.util.HashSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * setFieldNames(null): "Set this to null for the field names to be determined at runtime from the
 * IndexReader provided in the constructor." like(int) and like(Map) resolve null to the indexed fields;
 * retrieveInterestingTerms(int) does not, and throws NullPointerException (unless a like(...) call on
 * the same instance happened to resolve the field names first).
 */
public class TestMoreLikeThisNullFieldNames extends LuceneTestCase {

  public void testRetrieveInterestingTermsWithNullFieldNames() throws Exception {
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
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setFieldNames(null); // documented: determine the fields from the reader

    String[] terms = mlt.retrieveInterestingTerms(0);
    assertEquals(
        new HashSet<>(Arrays.asList("lucene", "release")), new HashSet<>(Arrays.asList(terms)));

    reader.close();
    dir.close();
    analyzer.close();
  }
}

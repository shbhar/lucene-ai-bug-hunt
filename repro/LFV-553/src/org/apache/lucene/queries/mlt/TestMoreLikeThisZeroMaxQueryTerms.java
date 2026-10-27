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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * setMaxQueryTerms(0) is accepted, but every later like(...) / retrieveInterestingTerms(...) call on a
 * text with at least one selectable term throws NullPointerException: createQueue sizes its priority
 * queue to min(maxQueryTerms, #terms) = 0 and then dereferences queue.top(), which is null for an empty
 * queue. The same happens through MoreLikeThisQuery.setMaxQueryTerms(0) at search time.
 */
public class TestMoreLikeThisZeroMaxQueryTerms extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    Document doc = new Document();
    doc.add(newTextField("text", "lucene release", Field.Store.YES));
    w.addDocument(doc);
    reader = w.getReader();
    w.close();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private MoreLikeThis newMlt() throws Exception {
    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setFieldNames(new String[] {"text"});
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    mlt.setMaxQueryTerms(0);
    return mlt;
  }

  public void testLikeReader() throws Exception {
    newMlt().like("text", new StringReader("lucene"));
  }

  public void testLikeDocNum() throws Exception {
    newMlt().like(0);
  }

  public void testRetrieveInterestingTerms() throws Exception {
    newMlt().retrieveInterestingTerms(new StringReader("lucene"), "text");
  }

  public void testMoreLikeThisQuery() throws Exception {
    MoreLikeThisQuery q =
        new MoreLikeThisQuery("lucene", new String[] {"text"}, analyzer, "text");
    q.setMinDocFreq(1);
    q.setMaxQueryTerms(0);
    IndexSearcher searcher = newSearcher(reader);
    searcher.count(q);
  }
}

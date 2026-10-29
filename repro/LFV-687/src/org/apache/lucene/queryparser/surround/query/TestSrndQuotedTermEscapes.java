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
package org.apache.lucene.queryparser.surround.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSrndQuotedTermEscapes extends LuceneTestCase {

  public void testEscapedQuoteInQuotedTerm() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new StringField("k", "a\"b", Field.Store.NO));
    w.addDocument(doc);
    IndexReader reader = w.getReader();
    w.close();
    Query q = QueryParser.parse("\"a\\\"b\"").makeLuceneQueryField("k", new BasicQueryFactory());
    int hits = newSearcher(reader).count(q);
    reader.close();
    dir.close();
    // "a\"b" must match the term a"b: expected 1 hit, actual 0 (it searches for a\"b)
    assertEquals("hits for the term a\"b", 1, hits);
  }
}

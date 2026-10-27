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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.CorePlusQueriesParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MoreLikeThisQuery(likeText, moreLikeFields, analyzer, fieldName) documents moreLikeFields as the "fields
 * used for similarity measure", but its rewrite only ever produces terms of fieldName: the like text is
 * matched against fieldName alone, so a document that is similar only through another field of
 * moreLikeFields is never found. The XML query parser's LikeThisQuery, whose fieldNames attribute is a
 * "Comma delimited list of field names", inherits this and only searches the first listed field.
 */
public class TestMoreLikeThisQueryMoreLikeFields extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
    // "zebra" and "quokka" occur only in the body field.
    String[][] docs = {
      {"apple banana", "zebra quokka"},
      {"cherry", "zebra"},
      {"banana cherry", "lion"},
    };
    for (String[] d : docs) {
      Document doc = new Document();
      doc.add(newTextField("title", d[0], Field.Store.YES));
      doc.add(newTextField("body", d[1], Field.Store.YES));
      w.addDocument(doc);
    }
    reader = w.getReader();
    w.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static Set<String> fieldsOf(Query q) {
    Set<String> out = new HashSet<>();
    q.visit(
        new QueryVisitor() {
          @Override
          public void consumeTerms(Query query, Term... terms) {
            for (Term t : terms) {
              out.add(t.field());
            }
          }

          @Override
          public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
            return this;
          }
        });
    return out;
  }

  public void testMoreLikeFieldsAreUsed() throws Exception {
    MoreLikeThisQuery q =
        new MoreLikeThisQuery("zebra quokka", new String[] {"title", "body"}, analyzer, "title");
    q.setMinDocFreq(1);
    q.setMinTermFrequency(1);
    Query rewritten = searcher.rewrite(q);
    assertTrue(
        "body is one of the moreLikeFields, so body terms must take part: " + rewritten,
        fieldsOf(rewritten).contains("body"));
    assertEquals(2, searcher.count(q));
  }

  public void testXmlLikeThisQueryUsesAllFieldNames() throws Exception {
    String xml =
        "<LikeThisQuery fieldNames=\"title,body\" minDocFreq=\"1\" percentTermsToMatch=\"1\">"
            + "zebra quokka</LikeThisQuery>";
    Query q =
        new CorePlusQueriesParser("title", analyzer)
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    assertEquals("documents similar through the body field must match", 2, searcher.count(q));
  }
}

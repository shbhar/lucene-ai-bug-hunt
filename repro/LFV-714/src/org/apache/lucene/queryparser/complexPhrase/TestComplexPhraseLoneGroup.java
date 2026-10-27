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
package org.apache.lucene.queryparser.complexPhrase;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Inside a complex phrase a parenthesized group stands for one position with alternatives:
 * "(john OR jonathan) smith" and "(jo* -john) smith" (both from TestComplexPhraseQuery) match a
 * john/jonathan, resp. a jo* term other than john, followed by smith. When the group is the whole
 * phrase, the parser instead reads the group's members as consecutive positions:
 * "(john OR jonathan)" becomes spanNear([john, jonathan], 0, true) and matches nothing, and
 * "(jo* -john)" becomes spanNot(jo*, spanNear([jo*, john])) and matches documents that contain
 * only john.
 */
public class TestComplexPhraseLoneGroup extends LuceneTestCase {
  private static final String F = "name";
  private static final String[] DOCS = {
    "john smith", "jonathan smith", "johnny walker", "peter jonathan"
  };

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    for (String text : DOCS) {
      Document doc = new Document();
      doc.add(newTextField(F, text, Field.Store.YES));
      w.addDocument(doc);
    }
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private Set<String> hits(String s) throws Exception {
    Query q = new ComplexPhraseQueryParser(F, analyzer).parse(s);
    Set<String> result = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
      result.add(searcher.storedFields().document(sd.doc).get(F));
    }
    return result;
  }

  /** Controls: the group as one clause of a longer phrase is one position with alternatives. */
  public void testGroupInsideLongerPhrase() throws Exception {
    assertEquals(Set.of("john smith", "jonathan smith"), hits("\"(john OR jonathan) smith\""));
    assertEquals(Set.of("jonathan smith"), hits("\"(jo* -john) smith\""));
    assertEquals(Set.of("john smith", "johnny walker", "jonathan smith", "peter jonathan"), hits("\"jo*\""));
  }

  /** The same group as the whole phrase: one position whose term is john or jonathan. */
  public void testLoneOrGroup() throws Exception {
    assertEquals(Set.of("john smith", "jonathan smith", "peter jonathan"), hits("\"(john OR jonathan)\""));
  }

  /** The same exclusion group as the whole phrase: a jo* term other than john. */
  public void testLoneNotGroup() throws Exception {
    assertEquals(Set.of("johnny walker", "jonathan smith", "peter jonathan"), hits("\"(jo* -john)\""));
  }
}

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
package org.apache.lucene.search.vectorhighlight;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFastVectorHighlighterSurrogatePair extends LuceneTestCase {

  public void testFragmentDoesNotSplitSurrogatePair() throws Exception {
    Directory dir = newDirectory();
    IndexWriter writer =
        new IndexWriter(
            dir, newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)));
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setStoreTermVectors(true);
    type.setStoreTermVectorPositions(true);
    type.setStoreTermVectorOffsets(true);
    Document doc = new Document();
    doc.add(new Field("f", "\uD83D\uDE00".repeat(40) + " foo", type));
    writer.addDocument(doc);
    writer.close();
    IndexReader reader = DirectoryReader.open(dir);
    FastVectorHighlighter highlighter = new FastVectorHighlighter();
    FieldQuery fieldQuery = highlighter.getFieldQuery(new TermQuery(new Term("f", "foo")), reader);
    String fragment = highlighter.getBestFragment(fieldQuery, reader, 0, "f", 100);
    reader.close();
    dir.close();
    // expected: no half character at the fragment start; actual: it starts with U+DE00
    assertFalse(fragment, Character.isLowSurrogate(fragment.charAt(0)));
  }
}

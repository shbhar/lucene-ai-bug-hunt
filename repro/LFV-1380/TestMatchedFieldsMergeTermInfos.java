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

import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.DefaultEncoder;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMatchedFieldsMergeTermInfos extends LuceneTestCase {

  public void testMergedMatchKeepsFirstMatchTermInfos() throws Exception {
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setStoreTermVectors(true);
    type.setStoreTermVectorPositions(true);
    type.setStoreTermVectorOffsets(true);
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    Document doc = new Document();
    doc.add(new Field("a", "running fast xx xx xx swim slow", type));
    doc.add(new Field("b", "running fast xx xx xx swim slow", type));
    writer.addDocument(doc);
    writer.close();
    IndexReader reader = DirectoryReader.open(dir);
    Query query =
        new BooleanQuery.Builder()
            .add(new PhraseQuery("a", "running", "fast"), Occur.SHOULD)
            .add(new TermQuery(new Term("b", "fast")), Occur.SHOULD)
            .add(new PhraseQuery("a", "swim", "slow"), Occur.SHOULD)
            .build();
    FastVectorHighlighter highlighter = new FastVectorHighlighter();
    String[] best =
        highlighter.getBestFragments(
            highlighter.getFieldQuery(query, reader),
            reader,
            0,
            "a",
            Set.of("a", "b"),
            20,
            1,
            new WeightedFragListBuilder(),
            new ScoreOrderFragmentsBuilder(),
            new String[] {"<b>"},
            new String[] {"</b>"},
            new DefaultEncoder());
    // expected: the passage matched by a:"running fast" and b:fast wins; actual: "swim slow" wins
    assertArrayEquals(new String[] {"<b>running fast</b> xx xx xx"}, best);
    reader.close();
    dir.close();
  }
}

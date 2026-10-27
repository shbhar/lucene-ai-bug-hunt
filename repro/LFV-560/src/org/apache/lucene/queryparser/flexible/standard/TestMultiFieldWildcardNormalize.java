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
package org.apache.lucene.queryparser.flexible.standard;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StandardQueryParser with setMultiFields: WildcardQueryNodeProcessor runs before
 * MultiFieldQueryNodeProcessor in StandardQueryNodeProcessorPipeline, so prefix and wildcard terms
 * are normalized with Analyzer#normalize(null, ...) instead of the analyzer of the field they are
 * expanded to. Fuzzy, regexp, range and plain terms are processed after the expansion and use the
 * expanded field. The classic MultiFieldQueryParser normalizes per field.
 */
public class TestMultiFieldWildcardNormalize extends LuceneTestCase {

  private static Analyzer perField(Map<String, Analyzer> map) {
    return new PerFieldAnalyzerWrapper(new WhitespaceAnalyzer(), map);
  }

  public void testPrefixAndWildcardUseExpandedFieldAnalyzer() throws Exception {
    Map<String, Analyzer> map = new HashMap<>();
    map.put("title", new StandardAnalyzer());
    try (Analyzer a = perField(map);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("title", "Lucene", Field.Store.NO)); // indexed as "lucene"
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        StandardQueryParser qp = new StandardQueryParser(a);
        qp.setMultiFields(new CharSequence[] {"title"});
        MultiFieldQueryParser classic = new MultiFieldQueryParser(new String[] {"title"}, a);
        // controls: explicit field, fuzzy through the expansion, classic multi-field parser
        assertEquals(1, s.count(qp.parse("title:Luc*", null)));
        assertEquals(1, s.count(qp.parse("Lucene~1", null)));
        assertEquals(1, s.count(classic.parse("Luc*")));
        for (String q : new String[] {"Luc*", "Lu?ene"}) {
          Query query = qp.parse(q, null);
          assertEquals(q + " parsed to " + query, 1, s.count(query));
        }
      }
    }
  }

  /** The same root cause with an immutable per-field map: normalize(null, ...) throws. */
  public void testImmutablePerFieldMap() throws Exception {
    try (Analyzer a = perField(Map.of("title", new StandardAnalyzer()))) {
      StandardQueryParser qp = new StandardQueryParser(a);
      qp.setMultiFields(new CharSequence[] {"title"});
      assertEquals("title:lucene~1", qp.parse("Lucene~1", null).toString()); // control
      assertEquals("title:luc*", qp.parse("Luc*", null).toString());
    }
  }
}

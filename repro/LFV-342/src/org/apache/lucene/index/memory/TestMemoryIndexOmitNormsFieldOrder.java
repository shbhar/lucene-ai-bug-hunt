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
package org.apache.lucene.index.memory;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * LUCENE-7679 made MemoryIndex honour omitNorms of passed-in fields, but the FieldInfo of a field
 * is created from the first IndexableField with that name. When that first instance does not
 * index terms (here a doc-values field, whose FieldType has omitNorms=false by default), a later
 * indexed instance with omitNorms=true is still given norms. IndexWriter takes omitNorms from the
 * indexed instance only (it has no norms for this field), so MemoryIndex scores differently.
 */
public class TestMemoryIndexOmitNormsFieldOrder extends LuceneTestCase {

  private static List<IndexableField> doc(boolean docValuesFirst) {
    FieldType textNoNorms = new FieldType(TextField.TYPE_NOT_STORED);
    textNoNorms.setOmitNorms(true);
    textNoNorms.freeze();
    IndexableField dv = new SortedDocValuesField("title", new BytesRef("quick brown fox"));
    IndexableField text = new Field("title", "quick brown fox", textNoNorms);
    return docValuesFirst ? List.of(dv, text) : List.of(text, dv);
  }

  public void testOmitNormsWhenDocValuesFieldComesFirst() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    TermQuery q = new TermQuery(new Term("title", "fox"));
    for (boolean docValuesFirst : new boolean[] {false, true}) {
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec());
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          w.addDocument(doc(docValuesFirst));
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          LeafReader iw = r.leaves().get(0).reader();
          assertNull(iw.getNormValues("title"));
          float expected = new IndexSearcher(iw).search(q, 1).scoreDocs[0].score;

          MemoryIndex mi = MemoryIndex.fromDocument(doc(docValuesFirst), analyzer);
          LeafReader m = (LeafReader) mi.createSearcher().getIndexReader();
          assertEquals("docValuesFirst=" + docValuesFirst + ": score", expected, mi.search(q), 0f);
          assertNull("docValuesFirst=" + docValuesFirst + ": norms on an omitNorms field", m.getNormValues("title"));
        }
      }
    }
  }
}

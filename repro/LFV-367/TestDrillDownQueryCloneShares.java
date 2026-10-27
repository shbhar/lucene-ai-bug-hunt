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
package org.apache.lucene.facet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * DrillDownQuery#clone shares the per-dimension BooleanQuery.Builder instances with the original,
 * so adding a value to an existing dimension of one copy also changes what the other copy matches.
 */
public class TestDrillDownQueryCloneShares extends LuceneTestCase {

  public void testAddingToCloneChangesOriginal() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (String color : new String[] {"red", "blue"}) {
      Document doc = new Document();
      doc.add(new StringField("color", color, Field.Store.NO));
      w.addDocument(doc);
    }
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = newSearcher(r);

    FacetsConfig config = new FacetsConfig();
    DrillDownQuery original = new DrillDownQuery(config);
    original.add("color", new TermQuery(new Term("color", "red")));

    DrillDownQuery clone = original.clone();
    clone.add("color", new TermQuery(new Term("color", "blue"))); // red OR blue, on the clone only

    assertEquals("clone matches red OR blue", 2, searcher.count(clone));
    assertEquals("original must still match only red", 1, searcher.count(original));

    IOUtils.close(r, dir);
  }

  public void testAddingToOriginalChangesClone() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (String color : new String[] {"red", "blue"}) {
      Document doc = new Document();
      doc.add(new StringField("color", color, Field.Store.NO));
      w.addDocument(doc);
    }
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = newSearcher(r);

    FacetsConfig config = new FacetsConfig();
    DrillDownQuery original = new DrillDownQuery(config);
    original.add("color", new TermQuery(new Term("color", "red")));
    DrillDownQuery snapshot = original.clone();

    original.add("color", new TermQuery(new Term("color", "blue")));

    assertEquals("original matches red OR blue", 2, searcher.count(original));
    assertEquals("snapshot taken before the add must still match only red", 1,
        searcher.count(snapshot));

    IOUtils.close(r, dir);
  }
}

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
package org.apache.lucene.facet.taxonomy;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestSearcherTaxonomyManagerCtorOrder extends LuceneTestCase {

  private static class AddDocumentAtGetReader extends InfoStream {
    IndexWriter writer;
    DirectoryTaxonomyWriter taxoWriter;

    @Override
    public void message(String component, String message) {
      if (writer != null && message.equals("flush at getReader")) {
        IndexWriter w = writer;
        writer = null;
        Document doc = new Document();
        doc.add(new FacetField("Author", "Bob"));
        try {
          w.addDocument(new FacetsConfig().build(taxoWriter, doc));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  public void testFirstPairKnowsTheCategoryOfItsDocument() throws Exception {
    AddDocumentAtGetReader hook = new AddDocumentAtGetReader();
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory();
        IndexWriter writer = new IndexWriter(indexDir, new IndexWriterConfig().setInfoStream(hook));
        DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir)) {
      hook.taxoWriter = taxoWriter;
      hook.writer = writer;
      try (SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(writer, null, taxoWriter)) {
        SearcherAndTaxonomy pair = mgr.acquire();
        try {
          assertEquals(1, pair.searcher().getIndexReader().numDocs());
          // expected: the pair's taxonomy knows Author/Bob, its document's category; actual: -1
          assertNotEquals(
              "taxonomy ordinal of Author/Bob",
              TaxonomyReader.INVALID_ORDINAL,
              pair.taxonomyReader().getOrdinal("Author", "Bob"));
        } finally {
          mgr.release(pair);
        }
      }
    }
  }
}

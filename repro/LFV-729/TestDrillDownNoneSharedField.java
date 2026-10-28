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
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * FacetsConfig#setDrillDownTermsIndexing is per dimension, and all dimensions share the default
 * index field $facets. A dimension set to DrillDownTermsIndexing.NONE writes only doc values into
 * $facets, every other dimension also indexes drill-down terms (StringField) into it, so a
 * document that has only the NONE dimension and a document that has another dimension give the
 * shared field two different schemas, and IndexWriter rejects the second document.
 */
public class TestDrillDownNoneSharedField extends LuceneTestCase {

  private static FacetsConfig config() {
    FacetsConfig config = new FacetsConfig();
    // "rating" needs no drill-down; "author" keeps the default (ALL); both use $facets
    config.setDrillDownTermsIndexing("rating", FacetsConfig.DrillDownTermsIndexing.NONE);
    return config;
  }

  public void testTaxonomyNoneDimThenDefaultDim() throws Exception {
    FacetsConfig config = config();
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    try {
      Document doc = new Document();
      doc.add(new FacetField("rating", "5"));
      writer.addDocument(config.build(taxoWriter, doc));

      doc = new Document();
      doc.add(new FacetField("author", "Bob"));
      // expected: accepted, like any document with a configured dimension
      writer.addDocument(config.build(taxoWriter, doc));
      assertEquals(2, writer.getDocStats().numDocs);
    } finally {
      IOUtils.close(writer, taxoWriter, dir, taxoDir);
    }
  }

  public void testTaxonomyDefaultDimThenNoneDim() throws Exception {
    FacetsConfig config = config();
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    try {
      Document doc = new Document();
      doc.add(new FacetField("author", "Bob"));
      writer.addDocument(config.build(taxoWriter, doc));

      doc = new Document();
      doc.add(new FacetField("rating", "5"));
      writer.addDocument(config.build(taxoWriter, doc));
      assertEquals(2, writer.getDocStats().numDocs);
    } finally {
      IOUtils.close(writer, taxoWriter, dir, taxoDir);
    }
  }

  public void testSortedSetNoneDimThenDefaultDim() throws Exception {
    FacetsConfig config = config();
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    try {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("rating", "5"));
      writer.addDocument(config.build(doc));

      doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("author", "Bob"));
      writer.addDocument(config.build(doc));
      assertEquals(2, writer.getDocStats().numDocs);
    } finally {
      IOUtils.close(writer, dir);
    }
  }

  /** Control (the workaround): the NONE dimension in its own index field. */
  public void testControlNoneDimInOwnField() throws Exception {
    FacetsConfig config = config();
    config.setIndexFieldName("rating", "rating_facets");
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    try {
      Document doc = new Document();
      doc.add(new FacetField("rating", "5"));
      writer.addDocument(config.build(taxoWriter, doc));
      doc = new Document();
      doc.add(new FacetField("author", "Bob"));
      writer.addDocument(config.build(taxoWriter, doc));
      assertEquals(2, writer.getDocStats().numDocs);
    } finally {
      IOUtils.close(writer, taxoWriter, dir, taxoDir);
    }
  }

  /** Control: the same documents index fine when every dimension keeps the default ALL. */
  public void testControlAllDefault() throws Exception {
    FacetsConfig config = new FacetsConfig();
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    try {
      Document doc = new Document();
      doc.add(new FacetField("rating", "5"));
      writer.addDocument(config.build(taxoWriter, doc));
      doc = new Document();
      doc.add(new FacetField("author", "Bob"));
      writer.addDocument(config.build(taxoWriter, doc));
      assertEquals(2, writer.getDocStats().numDocs);
    } finally {
      IOUtils.close(writer, taxoWriter, dir, taxoDir);
    }
  }
}

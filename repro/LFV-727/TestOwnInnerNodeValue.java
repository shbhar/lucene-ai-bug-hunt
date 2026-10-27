/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.facet.taxonomy;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

public class TestOwnInnerNodeValue extends LuceneTestCase {

  private record Observed(int topValue, int allValue, int specificValue, List<String> children) {}

  private Observed count(String[]... labels) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("place", true);

    Directory indexDirectory = newDirectory();
    Directory taxonomyDirectory = newDirectory();
    IndexWriter indexWriter = new IndexWriter(indexDirectory, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxonomyWriter = new DirectoryTaxonomyWriter(taxonomyDirectory);
    for (String[] label : labels) {
      Document document = new Document();
      document.add(new FacetField("place", label));
      indexWriter.addDocument(config.build(taxonomyWriter, document));
    }

    DirectoryReader indexReader = DirectoryReader.open(indexWriter);
    DirectoryTaxonomyReader taxonomyReader = new DirectoryTaxonomyReader(taxonomyWriter);
    IndexSearcher searcher = newSearcher(indexReader);
    FacetsCollector collector =
        searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    FastTaxonomyFacetCounts counts =
        new FastTaxonomyFacetCounts(taxonomyReader, config, collector);

    FacetResult top = counts.getTopChildren(10, "place", "continent", "country");
    FacetResult all = counts.getAllChildren("place", "continent", "country");
    int specific = counts.getSpecificValue("place", "continent", "country").intValue();
    List<String> children = new ArrayList<>();
    for (int i = 0; i < all.labelValues.length; i++) {
      children.add(all.labelValues[i].label + "=" + all.labelValues[i].value);
    }
    Observed observed =
        new Observed(top.value.intValue(), all.value.intValue(), specific, children);
    IOUtils.close(
        indexReader,
        taxonomyReader,
        indexWriter,
        taxonomyWriter,
        indexDirectory,
        taxonomyDirectory);
    return observed;
  }

  public void testDirectLabelAndDescendant() throws Exception {
    Observed observed =
        count(
            new String[] {"continent", "country"},
            new String[] {"continent", "country", "city"});
    assertEquals("rolled-up specific count", 2, observed.specificValue);
    assertEquals(
        "children=" + observed.children,
        List.of(2, 2),
        List.of(observed.topValue, observed.allValue));
  }

  public void testControlOnlyDescendants() throws Exception {
    Observed observed =
        count(
            new String[] {"continent", "country", "city-a"},
            new String[] {"continent", "country", "city-b"});
    assertEquals(2, observed.children.size());
    assertTrue(observed.children.contains("city-a=1"));
    assertTrue(observed.children.contains("city-b=1"));
    assertEquals(2, observed.specificValue);
    assertEquals(2, observed.topValue);
    assertEquals(2, observed.allValue);
  }
}

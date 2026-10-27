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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * TaxonomyFacetLabels is the "utility class to easily retrieve previously indexed facet labels,
 * allowing you to skip also adding stored fields for these values". For the same document it
 * returns only the indexed label when the dimension is single-valued, but adds the label's
 * ancestors (and, with requireDimCount, the bare dimension) when the dimension is configured
 * multi-valued: FacetsConfig indexes those ordinals internally for counting.
 */
public class TestTaxonomyFacetLabelsMultiValuedHierarchical extends LuceneTestCase {

  private static List<String> labelsOfOnlyDoc(FacetsConfig config, String dimFilter)
      throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    Document doc = new Document();
    doc.add(new FacetField("Author", "Bob"));
    doc.add(new FacetField("Publish Date", "2010", "10", "15"));
    w.addDocument(config.build(tw, doc));
    w.forceMerge(1);
    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    List<String> labels = new ArrayList<>();
    TaxonomyFacetLabels taxoLabels =
        new TaxonomyFacetLabels(tr, FacetsConfig.DEFAULT_INDEX_FIELD_NAME);
    LeafReaderContext ctx = r.leaves().get(0);
    TaxonomyFacetLabels.FacetLabelReader reader = taxoLabels.getFacetLabelReader(ctx);
    FacetLabel label;
    while ((label = dimFilter == null ? reader.nextFacetLabel(0) : reader.nextFacetLabel(0, dimFilter))
        != null) {
      labels.add(String.join("/", java.util.Arrays.copyOf(label.components, label.length)));
    }
    java.util.Collections.sort(labels);
    IOUtils.close(r, tr, w, tw, dir, taxoDir);
    return labels;
  }

  public void testSingleValuedHierarchicalControl() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    assertEquals(List.of("Author/Bob", "Publish Date/2010/10/15"), labelsOfOnlyDoc(config, null));
  }

  public void testMultiValuedHierarchical() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    config.setMultiValued("Publish Date", true);
    // same document, same indexed labels; main also returns Publish Date/2010 and
    // Publish Date/2010/10, which were never added to the document
    assertEquals(List.of("Author/Bob", "Publish Date/2010/10/15"), labelsOfOnlyDoc(config, null));
    assertEquals(List.of("Publish Date/2010/10/15"), labelsOfOnlyDoc(config, "Publish Date"));
  }

  public void testMultiValuedRequireDimCount() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);
    config.setMultiValued("Author", true);
    config.setRequireDimCount("Author", true);
    // main also returns the bare dimension "Author"
    assertEquals(List.of("Author/Bob", "Publish Date/2010/10/15"), labelsOfOnlyDoc(config, null));
  }
}

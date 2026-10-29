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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaxonomyFacetLabelsMultiValuedHierarchical extends LuceneTestCase {

  public void testLabelsOfMultiValuedHierarchicalDim() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Date", true);
    config.setMultiValued("Date", true);
    Document doc = new Document();
    doc.add(new FacetField("Date", "2010", "10"));
    try (Directory dir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      w.addDocument(config.build(tw, doc));
      try (DirectoryReader r = DirectoryReader.open(w);
          TaxonomyReader tr = new DirectoryTaxonomyReader(tw)) {
        TaxonomyFacetLabels.FacetLabelReader labels =
            new TaxonomyFacetLabels(tr, FacetsConfig.DEFAULT_INDEX_FIELD_NAME)
                .getFacetLabelReader(r.leaves().get(0));
        List<FacetLabel> actual = new ArrayList<>();
        for (FacetLabel l = labels.nextFacetLabel(0); l != null; l = labels.nextFacetLabel(0)) {
          actual.add(l);
        }
        // expected only the added label Date/2010/10, actual also returns its ancestor Date/2010
        assertEquals(List.of(new FacetLabel("Date", "2010", "10")), actual);
      }
    }
  }
}

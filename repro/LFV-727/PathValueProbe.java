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
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Plain-Java probe, public API only: two documents, Publish Date/2010 and Publish Date/2010/10, in a
 * dimension configured only with setHierarchical. Prints the FacetResult of Publish Date/2010 from
 * taxonomy faceting and from SortedSetDocValues faceting, plus getSpecificValue and drill-down.
 */
public class PathValueProbe {
  private static final String[][] PATHS = {{"2010"}, {"2010", "10"}};

  public static void main(String[] args) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date", true);

    try (Directory dir = new ByteBuffersDirectory();
        Directory taxoDir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
        DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir)) {
      for (String[] path : PATHS) {
        Document doc = new Document();
        doc.add(new FacetField("Publish Date", path));
        writer.addDocument(config.build(taxoWriter, doc));
      }
      try (DirectoryReader reader = DirectoryReader.open(writer);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc =
            searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);
        DrillDownQuery q = new DrillDownQuery(config);
        q.add("Publish Date", "2010");
        System.out.println("taxonomy getTopChildren(10, Publish Date, 2010): "
            + facets.getTopChildren(10, "Publish Date", "2010").toString().trim());
        System.out.println("taxonomy getAllChildren(Publish Date, 2010):     "
            + facets.getAllChildren("Publish Date", "2010").toString().trim());
        System.out.println("taxonomy getSpecificValue(Publish Date, 2010):   "
            + facets.getSpecificValue("Publish Date", "2010"));
        System.out.println("drill-down hits on Publish Date/2010:            " + searcher.count(q));
        System.out.println("taxonomy getTopChildren(10, Publish Date):       "
            + facets.getTopChildren(10, "Publish Date").toString().trim());
      }
    }

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String[] path : PATHS) {
        Document doc = new Document();
        doc.add(new SortedSetDocValuesFacetField("Publish Date", path));
        writer.addDocument(config.build(doc));
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc =
            searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(reader, config), fc);
        System.out.println("SortedSetDocValues getTopChildren(10, Publish Date, 2010): "
            + facets.getTopChildren(10, "Publish Date", "2010").toString().trim());
      }
    }
  }
}

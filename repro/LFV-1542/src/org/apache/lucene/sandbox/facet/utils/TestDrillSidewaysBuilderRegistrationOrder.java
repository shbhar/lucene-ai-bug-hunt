package org.apache.lucene.sandbox.facet.utils;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.DrillSideways;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDrillSidewaysBuilderRegistrationOrder extends LuceneTestCase {

  public void testBuildersRegisteredInAnotherOrderThanTheQueryDimensions() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("brand", false);
    config.setMultiValued("color", false);
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir)) {
        for (String[] brandAndColor : new String[][] {{"A", "red"}, {"A", "blue"}, {"B", "red"}}) {
          Document doc = new Document();
          doc.add(new FacetField("brand", brandAndColor[0]));
          doc.add(new FacetField("color", brandAndColor[1]));
          writer.addDocument(config.build(taxoWriter, doc));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        DrillDownQuery query = new DrillDownQuery(config);
        query.add("brand", "A");
        query.add("color", "red");
        TaxonomyFacetBuilder brand = new TaxonomyFacetBuilder(config, taxoReader, "brand");
        new DrillSidewaysFacetOrchestrator()
            .addDrillSidewaysBuilder("color", new TaxonomyFacetBuilder(config, taxoReader, "color"))
            .addDrillSidewaysBuilder("brand", brand)
            .collect(
                query,
                new DrillSideways(new IndexSearcher(reader), config, taxoReader),
                new TopScoreDocCollectorManager(1, 1));
        // expected: brand over the two red documents, A (1) and B (1); actual: A (2)
        assertEquals(
            "dim=brand path=[] value=2 childCount=2\n  A (1)\n  B (1)\n",
            brand.getResult().toString());
      }
    }
  }
}

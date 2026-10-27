package org.apache.lucene.facet.v1check;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.AssociationAggregationFunction;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetFloatAssociations;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: FacetResult.value for a sub-path of a single-valued hierarchical taxonomy
 * dimension, when some documents are labeled with the sub-path itself (not only below it).
 *
 * <p>Data: doc A labeled Year/2010, doc B labeled Year/2010/05. Both match a drill-down on
 * Year/2010. FacetResult.value javadoc: "Total number of documents containing a value for this
 * path, even those not included in the topN."
 *
 * <p>Uses only public API. Index layout is deterministic (plain IndexWriterConfig, single commit).
 */
public class TestSubPathValue extends LuceneTestCase {

  private static final String DIM = "Year";

  /** Values observed for path Year/2010: {getTopChildren.value, getAllChildren.value, specific, ddHits}. */
  private static final class Observed {
    Number top;
    Number all;
    Number specific;
    int drillDownHits;
    Number dimTop;

    @Override
    public String toString() {
      return "getTopChildren(Year/2010).value="
          + top
          + " getAllChildren(Year/2010).value="
          + all
          + " getSpecificValue(Year/2010)="
          + specific
          + " drillDownHits(Year/2010)="
          + drillDownHits
          + " getTopChildren(Year).value="
          + dimTop;
    }
  }

  private static Observed observe(Facets facets, IndexSearcher searcher, FacetsConfig config)
      throws Exception {
    Observed o = new Observed();
    FacetResult top = facets.getTopChildren(10, DIM, "2010");
    FacetResult all = facets.getAllChildren(DIM, "2010");
    FacetResult dimTop = facets.getTopChildren(10, DIM);
    o.top = top == null ? null : top.value;
    o.all = all == null ? null : all.value;
    o.dimTop = dimTop == null ? null : dimTop.value;
    o.specific = facets.getSpecificValue(DIM, "2010");
    DrillDownQuery ddq = new DrillDownQuery(config);
    ddq.add(DIM, "2010");
    o.drillDownHits = searcher.count(ddq);
    return o;
  }

  /**
   * Builds the taxonomy index: target docs A (Year/2010) and B (Year/2010/05), one more target doc
   * C (Year/2011/01), plus {@code fillerDocs} non-target docs; when {@code fillerLabels} > 0 the
   * filler docs carry that many distinct labels in another (multi-valued) dim, which makes the
   * taxonomy large enough (>= 1024 ordinals) for the sparse (hash map) counting branch.
   */
  private Observed runTaxonomy(FacetsConfig config, int fillerDocs, int fillerLabels)
      throws Exception {
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      config.setMultiValued("Filler", true);
      try (IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        iw.addDocument(config.build(tw, targetDoc(new FacetField(DIM, "2010"))));
        iw.addDocument(config.build(tw, targetDoc(new FacetField(DIM, "2010", "05"))));
        iw.addDocument(config.build(tw, targetDoc(new FacetField(DIM, "2011", "01"))));
        int label = 0;
        for (int i = 0; i < fillerDocs; i++) {
          Document d = new Document();
          d.add(new StringField("kind", "filler", Field.Store.NO));
          int perDoc = fillerDocs == 0 ? 0 : (fillerLabels + fillerDocs - 1) / fillerDocs;
          for (int j = 0; j < perDoc && label < fillerLabels; j++) {
            d.add(new FacetField("Filler", "f" + (label++)));
          }
          if (perDoc == 0 || fillerLabels == 0) {
            d.add(new FacetField("Filler", "f0"));
          }
          iw.addDocument(config.build(tw, d));
        }
        iw.commit();
        tw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q =
            fillerDocs == 0
                ? new MatchAllDocsQuery()
                : new TermQuery(new Term("kind", "target"));
        FacetsCollector fc = s.search(q, new FacetsCollectorManager());
        System.out.println(
            "taxoSize=" + tr.getSize() + " maxDoc=" + r.maxDoc() + " hits=" + s.count(q)
                + " (sparse branch iff taxoSize>=1024 and hits<maxDoc/10)");
        Facets facets = new FastTaxonomyFacetCounts(tr, config, fc);
        Observed o = observe(facets, s, config);
        // when filler docs exist, drill-down must be restricted to targets for a like-for-like
        // hit count; filler docs carry no Year label, so the unrestricted count is the same.
        return o;
      }
    }
  }

  private static Document targetDoc(FacetField ff) {
    Document d = new Document();
    d.add(new StringField("kind", "target", Field.Store.NO));
    d.add(ff);
    return d;
  }

  /** The claim: single-valued hierarchical dim (setHierarchical only), dense counting branch. */
  public void testSingleValuedHierarchicalDense() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    Observed o = runTaxonomy(config, 0, 0);
    System.out.println("TAXO single-valued hierarchical (dense): " + o);
    assertEquals("precondition: dim value", 3, o.dimTop.intValue());
    assertEquals("precondition: drill-down", 2, o.drillDownHits);
    assertEquals("precondition: getSpecificValue", 2, o.specific.intValue());
    assertEquals("getTopChildren(Year/2010).value; " + o, 2, o.top.intValue());
    assertEquals("getAllChildren(Year/2010).value; " + o, 2, o.all.intValue());
  }

  /** Same as above with a taxonomy of >= 1024 ordinals and < 10% hits: sparse counting branch. */
  public void testSingleValuedHierarchicalSparse() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    Observed o = runTaxonomy(config, 60, 1100);
    System.out.println("TAXO single-valued hierarchical (sparse): " + o);
    assertEquals("precondition: dim value", 3, o.dimTop.intValue());
    assertEquals("precondition: drill-down", 2, o.drillDownHits);
    assertEquals("precondition: getSpecificValue", 2, o.specific.intValue());
    assertEquals("getTopChildren(Year/2010).value; " + o, 2, o.top.intValue());
    assertEquals("getAllChildren(Year/2010).value; " + o, 2, o.all.intValue());
  }

  /** Control: same docs, taxonomy dim multi-valued + requireDimCount. */
  public void testControlMultiValuedRequireDimCount() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    Observed o = runTaxonomy(config, 0, 0);
    System.out.println("TAXO multi-valued+requireDimCount hierarchical (control): " + o);
    assertEquals(2, o.drillDownHits);
    assertEquals(2, o.specific.intValue());
    assertEquals("" + o, 2, o.top.intValue());
    assertEquals("" + o, 2, o.all.intValue());
  }

  /** Control: same docs and same single-valued hierarchical config, SortedSetDocValues faceting. */
  public void testControlSortedSetSameConfig() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    try (Directory indexDir = newDirectory()) {
      try (IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig())) {
        Document a = new Document();
        a.add(new SortedSetDocValuesFacetField(DIM, "2010"));
        iw.addDocument(config.build(a));
        Document b = new Document();
        b.add(new SortedSetDocValuesFacetField(DIM, "2010", "05"));
        iw.addDocument(config.build(b));
        Document c = new Document();
        c.add(new SortedSetDocValuesFacetField(DIM, "2011", "01"));
        iw.addDocument(config.build(c));
        iw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        DefaultSortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(r, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        Observed o = observe(facets, s, config);
        System.out.println("SSDV single-valued hierarchical (control): " + o);
        assertEquals(2, o.drillDownHits);
        assertEquals(2, o.specific.intValue());
        assertEquals("" + o, 2, o.top.intValue());
        assertEquals("" + o, 2, o.all.intValue());
      }
    }
  }
  /**
   * Observation (breadth): value-source SUM aggregation on the same single-valued hierarchical dim.
   * Doc A (Year/2010) price 10, doc B (Year/2010/05) price 1. Asserts only internal consistency of
   * one Facets object: getTopChildren(Year/2010).value vs getSpecificValue(Year/2010).
   */
  public void testObservationValueSourceSumConsistency() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(DIM, true);
    try (Directory indexDir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        Document a = new Document();
        a.add(new NumericDocValuesField("price", 10));
        a.add(new FacetField(DIM, "2010"));
        iw.addDocument(config.build(tw, a));
        Document b = new Document();
        b.add(new NumericDocValuesField("price", 1));
        b.add(new FacetField(DIM, "2010", "05"));
        iw.addDocument(config.build(tw, b));
        iw.commit();
        tw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(indexDir);
          TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new TaxonomyFacetFloatAssociations(
                FacetsConfig.DEFAULT_INDEX_FIELD_NAME,
                tr,
                config,
                fc,
                AssociationAggregationFunction.SUM,
                DoubleValuesSource.fromLongField("price"));
        FacetResult top = facets.getTopChildren(10, DIM, "2010");
        Number specific = facets.getSpecificValue(DIM, "2010");
        System.out.println(
            "ASSOC value-source SUM single-valued hierarchical: getTopChildren(Year/2010)="
                + top
                + " getSpecificValue(Year/2010)="
                + specific);
        assertEquals(
            "path value vs getSpecificValue", specific.floatValue(), top.value.floatValue(), 0f);
      }
    }
  }
}

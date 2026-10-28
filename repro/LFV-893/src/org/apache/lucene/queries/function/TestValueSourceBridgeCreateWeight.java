/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.queries.function;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.SumTotalTermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TotalTermFreqValueSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ValueSource#asDoubleValuesSource / #asLongValuesSource ("Expose this ValueSource as a
 * DoubleValuesSource / LongValuesSource") build a fresh context per segment and call getValues
 * without ever calling createWeight, which ValueSource's own consumers (FunctionQuery,
 * FunctionRangeQuery, ValueSource#getSortField) always do first. ValueSources that compute their
 * per-query state in createWeight (ttf, sttf) then fail through the bridge.
 */
public class TestValueSourceBridgeCreateWeight extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document d = new Document();
      d.add(new TextField("text", "test test x", Field.Store.NO));
      w.addDocument(d);
      d = new Document();
      d.add(new TextField("text", "test y", Field.Store.NO));
      w.addDocument(d);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static ValueSource ttf() {
    return new TotalTermFreqValueSource("text", "test", "text", new BytesRef("test"));
  }

  private void assertAllScores(Query q, float expected) throws Exception {
    TopDocs td = searcher.search(q, 10);
    assertEquals(2, td.scoreDocs.length);
    for (ScoreDoc sd : td.scoreDocs) {
      assertEquals(q.toString(), expected, sd.score, 0f);
    }
  }

  public void testNativeConsumersAreTheReference() throws Exception {
    // ttf(text:test) = 3 and sttf(text) = 5 for every document, through FunctionQuery and through
    // the ValueSource's own SortField
    assertAllScores(new FunctionQuery(ttf()), 3f);
    assertAllScores(new FunctionQuery(new SumTotalTermFreqValueSource("text")), 5f);
    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, new Sort(ttf().getSortField(false)));
    assertEquals(2, td.scoreDocs.length);
  }

  public void testAsDoubleValuesSourceTtf() throws Exception {
    assertAllScores(new FunctionScoreQuery(new MatchAllDocsQuery(), ttf().asDoubleValuesSource()), 3f);
  }

  public void testAsDoubleValuesSourceSttf() throws Exception {
    assertAllScores(
        FunctionScoreQuery.boostByValue(
            new MatchAllDocsQuery(), new SumTotalTermFreqValueSource("text").asDoubleValuesSource()),
        5f);
  }

  public void testAsLongValuesSourceSort() throws Exception {
    TopDocs td =
        searcher.search(
            new MatchAllDocsQuery(), 10, new Sort(ttf().asLongValuesSource().getSortField(false)));
    assertEquals(2, td.scoreDocs.length);
    for (ScoreDoc sd : td.scoreDocs) {
      assertEquals(3L, ((FieldDoc) sd).fields[0]);
    }
  }

  /**
   * A user ValueSource that follows ValueSource#createWeight to the letter: it computes a per-query
   * statistic once in createWeight, stores it in the context, and reads it back in getValues.
   */
  static final class PerQueryStatValueSource extends ValueSource {
    @Override
    public void createWeight(java.util.Map<Object, Object> context, IndexSearcher searcher) {
      context.put(this, (float) searcher.getIndexReader().numDocs());
    }

    @Override
    public FunctionValues getValues(
        java.util.Map<Object, Object> context, org.apache.lucene.index.LeafReaderContext leaf) {
      final Float stat = (Float) context.get(this);
      if (stat == null) {
        throw new IllegalStateException("getValues called on a context that createWeight never saw");
      }
      return new org.apache.lucene.queries.function.docvalues.FloatDocValues(this) {
        @Override
        public float floatVal(int doc) {
          return stat;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      return o == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String description() {
      return "numDocsOnce()";
    }
  }

  public void testUserValueSourceThroughBridges() throws Exception {
    // reference: the user source on its own scores numDocs = 2, through FunctionQuery
    assertAllScores(new FunctionQuery(new PerQueryStatValueSource()), 2f);
    // the same source as a DoubleValuesSource
    assertAllScores(
        new FunctionScoreQuery(
            new MatchAllDocsQuery(), new PerQueryStatValueSource().asDoubleValuesSource()),
        2f);
  }
}

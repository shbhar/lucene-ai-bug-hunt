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
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queries.function.valuesource.RangeMapFloatFunction;
import org.apache.lucene.queries.function.valuesource.SumTotalTermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TotalTermFreqValueSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * RangeMapFloatFunction (map(x,min,max,target,default)) accepts ValueSources as target and default,
 * but its createWeight only reaches the mapped source. ValueSource#createWeight says
 * "Implementations should propagate createWeight to sub-ValueSources". A target or default that
 * keeps its per-query state in the context (ttf, sttf) is then read without it.
 */
public class TestRangeMapCreateWeight extends LuceneTestCase {

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

  private void assertAllScores(FunctionQuery q, float expected) throws Exception {
    TopDocs td = searcher.search(q, 10);
    assertEquals(2, td.scoreDocs.length);
    for (ScoreDoc sd : td.scoreDocs) {
      assertEquals(q.toString(), expected, sd.score, 0f);
    }
  }

  public void testReferenceValues() throws Exception {
    // the sub-sources on their own: ttf(text:test) = 3, sttf(text) = 5
    assertAllScores(
        new FunctionQuery(new TotalTermFreqValueSource("text", "test", "text", new BytesRef("test"))),
        3f);
    assertAllScores(new FunctionQuery(new SumTotalTermFreqValueSource("text")), 5f);
  }

  public void testTargetValueSource() throws Exception {
    // 1 is in [0, 2], so every document maps to the target ttf(text:test) = 3
    ValueSource ttf = new TotalTermFreqValueSource("text", "test", "text", new BytesRef("test"));
    assertAllScores(
        new FunctionQuery(new RangeMapFloatFunction(new ConstValueSource(1f), 0f, 2f, ttf, null)),
        3f);
  }

  public void testDefaultValueSource() throws Exception {
    // 5 is outside [0, 2], so every document maps to the default sttf(text) = 5
    ValueSource sttf = new SumTotalTermFreqValueSource("text");
    assertAllScores(
        new FunctionQuery(
            new RangeMapFloatFunction(
                new ConstValueSource(5f), 0f, 2f, new ConstValueSource(7f), sttf)),
        5f);
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

  public void testUserValueSourceAsTargetAndDefault() throws Exception {
    // reference: the user source on its own scores numDocs = 2
    assertAllScores(new FunctionQuery(new PerQueryStatValueSource()), 2f);
    // as map() target (1 is in [0, 2]) and as map() default (5 is outside [0, 2])
    assertAllScores(
        new FunctionQuery(
            new RangeMapFloatFunction(
                new ConstValueSource(1f), 0f, 2f, new PerQueryStatValueSource(), null)),
        2f);
    assertAllScores(
        new FunctionQuery(
            new RangeMapFloatFunction(
                new ConstValueSource(5f),
                0f,
                2f,
                new ConstValueSource(7f),
                new PerQueryStatValueSource())),
        2f);
  }
}

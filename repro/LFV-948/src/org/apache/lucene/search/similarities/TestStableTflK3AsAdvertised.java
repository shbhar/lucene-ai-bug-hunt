/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.search.similarities;

import java.lang.reflect.Constructor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The Lucene 10.6.0 section of lucene/CHANGES.txt (New Features) says:
 *
 * <pre>
 * GITHUB#16243: Add StableTflSimilarity, a similarity that estimates term rarity from term length
 *   and document length instead of corpus statistics such as document frequency. It also supports k3
 *   query-term frequency saturation.
 * </pre>
 *
 * This test checks that claim against the jars it runs on: StableTflSimilarity must expose a k3
 * parameter, and with k3 = 7 a query term that appears twice must be weighted (k3 + 1) * 2 / (k3 + 2)
 * = 16/9 instead of 2. The constructor is looked up reflectively (public API only) so the same source
 * compiles on every build that has StableTflSimilarity.
 */
public class TestStableTflK3AsAdvertised extends LuceneTestCase {

  private static final String CLAIM =
      "CHANGES.txt (Lucene 10.6.0, GITHUB#16243) says StableTflSimilarity 'also supports k3"
          + " query-term frequency saturation'";

  private static Constructor<StableTflSimilarity> k3Constructor() {
    try {
      return StableTflSimilarity.class.getConstructor(float.class, float.class, float.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  public void testK3ParameterExists() throws Exception {
    boolean ctor = k3Constructor() != null;
    boolean getter;
    try {
      StableTflSimilarity.class.getMethod("getK3");
      getter = true;
    } catch (NoSuchMethodException e) {
      getter = false;
    }
    assertTrue(
        CLAIM
            + ", but this build has no StableTflSimilarity(float k1, float c, float k3) constructor"
            + " (found: "
            + ctor
            + ") and no getK3() (found: "
            + getter
            + ")",
        ctor && getter);
  }

  public void testDuplicateQueryTermIsSaturated() throws Exception {
    float k1 = StableTflSimilarity.DEFAULT_K1;
    float c = StableTflSimilarity.DEFAULT_C;
    float k3 = 7f;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setSimilarity(new StableTflSimilarity());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d = new Document();
        d.add(new TextField("t", "a b", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query single = new TermQuery(new Term("t", "a"));
        Query twice =
            new BooleanQuery.Builder()
                .add(single, BooleanClause.Occur.SHOULD)
                .add(single, BooleanClause.Occur.SHOULD)
                .build();

        // what the public constructors of this build give: no k3, so a repeated term counts twice
        s.setSimilarity(new StableTflSimilarity(k1, c));
        float linearRatio = s.search(twice, 1).scoreDocs[0].score / s.search(single, 1).scoreDocs[0].score;

        Constructor<StableTflSimilarity> ctor = k3Constructor();
        assertNotNull(
            CLAIM
                + ", but k3 cannot be configured on this build; a query term repeated twice scores "
                + linearRatio
                + "x a single occurrence (linear, no saturation)",
            ctor);
        s.setSimilarity(ctor.newInstance(k1, c, k3));
        float ratio = s.search(twice, 1).scoreDocs[0].score / s.search(single, 1).scoreDocs[0].score;
        assertEquals(
            "k3=" + k3 + " saturation of a twice-repeated term", (k3 + 1) * 2 / (k3 + 2), ratio, 1e-5f);
      }
    }
  }
}

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
package zeroconfig;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField;
import org.apache.lucene.geo.Polygon;

/**
 * Zero-configuration variant: default IndexWriterConfig, default IndexSearcher query cache and
 * caching policy (no cache is installed explicitly). Exit code 1 if the cached result is stale.
 */
public class ZeroConfigShapeDocValues {
  public static void main(String[] args) throws Exception {
    System.out.println("default query cache: " + IndexSearcher.getDefaultQueryCache());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 12_000; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        double c = i < 5 ? i : 50;
        doc.add(LatLonShape.createDocValueField("shape", new Polygon(new double[] {c - 0.1, c - 0.1, c + 0.1, c + 0.1, c - 0.1}, new double[] {c - 0.1, c + 0.1, c + 0.1, c - 0.1, c - 0.1})));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      Query query = LatLonShape.newSlowDocValuesBoxQuery("shape", ShapeField.QueryRelation.INTERSECTS, -1, 4.5, -1, 4.5);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        int before = 0;
        for (int k = 0; k < 6; k++) { // the default policy caches a filter after a few uses
          before = new IndexSearcher(r1).count(query);
        }
        w.updateDocValues(new Term("id", "0"), LatLonShape.createDocValueField("shape", new Polygon(new double[] {59.9, 59.9, 60.1, 60.1, 59.9}, new double[] {59.9, 60.1, 60.1, 59.9, 59.9})));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          int cached = new IndexSearcher(r2).count(query);
          IndexSearcher uncachedSearcher = new IndexSearcher(r2);
          uncachedSearcher.setQueryCache(null);
          int uncached = uncachedSearcher.count(query);
          System.out.println("before update: " + before + ", after update: default searcher " + cached + ", uncached " + uncached);
          if (cached != uncached) {
            System.out.println("STALE: the default query cache served the pre-update result");
            System.exit(1);
          }
          System.out.println("OK");
        }
      }
    }
  }
}

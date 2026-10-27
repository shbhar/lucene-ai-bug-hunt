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
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiDoubleValues;
import org.apache.lucene.facet.MultiDoubleValuesSource;
import org.apache.lucene.facet.range.DoubleRange;
import org.apache.lucene.facet.range.DoubleRangeFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Scale probe (not a test): 1,000 documents, each with two values in random order. (a) Distances
 * from the user to two locations, uniform in [0, 10) km, through a custom MultiDoubleValuesSource.
 * (b) Two temperatures, uniform in [-20, 20), stored as raw-bit doubles in a SortedNumeric field and
 * counted through the DoubleRangeFacetCounts(String, ...) constructor. Prints facet counts next to
 * the drill-down counts of the same ranges. Exits 1 if any facet count differs.
 */
public class RangeOrderScaleProbe {
  static final int DOCS = 1000;

  public static void main(String[] args) throws Exception {
    Random random = new Random(42);
    double[][] distances = new double[DOCS][];
    double[][] temperatures = new double[DOCS][];
    boolean mismatch = false;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int d = 0; d < DOCS; d++) {
          distances[d] = new double[] {10 * random.nextDouble(), 10 * random.nextDouble()};
          temperatures[d] = new double[] {-20 + 40 * random.nextDouble(), -20 + 40 * random.nextDouble()};
          Document doc = new Document();
          doc.add(new NumericDocValuesField("id", d));
          for (double t : temperatures[d]) {
            doc.add(new SortedNumericDocValuesField("temp", Double.doubleToRawLongBits(t)));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc = s.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());

        MultiDoubleValuesSource dist = new PerDoc(distances);
        DoubleRange[] distRanges = {
          new DoubleRange("< 1 km", 0.0, true, 1.0, false),
          new DoubleRange("< 2 km", 0.0, true, 2.0, false),
          new DoubleRange("< 5 km", 0.0, true, 5.0, false)
        };
        mismatch |=
            report("distances, custom source", s, new DoubleRangeFacetCounts("d", dist, fc, distRanges), "d", distRanges, dist);

        MultiDoubleValuesSource temp = MultiDoubleValuesSource.fromDoubleField("temp");
        DoubleRange[] tempRanges = {
          new DoubleRange("[-20, -10)", -20.0, true, -10.0, false),
          new DoubleRange("[-10, 0)", -10.0, true, 0.0, false),
          new DoubleRange("[0, 10)", 0.0, true, 10.0, false),
          new DoubleRange("[10, 20)", 10.0, true, 20.0, false)
        };
        mismatch |=
            report("temperatures, raw bits, field constructor", s, new DoubleRangeFacetCounts("temp", fc, tempRanges), "temp", tempRanges, temp);
      }
    }
    System.exit(mismatch ? 1 : 0);
  }

  static boolean report(String what, IndexSearcher s, Facets facets, String dim, DoubleRange[] ranges, MultiDoubleValuesSource src)
      throws IOException {
    FacetResult res = facets.getAllChildren(dim);
    int[] facet = new int[ranges.length];
    int[] drill = new int[ranges.length];
    for (int i = 0; i < ranges.length; i++) {
      facet[i] = res.labelValues[i].value.intValue();
      drill[i] = s.count(ranges[i].getQuery(null, src));
    }
    System.out.println(what + ": ranges " + Arrays.toString(Arrays.stream(ranges).map(x -> x.label).toArray()));
    System.out.println("  facet counts      " + Arrays.toString(facet) + ", total " + res.value);
    System.out.println("  drill-down counts " + Arrays.toString(drill));
    return Arrays.equals(facet, drill) == false;
  }

  /** Delivers each document's values in the generated order (looked up by "id"). */
  static final class PerDoc extends MultiDoubleValuesSource {
    final double[][] values;

    PerDoc(double[][] values) {
      this.values = values;
    }

    @Override
    public MultiDoubleValues getValues(LeafReaderContext ctx) throws IOException {
      NumericDocValues ids = DocValues.getNumeric(ctx.reader(), "id");
      return new MultiDoubleValues() {
        double[] cur;
        int upto;

        @Override
        public long getValueCount() {
          return cur.length;
        }

        @Override
        public double nextValue() {
          return cur[upto++];
        }

        @Override
        public boolean advanceExact(int doc) throws IOException {
          if (ids.advanceExact(doc) == false) {
            return false;
          }
          cur = values[(int) ids.longValue()];
          upto = 0;
          return true;
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public String toString() {
      return "PerDoc";
    }
  }
}

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
package briefprobe;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Brief-step probe (public API only, default IndexWriterConfig and codec, StandardAnalyzer, plain
 * IndexSearcher): document counts for the bug's cases, the or(false, ...) control, and one question
 * the earlier checks had not examined (the source side of overlapping).
 */
public class BriefProbe {
  static final String F = "body";

  static IndexSearcher searcher(Directory dir, String text) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document d = new Document();
      d.add(new TextField(F, text, Field.Store.NO));
      w.addDocument(d);
    }
    IndexReader r = DirectoryReader.open(dir);
    return new IndexSearcher(r);
  }

  static int c(IndexSearcher s, IntervalsSource src) throws IOException {
    return s.count(new IntervalQuery(F, src));
  }

  public static void main(String[] args) throws IOException {
    IntervalsSource york = Intervals.term("york");
    IntervalsSource newYork = Intervals.phrase("new", "york");
    IntervalsSource r = Intervals.or(york, newYork);
    IntervalsSource rNoRewrite = Intervals.or(false, york, newYork);
    IntervalsSource visit = Intervals.term("visit");
    IntervalsSource nw = Intervals.term("new");
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexSearcher s = searcher(dir, "visit new york");
      System.out.println("doc 'visit new york':");
      System.out.println("  within(visit, 1, \"new york\")      = " + c(s, Intervals.within(visit, 1, newYork)));
      System.out.println("  notWithin(visit, 1, \"new york\")   = " + c(s, Intervals.notWithin(visit, 1, newYork)));
      System.out.println("  within(visit, 1, york)            = " + c(s, Intervals.within(visit, 1, york)));
      System.out.println("  notWithin(visit, 1, york)         = " + c(s, Intervals.notWithin(visit, 1, york)));
      System.out.println("  within(visit, 1, R)               = " + c(s, Intervals.within(visit, 1, r)));
      System.out.println("  notWithin(visit, 1, R)            = " + c(s, Intervals.notWithin(visit, 1, r)));
      System.out.println("  within(visit, 1, R_norewrite)     = " + c(s, Intervals.within(visit, 1, rNoRewrite)));
      System.out.println("  notWithin(visit, 1, R_norewrite)  = " + c(s, Intervals.notWithin(visit, 1, rNoRewrite)));
      s.getIndexReader().close();
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexSearcher s = searcher(dir, "new york");
      System.out.println("doc 'new york':");
      System.out.println("  overlapping(new, \"new york\")      = " + c(s, Intervals.overlapping(nw, newYork)));
      System.out.println("  containedBy(new, R)               = " + c(s, Intervals.containedBy(nw, r)));
      System.out.println("  overlapping(new, R)               = " + c(s, Intervals.overlapping(nw, r)));
      System.out.println("  nonOverlapping(new, R)            = " + c(s, Intervals.nonOverlapping(nw, r)));
      System.out.println("  notWithin(new, 0, R)              = " + c(s, Intervals.notWithin(nw, 0, r)));
      System.out.println("  maxgaps(0, overlapping(new, R))   = " + c(s, Intervals.maxgaps(0, Intervals.overlapping(nw, r))));
      System.out.println("  containedBy(new, R_norewrite)     = " + c(s, Intervals.containedBy(nw, rNoRewrite)));
      System.out.println("  overlapping(new, R_norewrite)     = " + c(s, Intervals.overlapping(nw, rNoRewrite)));
      // not part of the claim: the SOURCE side of overlapping is a disjunction whose alternatives
      // contain one another (the reference is a single term)
      IntervalsSource srcOr = Intervals.or(nw, newYork);
      System.out.println("  [source side] overlapping(or(new, \"new york\"), york)              = "
          + c(s, Intervals.overlapping(srcOr, york)));
      System.out.println("  [source side] maxgaps(0, overlapping(or(new, \"new york\"), york))  = "
          + c(s, Intervals.maxgaps(0, Intervals.overlapping(srcOr, york))));
      s.getIndexReader().close();
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      // user-side workaround on stock Lucene: spell the alternatives out with public factories
      IndexSearcher s = searcher(dir, "visit new york");
      IntervalsSource wNotWithin =
          Intervals.notWithin(Intervals.notWithin(visit, 1, york), 1, newYork);
      System.out.println("workaround on 'visit new york':");
      System.out.println("  notWithin(notWithin(visit, 1, york), 1, \"new york\")          = " + c(s, wNotWithin));
      s.getIndexReader().close();
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexSearcher s = searcher(dir, "new york");
      System.out.println("workaround on 'new york':");
      System.out.println("  or(overlapping(new, york), overlapping(new, \"new york\"))     = "
          + c(s, Intervals.or(Intervals.overlapping(nw, york), Intervals.overlapping(nw, newYork))));
      System.out.println("  nonOverlapping(nonOverlapping(new, york), \"new york\")        = "
          + c(s, Intervals.nonOverlapping(Intervals.nonOverlapping(nw, york), newYork)));
      s.getIndexReader().close();
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexSearcher s = searcher(dir, "new york city hall");
      IntervalsSource r2 = Intervals.or(newYork, Intervals.phrase("new", "york", "city"));
      IntervalsSource hall = Intervals.term("hall");
      System.out.println("doc 'new york city hall', R2 = or(\"new york\", \"new york city\"):");
      System.out.println("  within(hall, 1, R2)               = " + c(s, Intervals.within(hall, 1, r2)));
      System.out.println("  notWithin(hall, 1, R2)            = " + c(s, Intervals.notWithin(hall, 1, r2)));
      s.getIndexReader().close();
    }
  }
}

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
package probe;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexSorter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;

/**
 * Version-portable probe of the root cause, written only against public API that exists unchanged
 * from Lucene 8.6.0 to main: SortField#getIndexSorter, IndexSorter.LongSorter and SortFieldProvider.
 *
 * <p>A custom SortField declares the field name "sku", but its IndexSorter orders documents by the
 * NUMERIC doc values of "price". IndexWriter's doc-values update guard knows only "sku".
 *
 * <ol>
 *   <li>Sanity: documents are inserted a(7), b(5), c(8); the flushed segment must be in price order
 *       b, a, c, which shows that the sorter really reads "price".
 *   <li>Control: an update of the declared field "sku" must be rejected (the guard exists).
 *   <li>Probe: an update of "price" (the field the sorter reads) must either be rejected, or leave
 *       the segment in its declared sort order. If it is accepted and the segment still declares the
 *       sort while its documents are out of that order, the probe throws (exit code 1).
 * </ol>
 *
 * <p>The oracle is direct: read "price" in docID order from the single segment and check it is
 * non-decreasing, which is the order the declared index sort promises. (CheckIndex is not used
 * because its sort check calls the search comparator, whose signature differs across versions.)
 *
 * <p>Two update paths are probed: {@code updateNumericDocValue} and {@code updateDocValues(Term,
 * Field...)}. Run as main(); exits 0 when the behaviour is correct.
 */
public class SorterReadsOtherFieldUpdateProbe {

  static final String NAME = "HistoryReadsPriceSortField";

  /** Declares field "sku" but sorts by the NUMERIC doc values of "price". */
  public static final class ReadsPriceSortField extends SortField {
    public ReadsPriceSortField(String field) {
      super(field, SortField.Type.CUSTOM, false);
    }

    @Override
    public IndexSorter getIndexSorter() {
      return new IndexSorter.LongSorter(
          NAME, null, false, reader -> DocValues.getNumeric(reader, "price"));
    }
  }

  /** Reads the sort back from the segment header. */
  public static final class Provider extends SortFieldProvider {
    public Provider() {
      super(NAME);
    }

    @Override
    public SortField readSortField(DataInput in) throws IOException {
      return new ReadsPriceSortField(in.readString());
    }

    @Override
    public void writeSortField(SortField sf, DataOutput out) throws IOException {
      out.writeString(sf.getField());
    }
  }

  static void registerProvider() throws IOException {
    if (SortFieldProvider.availableSortFieldProviders().contains(NAME)) {
      return;
    }
    Path dir = Files.createTempDirectory("spi");
    Path services = dir.resolve("META-INF/services/" + SortFieldProvider.class.getName());
    Files.createDirectories(services.getParent());
    Files.write(services, (Provider.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {dir.toUri().toURL()},
            SorterReadsOtherFieldUpdateProbe.class.getClassLoader())) {
      SortFieldProvider.reloadSortFieldProviders(loader);
    }
    if (SortFieldProvider.availableSortFieldProviders().contains(NAME) == false) {
      throw new IllegalStateException("harness: SortFieldProvider " + NAME + " not registered");
    }
  }

  static Document doc(String id, long price) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("price", price));
    doc.add(new NumericDocValuesField("sku", id.charAt(0)));
    return doc;
  }

  /** Returns "id:price" for each doc of the single segment, in docID order. */
  static List<long[]> readOrder(Directory dir) throws IOException {
    List<long[]> out = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      if (r.leaves().size() != 1) {
        throw new IllegalStateException("harness: expected 1 segment, got " + r.leaves().size());
      }
      LeafReader leaf = r.leaves().get(0).reader();
      NumericDocValues price = leaf.getNumericDocValues("price");
      NumericDocValues sku = leaf.getNumericDocValues("sku");
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (price.nextDoc() != d || sku.nextDoc() != d) {
          throw new IllegalStateException("harness: missing value at doc " + d);
        }
        out.add(new long[] {sku.longValue(), price.longValue()});
      }
      if (price.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        throw new IllegalStateException("harness: extra price value");
      }
    }
    return out;
  }

  static String fmt(List<long[]> order) {
    StringBuilder sb = new StringBuilder("[");
    for (long[] e : order) {
      if (sb.length() > 1) sb.append(", ");
      sb.append((char) e[0]).append(':').append(e[1]);
    }
    return sb.append(']').toString();
  }

  static boolean sortedByPrice(List<long[]> order) {
    for (int i = 1; i < order.size(); i++) {
      if (order.get(i - 1)[1] > order.get(i)[1]) return false;
    }
    return true;
  }

  static String declaredSort(Directory dir) throws IOException {
    SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
    StringBuilder sb = new StringBuilder();
    for (SegmentCommitInfo sci : infos) {
      Sort s = sci.info.getIndexSort();
      sb.append(sci.info.name).append(" indexSort=").append(s == null ? "null" : s.toString());
      if (s != null && s.getSort()[0] instanceof ReadsPriceSortField == false) {
        throw new IllegalStateException("harness: unexpected declared sort " + s);
      }
      if (s == null) {
        throw new IllegalStateException("harness: segment does not declare the index sort");
      }
    }
    return sb.toString();
  }

  /** Returns a failure description, or null if this path behaves correctly. */
  static String probe(boolean viaUpdateDocValues) throws IOException {
    String path = viaUpdateDocValues ? "updateDocValues(Term, Field...)" : "updateNumericDocValue";
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setIndexSort(new Sort(new ReadsPriceSortField("sku")))
              .setMergePolicy(NoMergePolicy.INSTANCE);
      boolean controlRejected;
      boolean accepted;
      String acceptMsg = "";
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a", 7));
        w.addDocument(doc("b", 5));
        w.addDocument(doc("c", 8));
        w.commit();
        List<long[]> flushed = readOrder(dir);
        System.out.println("[" + path + "] flushed order (sku:price) = " + fmt(flushed));
        if (fmt(flushed).equals("[b:5, a:7, c:8]") == false) {
          throw new IllegalStateException(
              "harness: flush did not sort by price; the sorter does not read 'price' here");
        }
        // Control: the declared sort field is protected by the guard.
        try {
          if (viaUpdateDocValues) {
            w.updateDocValues(new Term("id", "a"), new NumericDocValuesField("sku", 'z'));
          } else {
            w.updateNumericDocValue(new Term("id", "a"), "sku", 'z');
          }
          controlRejected = false;
        } catch (IllegalArgumentException e) {
          controlRejected = true;
          System.out.println("[" + path + "] control: update of declared field 'sku' rejected: "
              + e.getMessage());
        }
        if (controlRejected == false) {
          throw new IllegalStateException(
              "harness: the update of the declared sort field was accepted; guard absent");
        }
        // Probe: the field the sorter reads.
        try {
          if (viaUpdateDocValues) {
            w.updateDocValues(new Term("id", "c"), new NumericDocValuesField("price", 1));
          } else {
            w.updateNumericDocValue(new Term("id", "c"), "price", 1);
          }
          accepted = true;
        } catch (IllegalArgumentException e) {
          accepted = false;
          acceptMsg = e.getMessage();
        }
        w.commit();
      }
      if (accepted == false) {
        System.out.println("[" + path + "] update of read field 'price' rejected: " + acceptMsg);
        return null;
      }
      List<long[]> after = readOrder(dir);
      String declared = declaredSort(dir);
      boolean sorted = sortedByPrice(after);
      System.out.println("[" + path + "] update of read field 'price' ACCEPTED; segment "
          + declared + "; order after update (sku:price) = " + fmt(after)
          + "; sorted by the declared index sort = " + sorted);
      if (sorted) {
        return null;
      }
      return path + ": update of 'price' (read by the index sorter) was accepted and the segment"
          + " still declares the index sort but is out of order: " + fmt(after);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("lucene core = "
        + IndexWriter.class.getPackage().getImplementationVersion());
    registerProvider();
    List<String> failures = new ArrayList<>();
    for (boolean viaUpdateDocValues : new boolean[] {false, true}) {
      String f = probe(viaUpdateDocValues);
      if (f != null) failures.add(f);
    }
    if (failures.isEmpty() == false) {
      throw new AssertionError("BUG REPRODUCED (" + failures.size() + "/2 paths): "
          + String.join(" | ", failures));
    }
    System.out.println("OK: no path left the segment out of its declared index sort");
  }
}

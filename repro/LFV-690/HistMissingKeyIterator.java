/*
 * History driver for the iterator doc-values API, collector in package org.apache.lucene.search.
 *
 * getKeys() returns a NumericDocValues iterator. When advanceExact(doc) is false (the document has
 * no key) the collector itself picks the key value (DiversifiedTopDocsCollector.insert).
 *
 * Cases (one single-segment index each, MatchAllDocsQuery, so every score is equal and ties go
 * to the lower doc id):
 *   numeric   : d0 has no "seller" value, d1 seller=0; numHits=2, maxHitsPerKey=1.
 *               Key source = the field's own NumericDocValues. Expected: both returned.
 *   ordinal   : d0 has no "artist", d1 "ABBA", d2 "Zappa"; numHits=3, maxHitsPerKey=1.
 *               Key source = the pattern of upstream TestDiversifiedTopDocsCollector in this
 *               era: delegate the SortedDocValues iterator, longValue() returns ordValue(). Expected: all three returned.
 *   control-1 : d0 no seller, d1 seller=1 -> both returned (no key-0 involvement).
 *   control-2 : d0 seller=0, d1 seller=0 -> one returned (the cap itself works).
 *
 * Exit code: 0 = no hit dropped in numeric/ordinal; 1 = at least one of them dropped a hit;
 * 3 = a control case failed (the driver itself is wrong).
 */
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DiversifiedTopDocsCollector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

public class HistMissingKeyIterator {

  static final class NumericKeyCollector extends DiversifiedTopDocsCollector {
    NumericKeyCollector(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      try {
        return DocValues.getNumeric(context.reader(), "seller");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static final class OrdinalKeyCollector extends DiversifiedTopDocsCollector {
    OrdinalKeyCollector(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      final SortedDocValues sdv;
      try {
        sdv = DocValues.getSorted(context.reader(), "artist");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return new NumericDocValues() {
        @Override
        public int docID() {
          return sdv.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return sdv.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return sdv.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return sdv.advanceExact(target);
        }

        @Override
        public long cost() {
          return sdv.cost();
        }

        @Override
        public long longValue() throws IOException {
          return sdv.ordValue();
        }
      };
    }
  }

  static Path tmpRoot;
  static int dirCounter;

  static Directory index(String[] labels, Long[] sellers, String[] artists) throws IOException {
    Path p = Files.createTempDirectory(tmpRoot, "idx" + (dirCounter++));
    Directory dir = FSDirectory.open(p);
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig((Analyzer) null));
    for (int i = 0; i < labels.length; i++) {
      Document d = new Document();
      d.add(new StoredField("label", labels[i]));
      if (sellers != null && sellers[i] != null) {
        d.add(new NumericDocValuesField("seller", sellers[i]));
      }
      if (artists != null && artists[i] != null) {
        d.add(new SortedDocValuesField("artist", new BytesRef(artists[i])));
      }
      w.addDocument(d);
    }
    w.close();
    return dir;
  }

  /** Returns the number of hits and prints them. */
  static int run(String name, Directory dir, String[] labels, DiversifiedTopDocsCollector c)
      throws IOException {
    DirectoryReader r = DirectoryReader.open(dir);
    if (r.leaves().size() != 1 || r.maxDoc() != labels.length) {
      throw new IllegalStateException("expected one segment with " + labels.length + " docs");
    }
    IndexSearcher s = new IndexSearcher(r);
    s.search(new MatchAllDocsQuery(), c);
    TopDocs td = c.topDocs();
    List<String> out = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(labels[sd.doc] + "(key=" + ((DiversifiedTopDocsCollector.ScoreDocKey) sd).getKey() + ")");
    }
    System.out.println("  " + name + ": hits=" + out);
    r.close();
    dir.close();
    return td.scoreDocs.length;
  }

  public static void main(String[] args) throws Exception {
    tmpRoot = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "hist");
    System.out.println("lucene " + Version.LATEST + " (iterator doc values API)");
    boolean dropped = false;
    boolean controlFailed = false;

    String[] l2 = {"no-seller", "seller-0"};
    int n = run("numeric   [expect 2]", index(l2, new Long[] {null, 0L}, null), l2,
        new NumericKeyCollector(2, 1));
    boolean numericOk = n == 2;
    dropped |= !numericOk;

    String[] l3 = {"no-artist", "abba", "zappa"};
    n = run("ordinal   [expect 3]", index(l3, null, new String[] {null, "ABBA", "Zappa"}), l3,
        new OrdinalKeyCollector(3, 1));
    boolean ordinalOk = n == 3;
    dropped |= !ordinalOk;

    String[] lc1 = {"no-seller", "seller-1"};
    n = run("control-1 [expect 2]", index(lc1, new Long[] {null, 1L}, null), lc1,
        new NumericKeyCollector(2, 1));
    controlFailed |= n != 2;

    String[] lc2 = {"seller-0-a", "seller-0-b"};
    n = run("control-2 [expect 1]", index(lc2, new Long[] {0L, 0L}, null), lc2,
        new NumericKeyCollector(2, 1));
    controlFailed |= n != 1;

    System.out.println("RESULT numeric=" + (numericOk ? "ok" : "DROPPED")
        + " ordinal=" + (ordinalOk ? "ok" : "DROPPED")
        + " controls=" + (controlFailed ? "FAILED" : "ok"));
    System.exit(controlFailed ? 3 : (dropped ? 1 : 0));
  }
}

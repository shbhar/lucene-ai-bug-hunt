package org.apache.lucene.search;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestSortLaterSegmentPointsWork {

  @Test
  public void testSelectiveLaterSegmentReadsNoPoints() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i <= 1000; i++) {
        addDoc(w, "a", 1000 + i);
      }
      w.flush();
      for (int i = 0; i < 1000; i++) {
        addDoc(w, "b", i);
      }
      addDoc(w, "a", 1_000_000);
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      long[] pointDocsRead = new long[1];
      LeafReader second = new CountingLeafReader(reader.leaves().get(1).reader(), pointDocsRead);
      IndexSearcher searcher =
          new IndexSearcher(new MultiReader(reader.leaves().get(0).reader(), second));
      searcher.search(
          new TermQuery(new Term("f", "a")), 10, new Sort(new SortField("v", SortField.Type.LONG)));
      // Expected 0 (segment 1 has 1 hit, so its points are not worth reading); main since #16857: 1001.
      assertEquals(0, pointDocsRead[0]);
    }
  }

  private static void addDoc(IndexWriter w, String f, long v) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("f", f, Field.Store.NO));
    doc.add(new LongPoint("v", v));
    doc.add(new NumericDocValuesField("v", v));
    w.addDocument(doc);
  }

  private static final class CountingLeafReader extends FilterLeafReader {
    private final long[] count;

    CountingLeafReader(LeafReader in, long[] count) {
      super(in);
      this.count = count;
    }

    @Override
    public PointValues getPointValues(String field) {
      PointValues values = in.getPointValues(field);
      return values == null ? null : new CountingPointValues(values, count);
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  private static final class CountingPointValues extends PointValues {
    private final PointValues in;
    private final long[] count;

    CountingPointValues(PointValues in, long[] count) {
      this.in = in;
      this.count = count;
    }

    @Override
    public PointTree getPointTree() throws IOException {
      return new CountingPointTree(in.getPointTree(), count);
    }

    @Override
    public byte[] getMinPackedValue() {
      return in.getMinPackedValue();
    }

    @Override
    public byte[] getMaxPackedValue() {
      return in.getMaxPackedValue();
    }

    @Override
    public int getNumDimensions() {
      return in.getNumDimensions();
    }

    @Override
    public int getNumIndexDimensions() {
      return in.getNumIndexDimensions();
    }

    @Override
    public int getBytesPerDimension() {
      return in.getBytesPerDimension();
    }

    @Override
    public long size() {
      return in.size();
    }

    @Override
    public int getDocCount() {
      return in.getDocCount();
    }
  }

  private record CountingPointTree(PointValues.PointTree in, long[] count)
      implements PointValues.PointTree {
    @Override
    public PointValues.PointTree clone() {
      return new CountingPointTree(in.clone(), count);
    }

    @Override
    public boolean moveToChild() throws IOException {
      return in.moveToChild();
    }

    @Override
    public boolean moveToSibling() throws IOException {
      return in.moveToSibling();
    }

    @Override
    public boolean moveToParent() throws IOException {
      return in.moveToParent();
    }

    @Override
    public byte[] getMinPackedValue() {
      return in.getMinPackedValue();
    }

    @Override
    public byte[] getMaxPackedValue() {
      return in.getMaxPackedValue();
    }

    @Override
    public long size() {
      return in.size();
    }

    @Override
    public void visitDocIDs(PointValues.IntersectVisitor visitor) throws IOException {
      count[0] += in.size();
      in.visitDocIDs(visitor);
    }

    @Override
    public void visitDocValues(PointValues.IntersectVisitor visitor) throws IOException {
      count[0] += in.size();
      in.visitDocValues(visitor);
    }
  }
}

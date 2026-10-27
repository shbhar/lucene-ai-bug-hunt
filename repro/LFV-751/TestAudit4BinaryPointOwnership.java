package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Audit 4: encode keys into one scratch buffer, build queries, then search once (cache off). */
public class TestAudit4BinaryPointOwnership extends LuceneTestCase {

  private interface Factory {
    Query make(byte[] scratch);
  }

  private int orOfThree(IndexSearcher s, Factory f) throws Exception {
    byte[] scratch = new byte[4];
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int k = 1; k <= 3; k++) {
      scratch[3] = (byte) k; // encode key k into the reused buffer
      b.add(f.make(scratch), BooleanClause.Occur.SHOULD);
    }
    return s.count(b.build());
  }

  public void testEncodeLoop() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int k = 1; k <= 5; k++) {
        Document d = new Document();
        d.add(new BinaryPoint("b1", new byte[] {0, 0, 0, (byte) k}));
        d.add(new BinaryPoint("b2", new byte[] {0, 0, 0, (byte) k}, new byte[] {0, 0, 0, 0}));
        d.add(new IntPoint("i", k));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        // controls first
        int twoDim =
            orOfThree(
                s,
                sc ->
                    BinaryPoint.newRangeQuery(
                        "b2", new byte[][] {sc, new byte[4]}, new byte[][] {sc, new byte[4]}));
        int set = orOfThree(s, sc -> BinaryPoint.newSetQuery("b1", sc));
        int intp = orOfThree(s, sc -> IntPoint.newExactQuery("i", sc[3]));
        assertEquals("2-D control", 3, twoDim);
        assertEquals("newSetQuery control", 3, set);
        assertEquals("IntPoint control", 3, intp);
        int exact = orOfThree(s, sc -> BinaryPoint.newExactQuery("b1", sc));
        assertEquals("1-D BinaryPoint.newExactQuery OR of keys 1..3", 3, exact);
      }
    }
  }
}

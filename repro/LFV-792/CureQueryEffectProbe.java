package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Indexes the self-crossing ring POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)) as one document
 * (XYShape points and doc values, default IndexWriterConfig) and runs every box with corners on a
 * 0.5 grid in [-1, 7] x [-1, 7]: XYShape.newBoxQuery with INTERSECTS, WITHIN, DISJOINT and
 * CONTAINS, and newSlowDocValuesBoxQuery with INTERSECTS, WITHIN and DISJOINT. Prints the number
 * of queries, the number of hits per relation and a hash of every (box, relation, count) result,
 * so a stock run and a run with the duplicate removed can be compared.
 */
public class CureQueryEffectProbe {
  public static void main(String[] args) throws Exception {
    XYPolygon ring = new XYPolygon(new float[] {6, 0, 4, 3, 5, 3, 6}, new float[] {3, 6, 0, 4, 1, 2, 3});
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : XYShape.createIndexableFields("s", ring)) {
          doc.add(f);
        }
        doc.add(XYShape.createDocValueField("dv", ring));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        QueryRelation[] rels = {QueryRelation.INTERSECTS, QueryRelation.WITHIN, QueryRelation.DISJOINT, QueryRelation.CONTAINS};
        long[] hits = new long[7];
        long queries = 0, hash = 17;
        int n = 17; // -1.0, -0.5, ..., 7.0
        for (int x1 = 0; x1 < n; x1++) for (int x2 = x1 + 1; x2 < n; x2++)
          for (int y1 = 0; y1 < n; y1++) for (int y2 = y1 + 1; y2 < n; y2++) {
            float minX = -1 + 0.5f * x1, maxX = -1 + 0.5f * x2, minY = -1 + 0.5f * y1, maxY = -1 + 0.5f * y2;
            for (int k = 0; k < 7; k++) {
              int c = k < 4
                  ? s.count(XYShape.newBoxQuery("s", rels[k], minX, maxX, minY, maxY))
                  : s.count(XYShape.newSlowDocValuesBoxQuery("dv", rels[k - 4], minX, maxX, minY, maxY));
              hits[k] += c;
              queries++;
              hash = hash * 31 + ((((x1 * n + x2) * n + y1) * n + y2) * 7 + k) * 2 + c;
            }
          }
        System.out.println("queries=" + queries
            + " hits: points INTERSECTS=" + hits[0] + " WITHIN=" + hits[1] + " DISJOINT=" + hits[2] + " CONTAINS=" + hits[3]
            + " | doc values INTERSECTS=" + hits[4] + " WITHIN=" + hits[5] + " DISJOINT=" + hits[6]
            + " | result hash=" + Long.toHexString(hash));
      }
    }
  }
}

import java.nio.file.*;
import java.util.*;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.hnsw.HnswGraph;

/**
 * Deterministic release-side generator (default codec, SerialMergeScheduler, explicit
 * TieredMergePolicy): the apache/lucene#14127 indexing pattern with commit points drawn from
 * new Random(seed). For seeds in [from, to) it reports whether a segment's HNSW graph lists a
 * neighbour twice; the first `keep` such indexes are written to outDir/seed-N.
 * args: outDir from to keep segmentsPerTier maxMergeAtOnce [maxBufferedDocs floorSegmentMB
 * targetSearchConcurrency]
 */
public class DupGen3 {
  public static void main(String[] args) throws Exception {
    Path out = Paths.get(args[0]);
    int from = Integer.parseInt(args[1]), to = Integer.parseInt(args[2]), keep = Integer.parseInt(args[3]);
    double spt = Double.parseDouble(args[4]);
    int mao = Integer.parseInt(args[5]);
    mbd = args.length > 6 ? Integer.parseInt(args[6]) : -1;
    floor = args.length > 7 ? Double.parseDouble(args[7]) : -1;
    tsc = args.length > 8 ? Integer.parseInt(args[8]) : -1;
    int hits = 0;
    for (int seed = from; seed < to; seed++) {
      try (Directory dir = new ByteBuffersDirectory()) {
        build(dir, seed, spt, mao);
        String where = findRepeated(dir);
        if (where != null) {
          hits++;
          System.out.println("seed " + seed + " REPEATED " + where);
          if (hits <= keep) {
            Path p = out.resolve("seed-" + seed);
            Files.createDirectories(p);
            try (Directory fs = FSDirectory.open(p)) {
              for (String f : dir.listAll()) fs.copyFrom(dir, f, f, IOContext.DEFAULT);
            }
          }
        }
      }
    }
    System.out.println("version " + Version.LATEST + " seeds [" + from + "," + to + ") with repeated neighbours: " + hits);
  }

  static int mbd, tsc;
  static double floor;

  static void build(Directory dir, int seed, double spt, int mao) throws Exception {
    IndexWriterConfig c = new IndexWriterConfig();
    if (mbd > 0) c.setMaxBufferedDocs(mbd);
    TieredMergePolicy tmp = new TieredMergePolicy();
    if (floor > 0) tmp.setFloorSegmentMB(floor);
    if (tsc > 0) tmp.setTargetSearchConcurrency(tsc);
    tmp.setSegmentsPerTier(spt);
    tmp.setMaxMergeAtOnce(mao);
    c.setMergePolicy(tmp);
    c.setMergeScheduler(new SerialMergeScheduler());
    Random r = new Random(seed);
    try (IndexWriter w = new IndexWriter(dir, c)) {
      Document doc = new Document();
      KnnFloatVectorField vf = new KnnFloatVectorField("vector", new float[] {0}, VectorSimilarityFunction.EUCLIDEAN);
      doc.add(vf);
      for (int i = 0; i < 1000; ++i) {
        vf.setVectorValue(new float[] {i % 2 == 0 ? 0 : i % 10});
        w.addDocument(doc);
        if (r.nextInt(4) == 0) w.commit();
      }
      w.commit();
    }
  }

  static String findRepeated(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        KnnVectorsReader kvr = ((CodecReader) ctx.reader()).getVectorReader();
        if (kvr instanceof PerFieldKnnVectorsFormat.FieldsReader fr) kvr = fr.getFieldReader("vector");
        if (!(kvr instanceof HnswGraphProvider gp)) continue;
        HnswGraph g = gp.getGraph("vector");
        for (int level = 0; level < g.numLevels(); level++) {
          HnswGraph.NodesIterator it = g.getNodesOnLevel(level);
          while (it.hasNext()) {
            int node = it.nextInt();
            g.seek(level, node);
            int prev = -1;
            for (int n = g.nextNeighbor(); n != Integer.MAX_VALUE; n = g.nextNeighbor()) {
              if (n == prev) return ctx.reader() + " level=" + level + " node=" + node + " neighbour=" + n;
              prev = n;
            }
          }
        }
      }
    }
    return null;
  }
}

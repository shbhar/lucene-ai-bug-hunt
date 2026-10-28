import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Writes the fixture index with the release jars it is compiled against (9.12.3 or 10.3.2: the
 * last releases whose core can write Lucene99HnswScalarQuantizedVectorsFormat).
 *
 * <p>Segment A: three documents whose vectors are identical and constant (every component equal),
 * so the writer's quantiles are min == max. Segment B: three ordinary documents. Each document has
 * one field per (similarity, bits, compress, confidence interval) configuration.
 *
 * <p>Usage: GenUniformSqIndex OUTDIR [FIELD,FIELD,...] [cfs]
 */
public class GenUniformSqIndex {

  static final int DIM = 4;

  // name, similarity, bits, compress, confidenceInterval (null = default, 0 = dynamic)
  static Object[][] FIELDS = {
    {"euc7", VectorSimilarityFunction.EUCLIDEAN, 7, false, null},
    {"mip7", VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT, 7, false, null},
    {"dot7", VectorSimilarityFunction.DOT_PRODUCT, 7, false, null},
    {"cos7", VectorSimilarityFunction.COSINE, 7, false, null},
    {"euc4", VectorSimilarityFunction.EUCLIDEAN, 4, false, null},
    {"euc4c", VectorSimilarityFunction.EUCLIDEAN, 4, true, null},
    {"euc4dyn", VectorSimilarityFunction.EUCLIDEAN, 4, false, 0f},
    {"dot4c", VectorSimilarityFunction.DOT_PRODUCT, 4, true, null},
  };

  static float[] unit(float... v) {
    double n = 0;
    for (float x : v) n += x * x;
    float[] r = new float[v.length];
    for (int i = 0; i < v.length; i++) r[i] = (float) (v[i] / Math.sqrt(n));
    return r;
  }

  static boolean needsUnit(VectorSimilarityFunction f) {
    return f == VectorSimilarityFunction.DOT_PRODUCT || f == VectorSimilarityFunction.COSINE;
  }

  // segment A: identical constant vectors; segment B: ordinary vectors
  static final float[] A = {1f, 1f, 1f, 1f};
  static final float[][] B = {{2f, 1f, 1f, 1f}, {3f, 3f, 3f, 3f}, {-1f, 0f, 2f, 5f}};

  static float[] vec(float[] v, VectorSimilarityFunction f) {
    return needsUnit(f) ? unit(v) : v.clone();
  }

  public static void main(String[] args) throws Exception {
    Path out = Paths.get(args[0]);
    if (args.length > 1) {
      java.util.List<Object[]> keep = new java.util.ArrayList<>();
      for (String name : args[1].split(",")) {
        for (Object[] f : FIELDS) {
          if (f[0].equals(name)) keep.add(f);
        }
      }
      FIELDS = keep.toArray(new Object[0][]);
    }
    boolean cfs = args.length > 2 && args[2].equals("cfs");
    Files.createDirectories(out);
    Codec def = Codec.getDefault();
    final KnnVectorsFormat perField =
        new PerFieldKnnVectorsFormat() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            for (Object[] f : FIELDS) {
              if (f[0].equals(field)) {
                return new Lucene99HnswScalarQuantizedVectorsFormat(
                    16, 100, 1, (Integer) f[2], (Boolean) f[3], (Float) f[4], null);
              }
            }
            throw new IllegalArgumentException(field);
          }
        };
    Codec codec =
        new FilterCodec(def.getName(), def) {
          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return perField;
          }
        };
    try (Directory dir = new NIOFSDirectory(out)) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(codec).setMergePolicy(NoMergePolicy.INSTANCE);
      iwc.setUseCompoundFile(cfs);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 3; i++) {
          w.addDocument(doc("a" + i, A));
        }
        w.commit();
        for (int i = 0; i < B.length; i++) {
          w.addDocument(doc("b" + i, B[i]));
        }
        w.commit();
      }
      // record what the writing release itself returns for the exact-match query
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (Object[] f : FIELDS) {
          VectorSimilarityFunction sim = (VectorSimilarityFunction) f[1];
          float[] q = vec(A, sim);
          TopDocs td = s.search(new KnnFloatVectorQuery((String) f[0], q, 6), 6);
          StringBuilder sb = new StringBuilder();
          for (ScoreDoc sd : td.scoreDocs) {
            sb.append(
                String.format(
                    Locale.ROOT, " %s=%.9g", s.storedFields().document(sd.doc).get("id"), sd.score));
          }
          System.out.println("writer-release " + f[0] + ":" + sb);
        }
      }
    }
  }

  static Document doc(String id, float[] v) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    for (Object[] f : FIELDS) {
      VectorSimilarityFunction sim = (VectorSimilarityFunction) f[1];
      d.add(new KnnFloatVectorField((String) f[0], vec(v, sim), sim));
    }
    return d;
  }
}

package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** History check: does merging into this older quantized format change the source reader's vector? */
public class TestHistorySq99SourceUnchanged extends LuceneTestCase {
  public void testSourceReaderVectorUnchanged() throws IOException {
    float[] vector = {1f, 2f, 3f, 4f};
    MemoryIndex memoryIndex = new MemoryIndex();
    memoryIndex.addField(new KnnFloatVectorField("v", vector.clone(), VectorSimilarityFunction.EUCLIDEAN), null);
    LeafReader source = (LeafReader) memoryIndex.createSearcher().getIndexReader().leaves().get(0).reader();
    try (Directory dir = newDirectory();
        IndexWriter writer =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene99HnswScalarQuantizedVectorsFormat())))) {
      writer.addIndexes(SlowCodecReaderWrapper.wrap(source));
      writer.commit();
    }
    assertArrayEquals(vector, source.getFloatVectorValues("v").vectorValue(0), 0f);
  }
}

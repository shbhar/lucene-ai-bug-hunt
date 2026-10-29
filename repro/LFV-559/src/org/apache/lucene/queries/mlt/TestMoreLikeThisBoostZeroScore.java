package org.apache.lucene.queries.mlt;

import java.io.StringReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMoreLikeThisBoostZeroScore extends LuceneTestCase {

  public void testBoostWithZeroIdfTerm() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String text : new String[] {"common rare", "common"}) {
        Document doc = new Document();
        doc.add(new TextField("text", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        MoreLikeThis mlt =
            new MoreLikeThis(
                reader,
                new ClassicSimilarity() {
                  @Override
                  public float idf(long docFreq, long docCount) {
                    return (float) Math.log((double) docCount / docFreq); // 0 for "common"
                  }
                });
        mlt.setAnalyzer(new StandardAnalyzer());
        mlt.setFieldNames(new String[] {"text"});
        mlt.setMinTermFreq(1);
        mlt.setMinDocFreq(1);
        mlt.setBoost(true);
        try {
          mlt.like("text", new StringReader("common rare"));
        } catch (IllegalArgumentException e) {
          throw new AssertionError("expected a query with finite boosts, got " + e, e);
        }
      }
    }
  }
}

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SortingCodecReader;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestSortingPostingsEnumReusedPayload extends LuceneTestCase {

  public void testReusedEnumHasNoPayloadOnFieldWithoutPositions() throws Exception {
    FieldType docsAndFreqs = new FieldType(StringField.TYPE_NOT_STORED);
    docsAndFreqs.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 2; i++) {
        Token token = new Token("a", 0, 1);
        token.setPayload(new BytesRef("xyz"));
        Document doc = new Document();
        doc.add(new NumericDocValuesField("id", i));
        doc.add(new TextField("p", new CannedTokenStream(token)));
        doc.add(new Field("q", "b", docsAndFreqs));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        CodecReader sorted =
            SortingCodecReader.wrap(
                (CodecReader) getOnlyLeafReader(r),
                new Sort(new SortField("id", SortField.Type.LONG, true)));
        TermsEnum te = sorted.terms("p").iterator();
        te.seekExact(new BytesRef("a"));
        PostingsEnum pe = te.postings(null, PostingsEnum.ALL);
        pe.nextDoc();
        pe.nextPosition();
        te = sorted.terms("q").iterator();
        te.seekExact(new BytesRef("b"));
        pe = te.postings(pe, PostingsEnum.ALL);
        pe.nextDoc();
        pe.nextPosition();
        // expected null (q indexes no payloads), actual "xyz" (the payload last read from p)
        assertNull(pe.getPayload());
      }
    }
  }
}

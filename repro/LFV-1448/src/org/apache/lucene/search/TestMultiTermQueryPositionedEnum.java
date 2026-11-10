package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.AttributeSource;

public class TestMultiTermQueryPositionedEnum extends LuceneTestCase {

  public void testEnumPositionedAsDocumented() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String value : new String[] {"banana", "blueberry"}) {
        Document doc = new Document();
        doc.add(new StringField("f", value, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query q =
            new PrefixQuery(new Term("f", "b")) {
              @Override
              protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts)
                  throws IOException {
                TermsEnum te = super.getTermsEnum(terms, atts);
                te.next();
                return te;
              }
            };
        // javadoc: "The TermsEnum must already be positioned to the first matching term"; expected 2 hits, actual 1
        assertEquals(2, new IndexSearcher(reader).count(q));
      }
    }
  }
}

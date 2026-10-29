package org.apache.lucene.search.join;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParentsChildrenBlockJoinExplain extends LuceneTestCase {

  public void testExplainOfBooleanQueryWithJoinClause() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document child = new Document();
      child.add(new StringField("color", "red", Field.Store.NO));
      Document parent = new Document();
      parent.add(new StringField("type", "parent", Field.Store.NO));
      w.addDocuments(List.of(child, parent));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query parents = new TermQuery(new Term("type", "parent"));
        Query join =
            new ParentsChildrenBlockJoinQuery(
                new QueryBitSetProducer(parents), parents, new TermQuery(new Term("color", "red")));
        Query bq =
            new BooleanQuery.Builder()
                .add(join, Occur.SHOULD)
                .add(new TermQuery(new Term("color", "blue")), Occur.SHOULD)
                .build();
        try {
          assertTrue(new IndexSearcher(reader).explain(bq, 0).isMatch());
        } catch (IllegalStateException e) {
          // expected: a match explanation for child doc 0; actual: the per-leaf guard throws
          throw new AssertionError("expected a match explanation for doc 0, got " + e, e);
        }
      }
    }
  }
}

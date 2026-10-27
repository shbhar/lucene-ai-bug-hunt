import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** FuzzyQuery on the empty term. The edit distance from "" to a term is the term's length. */
public class EmptyFuzzyTerm {
  public static void main(String[] args) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String t : new String[] {"", "a", "b", "ab", "abc", "x"}) {
          Document doc = new Document();
          doc.add(new StringField("f", t, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        String[][] queries = {{"a", "1"}, {"", "1"}, {"", "2"}};
        for (String[] q : queries) {
          FuzzyQuery query = new FuzzyQuery(new Term("f", q[0]), Integer.parseInt(q[1]));
          TreeSet<String> hits = new TreeSet<>();
          for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
            hits.add('"' + searcher.storedFields().document(sd.doc).get("f") + '"');
          }
          System.out.println("\"" + q[0] + "\"~" + q[1] + " -> " + hits.size() + " hits " + hits);
        }
      }
    }
  }
}

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.AutomatonProvider;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/** The same complete DFA with a sink, supplied as a named automaton to RegexpQuery. */
public class ProviderProbe {
  public static void main(String[] args) throws Exception {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int sink = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, sink, Character.MIN_CODE_POINT, 'a' - 1);
    a.addTransition(s0, s0, 'a', Character.MAX_CODE_POINT);
    a.addTransition(sink, sink, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    AutomatonProvider provider = name -> "lower".equals(name) ? a : null;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {"0", "b", "zz"}) {
          Document doc = new Document();
          doc.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        RegexpQuery q =
            new RegexpQuery(new Term("f", "<lower>"), RegExp.ALL, 0, provider,
                MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE);
        System.out.println("RegexpQuery <lower> via AutomatonProvider: hits="
            + new IndexSearcher(r).count(q) + " (expected 2: b, zz)  compiled type="
            + q.getCompiled().type + "  run(\"0\")=" + Operations.run(a, "0"));
      }
    }
  }
}

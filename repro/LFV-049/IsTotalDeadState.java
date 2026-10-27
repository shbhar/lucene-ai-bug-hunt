import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;

/** Operations.isTotal says "accepts everything" for a DFA that rejects "0". Public API only. */
public class IsTotalDeadState {
  public static void main(String[] args) throws Exception {
    // A complete DFA that accepts exactly the strings whose characters are all >= 'a'.
    // State 0 (accepting) loops on ['a' .. MAX]; a character below 'a' goes to state 1,
    // a sink: not accepting, loops on every character (a "dead" state).
    Automaton a = new Automaton();
    int s0 = a.createState();
    int sink = a.createState();
    a.setAccept(s0, true);
    a.addTransition(s0, sink, Character.MIN_CODE_POINT, 'a' - 1);
    a.addTransition(s0, s0, 'a', Character.MAX_CODE_POINT);
    a.addTransition(sink, sink, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    Automaton noSink = Operations.removeDeadStates(a); // the same language, without the sink

    System.out.println("deterministic=" + a.isDeterministic()
        + "  hasDeadStates=" + Operations.hasDeadStates(a));
    System.out.println("run(\"b\")=" + Operations.run(a, "b")
        + "  run(\"zz\")=" + Operations.run(a, "zz") + "  run(\"0\")=" + Operations.run(a, "0"));
    System.out.println("isTotal(a)=" + Operations.isTotal(a)
        + "  isTotal(removeDeadStates(a))=" + Operations.isTotal(noSink));

    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {"0", "b", "zz"}) {
          Document doc = new Document();
          doc.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(r);
        AutomatonQuery q = new AutomatonQuery(new Term("f", ""), a);
        AutomatonQuery qNoSink = new AutomatonQuery(new Term("f", ""), noSink);
        System.out.println("AutomatonQuery over the terms 0, b, zz: hits=" + searcher.count(q)
            + " (compiled type " + new CompiledAutomaton(a, false, true).type + ");  without the sink: hits="
            + searcher.count(qNoSink) + " (" + new CompiledAutomaton(noSink, false, true).type + ")");
      }
    }
  }
}

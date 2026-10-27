import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;

/** Asserts the getSingleton javadoc contract on a DFA with a dead state. Expected to FAIL on main. */
public class TestGetSingletonContract extends LuceneTestCase {
  public void testDfaWithDeadStateAcceptingExactlyFo() {
    // 0 -f-> 1 -o-> 2(accept) -z-> 3(dead: non-accepting, no transitions); L = {"fo"}
    Automaton a = new Automaton();
    int s0 = a.createState(), s1 = a.createState(), s2 = a.createState(), s3 = a.createState();
    a.addTransition(s0, s1, 'f');
    a.addTransition(s1, s2, 'o');
    a.setAccept(s2, true);
    a.addTransition(s2, s3, 'z');
    a.finishState();
    assertTrue(a.isDeterministic()); // the only documented precondition of getSingleton
    IntsRef r = Operations.getSingleton(a);
    assertNotNull("javadoc: 'If this automaton accepts a single input, return it.'", r);
    assertEquals("fo", new String(r.ints, r.offset, r.length));
  }
}

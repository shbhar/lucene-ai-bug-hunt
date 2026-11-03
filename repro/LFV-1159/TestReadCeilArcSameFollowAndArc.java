package org.apache.lucene.util.fst;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;

public class TestReadCeilArcSameFollowAndArc extends LuceneTestCase {

  public void testSameFollowAndArc() throws Exception {
    FSTCompiler<Object> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, NoOutputs.getSingleton()).build();
    compiler.add(Util.toIntsRef(new BytesRef("ab"), new IntsRefBuilder()), NoOutputs.getSingleton().getNoOutput());
    FST<Object> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    FST.Arc<Object> arc = fst.getFirstArc(new FST.Arc<>());
    // expected: the root's arc 'a' (as with two distinct Arc objects); actual: the child's arc 'b'
    assertEquals('a', Util.readCeilArc('a', fst, arc, arc, fst.getBytesReader()).label());
  }
}

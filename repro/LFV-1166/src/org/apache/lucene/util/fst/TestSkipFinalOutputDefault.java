/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.util.fst;

import java.io.IOException;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;

public class TestSkipFinalOutputDefault extends LuceneTestCase {

  public void testLookupPastArcWithFinalOutput() throws IOException {
    PositiveIntOutputs p = PositiveIntOutputs.getSingleton();
    Outputs<Long> outputs =
        new Outputs<>() {
          @Override
          public void writeFinalOutput(Long output, DataOutput out) throws IOException {
            out.writeLong(output);
          }

          @Override
          public Long readFinalOutput(DataInput in) throws IOException {
            return in.readLong();
          }

          @Override
          public Long common(Long a, Long b) {
            return p.common(a, b);
          }

          @Override
          public Long subtract(Long a, Long b) {
            return p.subtract(a, b);
          }

          @Override
          public Long add(Long a, Long b) {
            return p.add(a, b);
          }

          @Override
          public void write(Long output, DataOutput out) throws IOException {
            p.write(output, out);
          }

          @Override
          public Long read(DataInput in) throws IOException {
            return p.read(in);
          }

          @Override
          public Long getNoOutput() {
            return p.getNoOutput();
          }

          @Override
          public String outputToString(Long output) {
            return p.outputToString(output);
          }

          @Override
          public long ramBytesUsed(Long output) {
            return p.ramBytesUsed(output);
          }
        };
    FSTCompiler<Long> compiler = new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, outputs).build();
    IntsRefBuilder scratch = new IntsRefBuilder();
    compiler.add(Util.toIntsRef(new BytesRef("a"), scratch), 5L);
    compiler.add(Util.toIntsRef(new BytesRef("ab"), scratch), 3L);
    compiler.add(Util.toIntsRef(new BytesRef("b"), scratch), 7L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    // expected 7 for key "b"; actual null: skipFinalOutput's default uses skipOutput, not readFinalOutput
    assertEquals(Long.valueOf(7), Util.get(fst, new BytesRef("b")));
  }
}

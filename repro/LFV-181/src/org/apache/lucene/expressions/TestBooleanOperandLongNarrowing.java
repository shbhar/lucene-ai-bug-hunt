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
package org.apache.lucene.expressions;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * The operand of !, &&, || and ?: is narrowed to a 32-bit int before it is tested. A long-valued
 * operand (the result of &, |, ^, ~, <<, >>, >>> or a hex/octal literal) is narrowed by truncation,
 * so every non-zero value whose low 32 bits are zero is treated as false, although the same value
 * is true when it comes from a decimal literal, a variable or arithmetic.
 */
public class TestBooleanOperandLongNarrowing extends LuceneTestCaseJupiter {

  private static double eval(String source, double... vars) throws Exception {
    Expression expr = JavascriptCompiler.compile(source);
    DoubleValues[] values = new DoubleValues[expr.variables.length];
    for (int i = 0; i < values.length; i++) {
      final double v = vars[i];
      values[i] =
          new DoubleValues() {
            @Override
            public double doubleValue() {
              return v;
            }

            @Override
            public boolean advanceExact(int doc) {
              return true;
            }
          };
    }
    return expr.evaluate(values);
  }

  /** A bit test of bit 32 in an indexed long field never fires. */
  @Test
  public void testBitTestOfHighBitInIndexedField() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document hasBit32 = new Document();
        hasBit32.add(new NumericDocValuesField("flags", (1L << 32) | 1L));
        w.addDocument(hasBit32);
        Document noBit32 = new Document();
        noBit32.add(new NumericDocValuesField("flags", 1L));
        w.addDocument(noBit32);
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("flags", DoubleValuesSource.fromLongField("flags"));
        DoubleValuesSource masked =
            JavascriptCompiler.compile("flags & 0x100000000").getDoubleValuesSource(bindings);
        DoubleValuesSource test =
            JavascriptCompiler.compile("(flags & 0x100000000) ? 10 : 1")
                .getDoubleValuesSource(bindings);
        LeafReaderContext leaf = reader.leaves().get(0);
        DoubleValues maskedValues = masked.getValues(leaf, null);
        DoubleValues testValues = test.getValues(leaf, null);
        // doc 0 has bit 32 set: the masked value is 2^32, i.e. non-zero ...
        assertTrue(maskedValues.advanceExact(0));
        assertEquals(4294967296.0, maskedValues.doubleValue(), 0.0);
        // ... so the conditional must take its 'true' branch
        assertTrue(testValues.advanceExact(0));
        assertEquals("(flags & 0x100000000) ? 10 : 1 on doc 0", 10.0, testValues.doubleValue(), 0.0);
        assertTrue(testValues.advanceExact(1));
        assertEquals("(flags & 0x100000000) ? 10 : 1 on doc 1", 1.0, testValues.doubleValue(), 0.0);
      }
    }
  }

  /** The truth value of 2^32 depends on how the operand is written. */
  @Test
  public void testTruthDependsOnlyOnValue() throws Exception {
    double flags = 0x1p32 + 1; // bits 0 and 32
    assertEquals(4294967296.0, eval("flags & 0x100000000", flags), 0.0);
    assertEquals(1.0, eval("(flags & 0x100000000) != 0", flags), 0.0);
    assertEquals(1.0, eval("v ? 1 : 0", 0x1p32), 0.0); // 2^32 from a variable is true
    assertEquals(1.0, eval("4294967296 ? 1 : 0"), 0.0); // 2^32 as a decimal literal is true

    assertEquals("(flags & 0x100000000) ? 1 : 0", 1.0, eval("(flags & 0x100000000) ? 1 : 0", flags), 0.0);
    assertEquals("!(flags & 0x100000000)", 0.0, eval("!(flags & 0x100000000)", flags), 0.0);
    assertEquals("(flags & 0x100000000) && 1", 1.0, eval("(flags & 0x100000000) && 1", flags), 0.0);
    assertEquals("(flags & 0x100000000) || 0", 1.0, eval("(flags & 0x100000000) || 0", flags), 0.0);
    assertEquals("(1 << 32) ? 1 : 0", 1.0, eval("(1 << 32) ? 1 : 0"), 0.0);
    assertEquals("0x100000000 ? 1 : 0", 1.0, eval("0x100000000 ? 1 : 0"), 0.0);
  }
}

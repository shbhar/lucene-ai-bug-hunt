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
package org.apache.lucene.index;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * TermsEnum#seekExact(BytesRef, TermState) positions the enum on the term; a following next()
 * must return the term after it. On a TermsEnum whose first call is seekExact(term, state) (the
 * pattern TermQuery/TermStates use, with a state taken from another enum on the same field and
 * segment), next() instead restarts at the first term of the field.
 *
 * <p>Uses only public API (IndexWriter, default codec, Terms/TermsEnum).
 */
public class TestNextAfterSeekExactTermState extends LuceneTestCase {

  public void testNextAfterSeekExactWithTermStateOnNewEnum() throws Exception {
    assertNextAfterTermStateSeek(TestUtil.getDefaultCodec());
  }

  /** Control: another codec (terms enum without its own TermState seek) gives the successor. */
  public void testControlSimpleTextCodec() throws Exception {
    assertNextAfterTermStateSeek(new SimpleTextCodec());
  }

  private void assertNextAfterTermStateSeek(Codec codec) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setCodec(codec);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String s : new String[] {"apple", "banana", "cherry"}) {
          Document doc = new Document();
          doc.add(new StringField("f", s, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Terms terms = reader.leaves().get(0).reader().terms("f");

        // TermState of "banana", taken from one TermsEnum ...
        TermsEnum source = terms.iterator();
        assertTrue(source.seekExact(new BytesRef("banana")));
        TermState state = source.termState();

        // ... used to position another TermsEnum of the same field and segment
        TermsEnum te = terms.iterator();
        te.seekExact(new BytesRef("banana"), state);
        assertEquals(new BytesRef("banana"), te.term());
        assertEquals(1, te.docFreq());

        // the enum is positioned on "banana", so next() must return "cherry"
        assertEquals(new BytesRef("cherry"), te.next());
        assertNull(te.next());
      }
    }
  }

  /** Control: the same sequence after the enum was positioned once by a regular seek passes. */
  public void testControlEnumUsedBeforeTermStateSeek() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String s : new String[] {"apple", "banana", "cherry"}) {
          Document doc = new Document();
          doc.add(new StringField("f", s, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Terms terms = reader.leaves().get(0).reader().terms("f");
        TermsEnum source = terms.iterator();
        assertTrue(source.seekExact(new BytesRef("banana")));
        TermState state = source.termState();

        TermsEnum te = terms.iterator();
        assertTrue(te.seekExact(new BytesRef("cherry"))); // any earlier positioning
        te.seekExact(new BytesRef("banana"), state);
        assertEquals(new BytesRef("cherry"), te.next());
      }
    }
  }
}

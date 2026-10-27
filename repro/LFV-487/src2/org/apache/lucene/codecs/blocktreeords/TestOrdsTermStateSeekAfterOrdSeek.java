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
package org.apache.lucene.codecs.blocktreeords;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * BlockTreeOrds terms enum: seekExact(term, termState) on the term the enum was just positioned
 * on by seekExact(ord) marks the enum as positioned without rebuilding its frame stack, so the
 * next seek or next() works on stale frames.
 *
 * <p>Index: 26 terms a00..a24 and b in one segment, default BlockTreeOrds block sizes (25, 48),
 * so the 25 a-terms form a sub-block. Only public API is used.
 */
public class TestOrdsTermStateSeekAfterOrdSeek extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private final List<String> words = new ArrayList<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    for (int i = 0; i < 25; i++) {
      words.add(String.format(Locale.ROOT, "a%02d", i));
    }
    words.add("b");
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new BlockTreeOrdsPostingsFormat()));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String word : words) {
        Document doc = new Document();
        doc.add(new StringField("f", word, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private TermsEnum positionByOrdThenByTermState(long ord) throws IOException {
    Terms terms = getOnlyLeafReader(reader).terms("f");
    TermsEnum te = terms.iterator();
    te.seekExact(ord); // supported: BlockTreeOrds implements ord()/seekExact(long)
    assertEquals(words.get((int) ord), te.term().utf8ToString());
    // TermState obtained from this same TermsEnum instance, as the TermsEnum javadoc requires
    TermState state = te.termState();
    te.seekExact(BytesRef.deepCopyOf(te.term()), state);
    assertEquals(words.get((int) ord), te.term().utf8ToString());
    assertEquals(ord, te.ord());
    return te;
  }

  /** seekExact of an existing term must return true. */
  public void testSeekExactExistingTerm() throws IOException {
    TermsEnum te = positionByOrdThenByTermState(0);
    assertTrue("term 'b' exists but seekExact returned false", te.seekExact(new BytesRef("b")));
    assertEquals("b", te.term().utf8ToString());
  }

  /** seekCeil of an existing term must return FOUND. */
  public void testSeekCeilExistingTerm() throws IOException {
    TermsEnum te = positionByOrdThenByTermState(0);
    assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(new BytesRef("b")));
    assertEquals("b", te.term().utf8ToString());
  }

  /** next() after the last term of the sub-block must return the following term. */
  public void testNextAfterLastTermOfSubBlock() throws IOException {
    TermsEnum te = positionByOrdThenByTermState(24);
    BytesRef next = te.next();
    assertNotNull("next() returned null but 'b' follows 'a24'", next);
    assertEquals("b", next.utf8ToString());
  }

  /** Control: the same seeks without the intermediate seekExact(term, state) all work. */
  public void testControlWithoutTermStateSeek() throws IOException {
    Terms terms = getOnlyLeafReader(reader).terms("f");
    TermsEnum te = terms.iterator();
    te.seekExact(0L);
    assertTrue(te.seekExact(new BytesRef("b")));
    te = terms.iterator();
    te.seekExact(0L);
    assertEquals(TermsEnum.SeekStatus.FOUND, te.seekCeil(new BytesRef("b")));
    te = terms.iterator();
    te.seekExact(24L);
    assertEquals("b", te.next().utf8ToString());
  }
}

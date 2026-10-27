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
package org.apache.lucene.queryparser.ext;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Extensions#buildExtensionField and #splitExtensionField document that "the latter inverts the
 * former", but the default split cuts at the FIRST delimiter while build appends the key after the
 * field. With the '_' delimiter of the ExtendableQueryParser javadoc example and a field named
 * first_name, buildExtensionField("customExt", "first_name") is "first_name_customExt", which
 * splits into field "first" and key "name_customExt": the extension is never called and the
 * parser builds a plain query on the non-existent field first_name_customExt.
 */
public class TestExtensionFieldWithDelimiter extends LuceneTestCase {

  private static final class Recorder extends ParserExtension {
    String field;
    String raw;

    @Override
    public Query parse(ExtensionQuery query) throws ParseException {
      field = query.field();
      raw = query.rawQueryString();
      return new TermQuery(new Term(query.field(), query.rawQueryString()));
    }
  }

  /** Control: a field without the delimiter round-trips. */
  public void testFieldWithoutDelimiter() throws Exception {
    Extensions ext = new Extensions('_');
    assertEquals(new Extensions.Pair<>("title", "customExt"),
        ext.splitExtensionField("dflt", ext.buildExtensionField("customExt", "title")));
  }

  /** The documented inversion, called directly. */
  public void testSplitInvertsBuild() throws Exception {
    Extensions ext = new Extensions('_');
    assertEquals(new Extensions.Pair<>("first_name", "customExt"),
        ext.splitExtensionField("dflt", ext.buildExtensionField("customExt", "first_name")));
  }

  /** End to end through ExtendableQueryParser. */
  public void testParserDispatch() throws Exception {
    Extensions ext = new Extensions('_');
    Recorder rec = new Recorder();
    ext.add("customExt", rec);
    ExtendableQueryParser qp = new ExtendableQueryParser("dflt", new MockAnalyzer(random()), ext);
    Query q = qp.parse(ext.buildExtensionField("customExt", "first_name") + ":foo");
    assertEquals("extension not called; parsed query: " + q, "first_name", rec.field);
    assertEquals("foo", rec.raw);
  }
}

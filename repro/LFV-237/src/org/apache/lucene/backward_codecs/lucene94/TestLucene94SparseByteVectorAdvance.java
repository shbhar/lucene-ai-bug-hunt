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
package org.apache.lucene.backward_codecs.lucene94;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene94SparseByteVectorAdvance extends LuceneTestCase {

  private static final String INDEX_ZIP_BASE64 =
      "UEsDBBQAAAAIAAAAXV3C/Pf5lQAAACkBAAAGAAAAXzAuY2Zls7+eIy7uU5qcmpdqaeCcn1uQX5qX4ppXUpSZWswA" +
      "BMs2aPRrLnmkzvtOwPaqceRFBnaFeKhyE4+84vKw1OSS/KJit/yi3MSSeAO9stQKAwYIeA6lWfTSUnIlGCGcuVBB" +
      "QqYk74BqKEIypcKACcJxINKU3AKohiNIppRYMEM4AQjBvNwOqOAaqOABjckvGGDWRDLUAwBQSwMEFAAAAAgAAABd" +
      "XSQ264xjAQAARAQAAAYAAABfMC5jZnOzv54jLuJTmpyal2pp4JyfW5BfmpfikliSyAAEyzZo9GsueaTO+07A9qpx" +
      "5EWgkD1QvVwORL2JR15xeVhqckl+UbFbflFuYolnXkpqBVAVI7pOaR9cWuINGAYBOKAx+QWMfdc+/R7Yn+KwcHHL" +
      "TM1JKQZ7zjcVHDQYHmRoaGBlAkpwATGIxuIrBzgLJmkGF1FwxJCEKWdkZEJ23f5ZvJcZIPEgizMeoBFIYjQwMoHt" +
      "Y/hfz8Dw/z+ypXuyZpZBWCB7xbCEi2cKKN4xUwyyKdzai+PxuxtX6BKbfEKYglvAvmAE4jCoIA9cmjMJyhBgxIwe" +
      "BBCAGgDC//+jhkS1Ms9RWDjIwMIhGOie1BRIaLglFuMMfkjyAJuMbKbVCvaDIPOEYL4Em+SZl5ZfjDVMGRnBkfEf" +
      "CpjkA1KLwFq88/JQAkevuDQtLbOC0QC3ijQwJYkzfEGJGdmtSlvPzEDmJ836HAIAUEsDBBQAAAAIAAAAXV3rg84B" +
      "+AAAADoBAAAFAAAAXzAuc2mzv54jLuxTmpyal2ppEJyanpuaV+KZl5bPAATLNmj0ay55pM77TsD2qnHkRQZOoCAL" +
      "EDMBMSMyByzAwZcDNkevLLWoODM/j9VSz0TPiK04v7QoOZU1Lae0OIOzJDM3tbgkMbeA19Dc0tjI2MgADESyEssS" +
      "9YpK80DyMP08RqZ6BnrG2pa6PiHB3GAVZal5KflF7KF52Xn55XlM+cWsPpl5pRXs+cV6iUXJGeyJINLMhAvIhxoC" +
      "U8vMFm+gl5yWygqkijMhnGJGebjPS/KLUlPcMlNzUord8otyE0v0cvNTUrmcXIND4oMDXF1dGA5oTH7BAAXiy9Xi" +
      "AFBLAwQUAAAACAAAAF1d1R0COl0AAACaAAAACgAAAHNlZ21lbnRzXzGzv54jzlGcmp6bmldSzMDAwLVsg0a/5pJH" +
      "6rzvBGyvGkdeYTTkZGHiZIAAFkYgwQgUYIo3QFN4kcOnNDk1L9XS5D8UgDT8RwMgMUY0nZchZh/QmPwCag1Dy81i" +
      "ZgBQSwECFAMUAAAACAAAAF1dwvz3+ZUAAAApAQAABgAAAAAAAAAAAAAAgAEAAAAAXzAuY2ZlUEsBAhQDFAAAAAgA" +
      "AABdXSQ264xjAQAARAQAAAYAAAAAAAAAAAAAAIABuQAAAF8wLmNmc1BLAQIUAxQAAAAIAAAAXV3rg84B+AAAADoB" +
      "AAAFAAAAAAAAAAAAAACAAUACAABfMC5zaVBLAQIUAxQAAAAIAAAAXV3VHQI6XQAAAJoAAAAKAAAAAAAAAAAAAACA" +
      "AVsDAABzZWdtZW50c18xUEsFBgAAAAAEAAQA0wAAAOADAAAAAA==";

  public void testAdvanceThenIndex() throws Exception {
    Path path = createTempDir();
    TestUtil.unzip(new ByteArrayInputStream(Base64.getDecoder().decode(INDEX_ZIP_BASE64)), path);
    try (Directory dir = FSDirectory.open(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      ByteVectorValues values = reader.leaves().get(0).reader().getByteVectorValues("v");
      KnnVectorValues.DocIndexIterator it = values.iterator();
      assertEquals(1, it.advance(1));
      // index written by 9.4.2, doc 0 has no vector: expected doc 1's vector [1, 2], got [0, 0]
      assertEquals("[1, 2]", Arrays.toString(values.vectorValue(it.index())));
    }
  }
}

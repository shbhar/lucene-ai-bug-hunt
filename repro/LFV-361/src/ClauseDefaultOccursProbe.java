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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.queryparser.xml.CoreParser;

/** Version probe (no test framework): exit 0 if a Clause without "occurs" is accepted. */
public class ClauseDefaultOccursProbe {
  public static void main(String[] args) {
    String xml = "<BooleanQuery fieldName='f'><Clause><TermQuery>a</TermQuery></Clause>"
        + "<Clause occurs='must'><TermQuery>b</TermQuery></Clause></BooleanQuery>";
    try {
      Object q = new CoreParser("contents", new WhitespaceAnalyzer())
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      System.out.println("parsed -> " + q);
      System.exit(0);
    } catch (Exception e) {
      System.out.println("rejected -> " + e);
      System.exit(1);
    }
  }
}

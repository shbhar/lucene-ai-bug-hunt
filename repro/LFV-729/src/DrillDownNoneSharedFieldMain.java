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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** Version-independent main() variant of TestDrillDownNoneSharedField (exit 1 = bug present). */
public class DrillDownNoneSharedFieldMain {
  public static void main(String[] args) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setDrillDownTermsIndexing("rating", FacetsConfig.DrillDownTermsIndexing.NONE);
    Directory dir = new ByteBuffersDirectory();
    Directory taxoDir = new ByteBuffersDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    Document doc = new Document();
    doc.add(new FacetField("rating", "5"));
    writer.addDocument(config.build(taxoWriter, doc));
    doc = new Document();
    doc.add(new FacetField("author", "Bob"));
    int status = 0;
    try {
      writer.addDocument(config.build(taxoWriter, doc));
      System.out.println("OK: both documents accepted");
    } catch (IllegalArgumentException e) {
      System.out.println("BUG: second document rejected: " + e);
      status = 1;
    }
    writer.close();
    taxoWriter.close();
    System.exit(status);
  }
}

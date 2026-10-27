package org.apache.lucene.queries.function;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queries.function.valuesource.ByteKnnVectorFieldSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/** 9.x-API variant: ByteKnnVectorFieldSource on the same Lucene 9.4.2 index, run on 9.12.3. */
public class TestSparseByteVectors94Advance9x extends LuceneTestCase {
  private static final String INDEX_ZIP_BASE64 =
      "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" +
      "AAAAAAAAAAAApIEOBAAAXzBfTHVjZW5lOTBfMC50aW1QSwECFAMUAAAACAAAAFpdTNSqTEQAAABIAAAAEQAAAAAAAA" +
      "AAAAAApIGRBAAAXzBfTHVjZW5lOTBfMC50aXBQSwECFAMUAAAACAAAAFpdGgDkhYEAAAC4AAAAEQAAAAAAAAAAAAAA" +
      "pIEEBQAAXzBfTHVjZW5lOTBfMC50bWRQSwECFAMUAAAACAAAAFpduq//NF4AAAB6AAAAIgAAAAAAAAAAAAAApIG0BQ" +
      "AAXzBfTHVjZW5lOTRIbnN3VmVjdG9yc0Zvcm1hdF8wLnZlY1BLAQIUAxQAAAAIAAAAWl1dniqLbgAAAMQAAAAiAAAA" +
      "AAAAAAAAAACkgVIGAABfMF9MdWNlbmU5NEhuc3dWZWN0b3JzRm9ybWF0XzAudmVtUEsBAhQDFAAAAAgAAABaXUxTYt" +
      "NYAAAA7wEAACIAAAAAAAAAAAAAAKSBAAcAAF8wX0x1Y2VuZTk0SG5zd1ZlY3RvcnNGb3JtYXRfMC52ZXhQSwECFAMU" +
      "AAAACAAAAFpdi86RqV0AAACaAAAACgAAAAAAAAAAAAAApIGYBwAAc2VnbWVudHNfMVBLBQYAAAAADQANACcDAAAdCA" +
      "AAAAA=";

  public void testByteKnnVectorFieldSourceOnLucene94SparseField() throws IOException {
    Path path = createTempDir("sparse-byte-94-vs");
    TestUtil.unzip(new ByteArrayInputStream(Base64.getDecoder().decode(INDEX_ZIP_BASE64)), path);
    try (Directory dir = newFSDirectory(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReaderContext ctx = reader.leaves().get(0);
      StoredFields stored = ctx.reader().storedFields();
      FunctionValues fv = new ByteKnnVectorFieldSource("v").getValues(new HashMap<>(), ctx);
      StringBuilder mismatches = new StringBuilder();
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc += 2) {
        byte[] want = BytesRef.deepCopyOf(stored.document(doc).getBinaryValue("expected")).bytes;
        byte[] got = fv.byteVectorVal(doc);
        if (Arrays.equals(want, got) == false) {
          mismatches.append(" doc ").append(doc).append(": expected ").append(Arrays.toString(want))
              .append(" got ").append(Arrays.toString(got));
        }
      }
      assertEquals("", mismatches.toString());
    }
  }
}

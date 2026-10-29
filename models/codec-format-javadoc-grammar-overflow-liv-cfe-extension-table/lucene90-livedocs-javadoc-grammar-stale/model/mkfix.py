import sys, pathlib
b = pathlib.Path(sys.argv[1]) / "lucene/core/src/java/org/apache/lucene/codecs"
def sub(rel, old, new):
    p = b / rel; s = p.read_text()
    assert s.count(old) == 1, (rel, old[:60], s.count(old))
    p.write_text(s.replace(old, new))
# 1. .liv
sub("lucene90/Lucene90LiveDocsFormat.java",
""" * <p>Deletions (.liv) --&gt; IndexHeader,Generation,Bits
 *
 * <ul>
 *   <li>SegmentHeader --&gt; {@link CodecUtil#writeIndexHeader IndexHeader}
 *   <li>Bits --&gt; &lt;{@link DataOutput#writeLong Int64}&gt; <sup>LongCount</sup>
 * </ul>
""",
""" * <p>Deletions (.liv) --&gt; IndexHeader,Bits,Footer
 *
 * <ul>
 *   <li>IndexHeader --&gt; {@link CodecUtil#writeIndexHeader IndexHeader}
 *   <li>Bits --&gt; &lt;{@link DataOutput#writeLong Int64}&gt; <sup>LongCount</sup>
 *   <li>Footer --&gt; {@link CodecUtil#writeFooter CodecFooter}
 * </ul>
 *
 * <p>Notes:
 *
 * <ul>
 *   <li>The suffix of the IndexHeader is the deletes generation in base 36, the same generation as
 *       in the file name.
 *   <li>LongCount is ceil(maxDoc / 64). Bit (docID % 64) of Int64 number (docID / 64) is set if the
 *       document is live and clear if it is deleted. The bits from maxDoc on are clear.
 * </ul>
""")
# 2. .cfe
sub("lucene90/Lucene90CompoundFormat.java",
""" *   <li>Compound Entry Table (.cfe) --&gt; Header, FileCount, &lt;FileName, DataOffset,
 *       DataLength&gt; <sup>FileCount</sup>
""",
""" *   <li>Compound Entry Table (.cfe) --&gt; Header, FileCount, &lt;FileName, DataOffset,
 *       DataLength&gt; <sup>FileCount</sup>, Footer
""")
sub("lucene90/Lucene90CompoundFormat.java",
""" *   <li>DataOffset,DataLength,Checksum --&gt; {@link DataOutput#writeLong UInt64}
 *   <li>FileName --&gt; {@link DataOutput#writeString String}
""",
""" *   <li>DataOffset,DataLength --&gt; {@link DataOutput#writeLong UInt64}
 *   <li>FileName --&gt; {@link DataOutput#writeString String}, the file name without the segment
 *       name (for example <code>.fnm</code> for <code>_0.fnm</code>)
""")
sub("lucene90/Lucene90CompoundFormat.java",
""" *       files length, and a String with that file's name. The start of file's data section is
 *       aligned to 64 bytes to not introduce additional unaligned accesses with mmap.""",
""" *       files length, and a String with that file's name without the segment name. The start
 *       of file's data section is aligned to 64 bytes to not introduce additional unaligned
 *       accesses with mmap.""")
# 3. extension table + postings list
pi = "lucene104/package-info.java"
sub(pi, """ * <td>.fdt</td>
 * <td>The stored fields for documents</td>
 * </tr>
""", """ * <td>.fdt</td>
 * <td>The stored fields for documents</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat Field Metadata}</td>
 * <td>.fdm</td>
 * <td>Metadata about the stored fields and their index</td>
 * </tr>
""")
sub(pi, """ * <td>The index into the Term Dictionary</td>
 * </tr>
""", """ * <td>The index into the Term Dictionary</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene104.Lucene104PostingsFormat Term Metadata}</td>
 * <td>.tmd</td>
 * <td>Per-field term statistics and the location of the Term Index</td>
 * </tr>
""")
sub(pi, """ * <td>Stores additional per-position metadata information such as character offsets and user payloads</td>
 * </tr>
""", """ * <td>Stores additional per-position metadata information such as character offsets and user payloads</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene104.Lucene104PostingsFormat Postings Metadata}</td>
 * <td>.psm</td>
 * <td>Small metadata about how postings are encoded</td>
 * </tr>
""")
sub(pi, """ * <td>.dvd, .dvm</td>""", """ * <td>.dvd, .dvm, .dvs, .dvp</td>""")
sub(pi, """ * <td>Stores offset into the document data file</td>
 * </tr>
""", """ * <td>Stores offset into the document data file</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90TermVectorsFormat Term Vector Metadata}</td>
 * <td>.tvm</td>
 * <td>Metadata about the term vectors and their index</td>
 * </tr>
""")
sub(pi, """ * <td>.vec, .vem, .veq, vex</td>
 * <td>Holds indexed vectors; <code>.vec</code> files contain the raw vector data,
 * <code>.vem</code> the vector metadata, <code>.veq</code> the quantized vector data, and <code>.vex</code> the
 * hnsw graph data.</td>""", """ * <td>.vec, .vemf, .vem, .vex</td>
 * <td>Holds indexed vectors; <code>.vec</code> files contain the raw vector data,
 * <code>.vemf</code> the raw vector metadata, <code>.vem</code> the hnsw graph metadata, and
 * <code>.vex</code> the hnsw graph data. The optional scalar-quantized formats add
 * <code>.veq</code> (quantized vector data) and <code>.vemq</code> (its metadata).</td>""")
pf = "lucene104/Lucene104PostingsFormat.java"
sub(pf, """ *   <li><code>.tip</code>: <a href="#Termindex">Term Index</a>
""", """ *   <li><code>.tip</code>: <a href="#Termindex">Term Index</a>
 *   <li><code>.tmd</code>: Term Metadata, see {@link Lucene103BlockTreeTermsWriter}
""")
sub(pf, """ *   <li><code>.pay</code>: <a href="#Payloads">Payloads and Offsets</a>
""", """ *   <li><code>.pay</code>: <a href="#Payloads">Payloads and Offsets</a>
 *   <li><code>.psm</code>: Postings metadata (maximum impact sizes and the end pointers of the
 *       .doc, .pos and .pay files)
""")
# 4. doc values prose
dv = "lucene90/Lucene90DocValuesFormat.java"
sub(dv, """ *   <li>Table-compressed: when the number of unique values is very small (&lt; 256), and when there""",
        """ *   <li>Table-compressed: when the number of unique values is very small (&lt;= 256), and when there""")
sub(dv, """ *   <li>Prefix-compressed Binary: values are written in chunks of 16, with the first value written
 *       completely and other values sharing prefixes. chunk addresses are written as
 *       Monotonic-compressed numerics. A reverse lookup index is written from a portion of every
 *       1024th term.""", """ *   <li>Prefix-compressed Binary: values are written in blocks of 64, with the first value written
 *       completely and other values sharing prefixes; the rest of each block is compressed with
 *       LZ4, using the first value as a dictionary. Block addresses are written as
 *       Monotonic-compressed numerics. A reverse lookup index is written from a portion of every
 *       1024th term.""")
print("ok")

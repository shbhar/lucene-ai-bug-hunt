# Formal models and scripts: taxonomy-writer-concurrent-add

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DirectoryTaxonomyWriter gives an existing category a second ordinal after an addCategory call fails with an IOException while the writer opens its near-real-time reader, because the cache stays marked complete or the reader stays stale (bug LFV-521; [repro](../../repro/LFV-521/), [bug page](../../bugs/LFV-521.html))
- DirectoryTaxonomyWriter gives every later category a wrong ordinal after IndexWriter rejects one enriched ordinal document: the rejected document keeps its docID but the writer's ordinal counter does not advance (bug LFV-520; [repro](../../repro/LFV-520/), [bug page](../../bugs/LFV-520.html))
- DirectoryTaxonomyWriter counts a new ordinal in getSize before it stores that ordinal's parent, so a concurrent getParent(getSize - 1) returns 0 (the root) instead of the real parent, or throws ArrayIndexOutOfBoundsException (bug LFV-512; [repro](../../repro/LFV-512/))

## Files

- [taxonomy-writer-addcategory-not-failure-atomic/model/F_ex_c1000_m1_t1.cfg](taxonomy-writer-addcategory-not-failure-atomic/model/F_ex_c1000_m1_t1.cfg) (used for LFV-521)
- [taxonomy-writer-addcategory-not-failure-atomic/model/F_new_c3_m1_t1.cfg](taxonomy-writer-addcategory-not-failure-atomic/model/F_new_c3_m1_t1.cfg) (used for LFV-521)
- [taxonomy-writer-addcategory-not-failure-atomic/model/TaxoWriterCache.tla](taxonomy-writer-addcategory-not-failure-atomic/model/TaxoWriterCache.tla) (used for LFV-521)
- [taxonomy-writer-addcategory-not-failure-atomic/model/XC_new_c2_t1.cfg](taxonomy-writer-addcategory-not-failure-atomic/model/XC_new_c2_t1.cfg) (used for LFV-521)
- [taxonomy-writer-addcategory-not-failure-atomic/model/XF_new_c2_m1_t1.cfg](taxonomy-writer-addcategory-not-failure-atomic/model/XF_new_c2_m1_t1.cfg) (used for LFV-521)
- [taxonomy-writer-getsize-ahead-of-parent-array/model/G_new_c3_t2.cfg](taxonomy-writer-getsize-ahead-of-parent-array/model/G_new_c3_t2.cfg) (used for LFV-512)
- [taxonomy-writer-getsize-ahead-of-parent-array/model/TaxoWriterCache.tla](taxonomy-writer-getsize-ahead-of-parent-array/model/TaxoWriterCache.tla) (used for LFV-512)
- [taxonomy-writer-rejected-ordinal-document-shifts-ordinals/model/R_new_c3_t1.cfg](taxonomy-writer-rejected-ordinal-document-shifts-ordinals/model/R_new_c3_t1.cfg) (used for LFV-520)
- [taxonomy-writer-rejected-ordinal-document-shifts-ordinals/model/TaxoWriterCache.tla](taxonomy-writer-rejected-ordinal-document-shifts-ordinals/model/TaxoWriterCache.tla) (used for LFV-520)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

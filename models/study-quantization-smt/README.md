# Formal models and scripts: study-quantization-smt

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Two antipodal COSINE vectors make the OSQ centroid zero, so flush or merge fails and the writer closes (bug LFV-047; [repro](../../repro/LFV-047/), [bug page](../../bugs/LFV-047.html))
- COSINE vectors whose float32 squared norm underflows pass validation and give Inf/NaN scores or poison OSQ segments (bug LFV-046; [repro](../../repro/LFV-046/), [bug page](../../bugs/LFV-046.html))
- Quantized MIP/EUCLIDEAN scores become NaN when OSQ correction terms overflow (bug LFV-064; [repro](../../repro/LFV-064/))
- Quantized MIP score of a document equal to its segment centroid is 3.0 instead of 3.5 (bug LFV-079; [repro](../../repro/LFV-079/))
- PACKED_NIBBLE quantized vectors with an odd dimension fail once raw vectors are dropped (bug LFV-065; [repro](../../repro/LFV-065/))
- OptimizedScalarQuantizer emits out-of-range codes when the interval width is deep-subnormal (bug LFV-074; [repro](../../repro/LFV-074/))

## Files

- [smt/accumulators.py](smt/accumulators.py)
- [smt/cosine_precond.py](smt/cosine_precond.py)
- [smt/dump_mono.py](smt/dump_mono.py)
- [smt/dump_r1.py](smt/dump_r1.py)
- [smt/error_model.py](smt/error_model.py)
- [smt/fpjava.py](smt/fpjava.py)
- [smt/mip_single_doc.py](smt/mip_single_doc.py)
- [smt/mip_single_doc_m4p.py](smt/mip_single_doc_m4p.py)
- [smt/packing_bv.py](smt/packing_bv.py)
- [smt/quantize_range.py](smt/quantize_range.py)
- [smt/score_transforms.py](smt/score_transforms.py)
- [smt/semantics.py](smt/semantics.py)
- [smt/smt2/mono_euc.smt2](smt/smt2/mono_euc.smt2)
- [smt/smt2/mono_mip.smt2](smt/smt2/mono_mip.smt2)
- [smt/smt2/mono_qdot.smt2](smt/smt2/mono_qdot.smt2)
- [smt/smt2/mono_qeuc.smt2](smt/smt2/mono_qeuc.smt2)
- [smt/smt2/mono_recip.smt2](smt/smt2/mono_recip.smt2)
- [smt/smt2/r1n_bits1.smt2](smt/smt2/r1n_bits1.smt2)
- [smt/smt2/r1n_bits2.smt2](smt/smt2/r1n_bits2.smt2)
- [smt/smt2/r1n_bits4.smt2](smt/smt2/r1n_bits4.smt2)
- [smt/smt2/r1n_bits7.smt2](smt/smt2/r1n_bits7.smt2)
- [smt/smt2/r1n_bits8.smt2](smt/smt2/r1n_bits8.smt2)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

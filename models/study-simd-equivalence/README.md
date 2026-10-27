# Formal models and scripts: study-simd-equivalence

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Doc-values range queries with a skip index return wrong hits when a segment's values span more than Long.MAX_VALUE (bug LFV-044; [repro](../../repro/LFV-044/))
- Panama minMaxScalarQuantize rounds differently from the default implementation at one boundary value (bug LFV-071; [repro](../../repro/LFV-071/))

## Files

- [smt/byte_kernels.py](smt/byte_kernels.py)
- [smt/byte_kernels_comp.py](smt/byte_kernels_comp.py)
- [smt/dv_fix_check.py](smt/dv_fix_check.py)
- [smt/dv_gcd1_classify.py](smt/dv_gcd1_classify.py)
- [smt/dv_gcd_transform.py](smt/dv_gcd_transform.py)
- [smt/dv_gcd_transform_int.py](smt/dv_gcd_transform_int.py)
- [smt/int4_kernels.py](smt/int4_kernels.py)
- [smt/int4bit_abstract.py](smt/int4bit_abstract.py)
- [smt/misc_kernels.py](smt/misc_kernels.py)
- [smt/mutation_checks.py](smt/mutation_checks.py)
- [smt/quantize_round_fp.py](smt/quantize_round_fp.py)
- [smt/vecsem.py](smt/vecsem.py)
- [smt/obligations/mut_int4DotProductBothPacked_VB128_n32.smt2](smt/obligations/mut_int4DotProductBothPacked_VB128_n32.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB128_n33.smt2](smt/obligations/mut_int4DotProductBothPacked_VB128_n33.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB256_n32.smt2](smt/obligations/mut_int4DotProductBothPacked_VB256_n32.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB256_n33.smt2](smt/obligations/mut_int4DotProductBothPacked_VB256_n33.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB256_n64.smt2](smt/obligations/mut_int4DotProductBothPacked_VB256_n64.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB512_n32.smt2](smt/obligations/mut_int4DotProductBothPacked_VB512_n32.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB512_n33.smt2](smt/obligations/mut_int4DotProductBothPacked_VB512_n33.smt2)
- [smt/obligations/mut_int4DotProductBothPacked_VB512_n64.smt2](smt/obligations/mut_int4DotProductBothPacked_VB512_n64.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB128_n32.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB128_n32.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB128_n33.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB128_n33.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB256_n32.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB256_n32.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB256_n33.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB256_n33.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB256_n64.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB256_n64.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB512_n32.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB512_n32.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB512_n33.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB512_n33.smt2)
- [smt/obligations/mut_int4DotProductSinglePacked_VB512_n64.smt2](smt/obligations/mut_int4DotProductSinglePacked_VB512_n64.smt2)
- [smt/obligations/mut_int4DotProduct_VB128_n32.smt2](smt/obligations/mut_int4DotProduct_VB128_n32.smt2)
- [smt/obligations/mut_int4DotProduct_VB128_n33.smt2](smt/obligations/mut_int4DotProduct_VB128_n33.smt2)
- [smt/obligations/mut_int4DotProduct_VB256_n32.smt2](smt/obligations/mut_int4DotProduct_VB256_n32.smt2)
- [smt/obligations/mut_int4DotProduct_VB256_n33.smt2](smt/obligations/mut_int4DotProduct_VB256_n33.smt2)
- [smt/obligations/mut_int4DotProduct_VB256_n64.smt2](smt/obligations/mut_int4DotProduct_VB256_n64.smt2)
- [smt/obligations/mut_int4DotProduct_VB512_n32.smt2](smt/obligations/mut_int4DotProduct_VB512_n32.smt2)
- [smt/obligations/mut_int4DotProduct_VB512_n33.smt2](smt/obligations/mut_int4DotProduct_VB512_n33.smt2)
- [smt/obligations/mut_int4DotProduct_VB512_n64.smt2](smt/obligations/mut_int4DotProduct_VB512_n64.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB128_n32.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB128_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB128_n33.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB128_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n32.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n33.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n64.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB256_n64.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n32.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n33.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n64.smt2](smt/obligations/mut_int4SquareDistanceBothPacked_VB512_n64.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB128_n32.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB128_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB128_n33.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB128_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n32.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n33.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n64.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB256_n64.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n32.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n32.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n33.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n33.smt2)
- [smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n64.smt2](smt/obligations/mut_int4SquareDistanceSinglePacked_VB512_n64.smt2)
- [smt/obligations/mut_int4SquareDistance_VB128_n32.smt2](smt/obligations/mut_int4SquareDistance_VB128_n32.smt2)
- [smt/obligations/mut_int4SquareDistance_VB128_n33.smt2](smt/obligations/mut_int4SquareDistance_VB128_n33.smt2)
- [smt/obligations/mut_int4SquareDistance_VB256_n32.smt2](smt/obligations/mut_int4SquareDistance_VB256_n32.smt2)
- [smt/obligations/mut_int4SquareDistance_VB256_n33.smt2](smt/obligations/mut_int4SquareDistance_VB256_n33.smt2)
- [smt/obligations/mut_int4SquareDistance_VB256_n64.smt2](smt/obligations/mut_int4SquareDistance_VB256_n64.smt2)
- [smt/obligations/mut_int4SquareDistance_VB512_n32.smt2](smt/obligations/mut_int4SquareDistance_VB512_n32.smt2)
- [smt/obligations/mut_int4SquareDistance_VB512_n33.smt2](smt/obligations/mut_int4SquareDistance_VB512_n33.smt2)
- [smt/obligations/mut_int4SquareDistance_VB512_n64.smt2](smt/obligations/mut_int4SquareDistance_VB512_n64.smt2)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

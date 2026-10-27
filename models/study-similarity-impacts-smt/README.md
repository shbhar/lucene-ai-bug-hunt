# Formal models and scripts: study-similarity-impacts-smt

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- AxiomaticF2LOG and AxiomaticF2EXP scores drop by 1 ulp as term frequency crosses a power of two (bug LFV-063; [repro](../../repro/LFV-063/))
- IBSimilarity with DistributionLL returns -0.0f scores (bug LFV-072; [repro](../../repro/LFV-072/))
- BaseSimilarityTestCase's 'scores don't decrease as terms get rarer' check is dead code (bug LFV-073; [repro](../../repro/LFV-073/))

## Files

- [smt/axiomatic_f2.py](smt/axiomatic_f2.py)
- [smt/axiomatic_f2_fast.py](smt/axiomatic_f2_fast.py)
- [smt/axiomatic_f2_narrow.py](smt/axiomatic_f2_narrow.py)
- [smt/axiomatic_f2_stage2.py](smt/axiomatic_f2_stage2.py)
- [smt/float_scorers.py](smt/float_scorers.py)
- [smt/fpjava.py](smt/fpjava.py)
- [smt/impacts_bv.py](smt/impacts_bv.py)
- [smt/models.py](smt/models.py)
- [smt/monotype.py](smt/monotype.py)
- [smt/oplemmas.py](smt/oplemmas.py)
- [smt/range_proofs.py](smt/range_proofs.py)
- [smt/smtrun.py](smt/smtrun.py)
- [smt/translation_validate_f2.py](smt/translation_validate_f2.py)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

; DenseBinaryDocValues(variable).binaryValues: (int)(lastEnd-firstStart) is exact
; core/.../codecs/lucene90/Lucene90DocValuesProducer.java:1347 & :1389-1391 (#16286, 2026-06-30)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun firstStart () (_ BitVec 64))
(declare-fun lastEnd () (_ BitVec 64))
(assert
 (and (bvsge firstStart (_ bv0 64)) (bvsge lastEnd firstStart) (bvsge (_ bv1099511627776 64) (bvsub lastEnd firstStart))))
(assert
 (let ((?x57 (bvsub lastEnd firstStart)))
 (let (($x20 (= ((_ sign_extend 32) ((_ extract 31 0) ?x57)) ?x57)))
 (not $x20))))
(check-sat)

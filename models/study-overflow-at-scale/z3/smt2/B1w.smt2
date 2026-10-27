;   ... witness size=2,length=2^30 gives a negative array size
; core/.../codecs/lucene90/Lucene90DocValuesProducer.java:1347 & :1389-1391 (#16286, 2026-06-30)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun size () (_ BitVec 32))
(assert
 (let (($x34 (= length (_ bv1073741824 32))))
 (let (($x24499 (= size (_ bv2 32))))
 (let (($x1364 (bvsle length (_ bv2147483631 32))))
 (let (($x16 (bvsle size (_ bv2147483631 32))))
 (let (($x47 (and (bvsge size (_ bv1 32)) $x16 (bvsge length (_ bv0 32)) $x1364)))
 (and $x47 $x24499 $x34)))))))
(assert
 (let ((?x43 (bvmul size length)))
 (let (($x1118 (bvsge ?x43 (_ bv0 32))))
 (not $x1118))))
(check-sat)

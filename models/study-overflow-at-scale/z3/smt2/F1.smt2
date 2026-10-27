; DirectReader merge buffers: baseOffset+(index*bpv)/8 exact for index<2^56 (long math)
; core/.../util/packed/DirectReader.java:141,158,172
; benchmark generated from python API
(set-info :status unknown)
(declare-fun baseOffset () (_ BitVec 64))
(declare-fun bpv () (_ BitVec 64))
(declare-fun index () (_ BitVec 64))
(assert
 (let (($x21233 (bvsle bpv (_ bv64 64))))
 (and (bvsge index (_ bv0 64)) (bvsgt (_ bv72057594037927936 64) index) (bvsge bpv (_ bv1 64)) $x21233 (bvsge baseOffset (_ bv0 64)) (bvsgt (_ bv4611686018427387904 64) baseOffset))))
(assert
 (let ((?x1118 (bvadd ((_ sign_extend 64) baseOffset) (bvsdiv (bvmul ((_ sign_extend 64) index) ((_ sign_extend 64) bpv)) (_ bv8 128)))))
 (let (($x20045 (= ((_ sign_extend 64) (bvadd baseOffset (bvsdiv (bvmul index bpv) (_ bv8 64)))) ?x1118)))
 (not $x20045))))
(check-sat)

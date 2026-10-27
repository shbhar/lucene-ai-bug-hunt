; PackedInts.Format.PACKED.longCount: (int)((byteCount+7)>>>3) exact for valueCount<=2^31-1, bpv<=64
; core/.../util/packed/PackedInts.java:148-153
; benchmark generated from python API
(set-info :status unknown)
(declare-fun bitsPerValue () (_ BitVec 32))
(declare-fun valueCount () (_ BitVec 32))
(assert
 (let (($x16780 (bvsle bitsPerValue (_ bv64 32))))
 (and (bvsge valueCount (_ bv0 32)) (bvsge bitsPerValue (_ bv0 32)) $x16780)))
(assert
 (let ((?x15978 (bvadd (bvmul ((_ zero_extend 32) valueCount) ((_ zero_extend 32) bitsPerValue)) (_ bv7 64))))
 (let ((?x3567 (bvsdiv ?x15978 (_ bv8 64))))
 (let ((?x19605 (bvlshr (bvadd ?x3567 (_ bv7 64)) (_ bv3 64))))
 (let (($x3621 (= ((_ sign_extend 32) ((_ extract 31 0) ?x19605)) ?x19605)))
 (not $x3621))))))
(check-sat)

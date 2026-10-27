(set-logic QF_BV)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun s0 () (_ BitVec 16))
(declare-fun s1 () (_ BitVec 16))
(declare-fun s2 () (_ BitVec 16))
(declare-fun s3 () (_ BitVec 16))
(declare-fun s4 () (_ BitVec 16))
(declare-fun s5 () (_ BitVec 16))
(declare-fun s6 () (_ BitVec 16))
(declare-fun s7 () (_ BitVec 16))
(declare-fun s8 () (_ BitVec 16))
(declare-fun s9 () (_ BitVec 16))
(declare-fun s10 () (_ BitVec 16))
(declare-fun s11 () (_ BitVec 16))
(declare-fun s12 () (_ BitVec 16))
(declare-fun s13 () (_ BitVec 16))
(declare-fun s14 () (_ BitVec 16))
(declare-fun s15 () (_ BitVec 16))
(declare-fun s16 () (_ BitVec 16))
(declare-fun s17 () (_ BitVec 16))
(declare-fun s18 () (_ BitVec 16))
(declare-fun s19 () (_ BitVec 16))
(declare-fun s20 () (_ BitVec 16))
(declare-fun s21 () (_ BitVec 16))
(declare-fun s22 () (_ BitVec 16))
(declare-fun s23 () (_ BitVec 16))
(declare-fun s24 () (_ BitVec 16))
(declare-fun s25 () (_ BitVec 16))
(declare-fun s26 () (_ BitVec 16))
(declare-fun s27 () (_ BitVec 16))
(declare-fun s28 () (_ BitVec 16))
(declare-fun s29 () (_ BitVec 16))
(declare-fun s30 () (_ BitVec 16))
(declare-fun s31 () (_ BitVec 16))
(assert
 (bvule s0 (_ bv225 16)))
(assert
 (bvule s1 (_ bv225 16)))
(assert
 (bvule s2 (_ bv225 16)))
(assert
 (bvule s3 (_ bv225 16)))
(assert
 (bvule s4 (_ bv225 16)))
(assert
 (bvule s5 (_ bv225 16)))
(assert
 (bvule s6 (_ bv225 16)))
(assert
 (bvule s7 (_ bv225 16)))
(assert
 (bvule s8 (_ bv225 16)))
(assert
 (bvule s9 (_ bv225 16)))
(assert
 (bvule s10 (_ bv225 16)))
(assert
 (bvule s11 (_ bv225 16)))
(assert
 (bvule s12 (_ bv225 16)))
(assert
 (bvule s13 (_ bv225 16)))
(assert
 (bvule s14 (_ bv225 16)))
(assert
 (bvule s15 (_ bv225 16)))
(assert
 (bvule s16 (_ bv225 16)))
(assert
 (bvule s17 (_ bv225 16)))
(assert
 (bvule s18 (_ bv225 16)))
(assert
 (bvule s19 (_ bv225 16)))
(assert
 (bvule s20 (_ bv225 16)))
(assert
 (bvule s21 (_ bv225 16)))
(assert
 (bvule s22 (_ bv225 16)))
(assert
 (bvule s23 (_ bv225 16)))
(assert
 (bvule s24 (_ bv225 16)))
(assert
 (bvule s25 (_ bv225 16)))
(assert
 (bvule s26 (_ bv225 16)))
(assert
 (bvule s27 (_ bv225 16)))
(assert
 (bvule s28 (_ bv225 16)))
(assert
 (bvule s29 (_ bv225 16)))
(assert
 (bvule s30 (_ bv225 16)))
(assert
 (bvule s31 (_ bv225 16)))
(assert
 (let ((?x11734 ((_ zero_extend 16) s31)))
 (let ((?x3663 ((_ zero_extend 16) s30)))
 (let ((?x6053 ((_ zero_extend 16) s29)))
 (let ((?x2924 ((_ zero_extend 16) s28)))
 (let ((?x297 ((_ zero_extend 16) s27)))
 (let ((?x3858 ((_ zero_extend 16) s26)))
 (let ((?x6807 ((_ zero_extend 16) s25)))
 (let ((?x14521 ((_ zero_extend 16) s24)))
 (let ((?x2525 ((_ zero_extend 16) s23)))
 (let ((?x6428 ((_ zero_extend 16) s22)))
 (let ((?x13651 ((_ zero_extend 16) s21)))
 (let ((?x4021 ((_ zero_extend 16) s20)))
 (let ((?x6293 ((_ zero_extend 16) s19)))
 (let ((?x6401 ((_ zero_extend 16) s18)))
 (let ((?x7423 ((_ zero_extend 16) s17)))
 (let ((?x2377 ((_ zero_extend 16) s16)))
 (let ((?x13178 ((_ zero_extend 16) s15)))
 (let ((?x1356 ((_ zero_extend 16) s14)))
 (let ((?x673 ((_ zero_extend 16) s13)))
 (let ((?x3109 ((_ zero_extend 16) s12)))
 (let ((?x1516 ((_ zero_extend 16) s11)))
 (let ((?x1050 ((_ zero_extend 16) s10)))
 (let ((?x8776 ((_ zero_extend 16) s9)))
 (let ((?x1889 ((_ zero_extend 16) s8)))
 (let ((?x10789 ((_ zero_extend 16) s7)))
 (let ((?x13019 ((_ zero_extend 16) s6)))
 (let ((?x13657 ((_ zero_extend 16) s5)))
 (let ((?x1654 ((_ zero_extend 16) s4)))
 (let ((?x3748 ((_ zero_extend 16) s3)))
 (let ((?x4733 ((_ zero_extend 16) s2)))
 (let ((?x4864 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) ((_ zero_extend 16) s0)) ((_ zero_extend 16) s1)) ?x4733) ?x3748)))
 (let ((?x7362 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x4864 ?x1654) ?x13657) ?x13019) ?x10789) ?x1889) ?x8776)))
 (let ((?x760 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x7362 ?x1050) ?x1516) ?x3109) ?x673) ?x1356) ?x13178)))
 (let ((?x662 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x760 ?x2377) ?x7423) ?x6401) ?x6293) ?x4021) ?x13651)))
 (let ((?x4287 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x662 ?x6428) ?x2525) ?x14521) ?x6807) ?x3858) ?x297)))
 (let ((?x5357 (bvadd (bvadd (bvadd (bvadd ?x4287 ?x2924) ?x6053) ?x3663) ?x11734)))
 (let ((?x6598 (bvadd (bvand (concat (bvadd (_ bv0 16) s30) (bvadd (_ bv0 16) s28)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s30) (bvadd (_ bv0 16) s28)) (_ bv16 32)))))
 (let ((?x5572 (bvadd ?x6598 (bvand (concat (bvadd (_ bv0 16) s31) (bvadd (_ bv0 16) s29)) (_ bv65535 32)))))
 (let ((?x2465 (bvadd ?x5572 (bvlshr (concat (bvadd (_ bv0 16) s31) (bvadd (_ bv0 16) s29)) (_ bv16 32)))))
 (let ((?x3070 (bvadd (bvand (concat (bvadd (_ bv0 16) s26) (bvadd (_ bv0 16) s24)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s26) (bvadd (_ bv0 16) s24)) (_ bv16 32)))))
 (let ((?x6773 (bvadd ?x3070 (bvand (concat (bvadd (_ bv0 16) s27) (bvadd (_ bv0 16) s25)) (_ bv65535 32)))))
 (let ((?x12091 (bvadd ?x6773 (bvlshr (concat (bvadd (_ bv0 16) s27) (bvadd (_ bv0 16) s25)) (_ bv16 32)))))
 (let ((?x6473 (bvadd (bvand (concat (bvadd (_ bv0 16) s22) (bvadd (_ bv0 16) s20)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s22) (bvadd (_ bv0 16) s20)) (_ bv16 32)))))
 (let ((?x1291 (bvadd ?x6473 (bvand (concat (bvadd (_ bv0 16) s23) (bvadd (_ bv0 16) s21)) (_ bv65535 32)))))
 (let ((?x2934 (bvadd ?x1291 (bvlshr (concat (bvadd (_ bv0 16) s23) (bvadd (_ bv0 16) s21)) (_ bv16 32)))))
 (let ((?x1568 (bvadd (bvand (concat (bvadd (_ bv0 16) s18) (bvadd (_ bv0 16) s16)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s18) (bvadd (_ bv0 16) s16)) (_ bv16 32)))))
 (let ((?x4090 (bvadd ?x1568 (bvand (concat (bvadd (_ bv0 16) s19) (bvadd (_ bv0 16) s17)) (_ bv65535 32)))))
 (let ((?x12891 (bvadd ?x4090 (bvlshr (concat (bvadd (_ bv0 16) s19) (bvadd (_ bv0 16) s17)) (_ bv16 32)))))
 (let ((?x3807 (bvadd (bvand (concat (bvadd (_ bv0 16) s14) (bvadd (_ bv0 16) s12)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s14) (bvadd (_ bv0 16) s12)) (_ bv16 32)))))
 (let ((?x12076 (bvadd ?x3807 (bvand (concat (bvadd (_ bv0 16) s15) (bvadd (_ bv0 16) s13)) (_ bv65535 32)))))
 (let ((?x7251 (bvadd ?x12076 (bvlshr (concat (bvadd (_ bv0 16) s15) (bvadd (_ bv0 16) s13)) (_ bv16 32)))))
 (let ((?x10429 (bvadd (bvand (concat (bvadd (_ bv0 16) s10) (bvadd (_ bv0 16) s8)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s10) (bvadd (_ bv0 16) s8)) (_ bv16 32)))))
 (let ((?x1338 (bvadd ?x10429 (bvand (concat (bvadd (_ bv0 16) s11) (bvadd (_ bv0 16) s9)) (_ bv65535 32)))))
 (let ((?x6740 (bvadd ?x1338 (bvlshr (concat (bvadd (_ bv0 16) s11) (bvadd (_ bv0 16) s9)) (_ bv16 32)))))
 (let ((?x2720 (bvadd (bvand (concat (bvadd (_ bv0 16) s6) (bvadd (_ bv0 16) s4)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s6) (bvadd (_ bv0 16) s4)) (_ bv16 32)))))
 (let ((?x11920 (bvadd ?x2720 (bvand (concat (bvadd (_ bv0 16) s7) (bvadd (_ bv0 16) s5)) (_ bv65535 32)))))
 (let ((?x10263 (bvadd ?x11920 (bvlshr (concat (bvadd (_ bv0 16) s7) (bvadd (_ bv0 16) s5)) (_ bv16 32)))))
 (let ((?x12794 (bvadd (bvand (concat (bvadd (_ bv0 16) s2) (bvadd (_ bv0 16) s0)) (_ bv65535 32)) (bvlshr (concat (bvadd (_ bv0 16) s2) (bvadd (_ bv0 16) s0)) (_ bv16 32)))))
 (let ((?x1064 (bvadd ?x12794 (bvand (concat (bvadd (_ bv0 16) s3) (bvadd (_ bv0 16) s1)) (_ bv65535 32)))))
 (let ((?x4523 (bvadd ?x1064 (bvlshr (concat (bvadd (_ bv0 16) s3) (bvadd (_ bv0 16) s1)) (_ bv16 32)))))
 (let ((?x5550 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x4523 ?x10263) ?x6740) ?x7251) ?x12891) ?x2934) ?x12091)))
 (let ((?x5537 (bvadd (_ bv0 32) (bvadd (_ bv0 32) (bvadd ?x5550 ?x2465)))))
 (and (distinct (bvadd ?x5537 (_ bv1 32)) (bvadd (bvadd ?x5357 (_ bv1 32)) (_ bv1 32))) true))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
(check-sat)

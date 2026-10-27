(set-logic QF_BV)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun p0 () (_ BitVec 8))
(declare-fun p1 () (_ BitVec 8))
(declare-fun p2 () (_ BitVec 8))
(declare-fun p3 () (_ BitVec 8))
(declare-fun p4 () (_ BitVec 8))
(declare-fun p5 () (_ BitVec 8))
(declare-fun p6 () (_ BitVec 8))
(declare-fun p7 () (_ BitVec 8))
(declare-fun p8 () (_ BitVec 8))
(declare-fun p9 () (_ BitVec 8))
(declare-fun p10 () (_ BitVec 8))
(declare-fun p11 () (_ BitVec 8))
(declare-fun p12 () (_ BitVec 8))
(declare-fun p13 () (_ BitVec 8))
(declare-fun p14 () (_ BitVec 8))
(declare-fun p15 () (_ BitVec 8))
(declare-fun p16 () (_ BitVec 8))
(declare-fun p17 () (_ BitVec 8))
(declare-fun p18 () (_ BitVec 8))
(declare-fun p19 () (_ BitVec 8))
(declare-fun p20 () (_ BitVec 8))
(declare-fun p21 () (_ BitVec 8))
(declare-fun p22 () (_ BitVec 8))
(declare-fun p23 () (_ BitVec 8))
(declare-fun p24 () (_ BitVec 8))
(declare-fun p25 () (_ BitVec 8))
(declare-fun p26 () (_ BitVec 8))
(declare-fun p27 () (_ BitVec 8))
(declare-fun p28 () (_ BitVec 8))
(declare-fun p29 () (_ BitVec 8))
(declare-fun p30 () (_ BitVec 8))
(declare-fun p31 () (_ BitVec 8))
(assert
 (bvule p0 (_ bv225 8)))
(assert
 (bvule p1 (_ bv225 8)))
(assert
 (bvule p2 (_ bv225 8)))
(assert
 (bvule p3 (_ bv225 8)))
(assert
 (bvule p4 (_ bv225 8)))
(assert
 (bvule p5 (_ bv225 8)))
(assert
 (bvule p6 (_ bv225 8)))
(assert
 (bvule p7 (_ bv225 8)))
(assert
 (bvule p8 (_ bv225 8)))
(assert
 (bvule p9 (_ bv225 8)))
(assert
 (bvule p10 (_ bv225 8)))
(assert
 (bvule p11 (_ bv225 8)))
(assert
 (bvule p12 (_ bv225 8)))
(assert
 (bvule p13 (_ bv225 8)))
(assert
 (bvule p14 (_ bv225 8)))
(assert
 (bvule p15 (_ bv225 8)))
(assert
 (bvule p16 (_ bv225 8)))
(assert
 (bvule p17 (_ bv225 8)))
(assert
 (bvule p18 (_ bv225 8)))
(assert
 (bvule p19 (_ bv225 8)))
(assert
 (bvule p20 (_ bv225 8)))
(assert
 (bvule p21 (_ bv225 8)))
(assert
 (bvule p22 (_ bv225 8)))
(assert
 (bvule p23 (_ bv225 8)))
(assert
 (bvule p24 (_ bv225 8)))
(assert
 (bvule p25 (_ bv225 8)))
(assert
 (bvule p26 (_ bv225 8)))
(assert
 (bvule p27 (_ bv225 8)))
(assert
 (bvule p28 (_ bv225 8)))
(assert
 (bvule p29 (_ bv225 8)))
(assert
 (bvule p30 (_ bv225 8)))
(assert
 (bvule p31 (_ bv225 8)))
(assert
 (let ((?x1243 ((_ zero_extend 24) p31)))
 (let ((?x51 ((_ zero_extend 24) p30)))
 (let ((?x3101 ((_ zero_extend 24) p29)))
 (let ((?x2341 ((_ zero_extend 24) p28)))
 (let ((?x2318 ((_ zero_extend 24) p27)))
 (let ((?x1352 ((_ zero_extend 24) p26)))
 (let ((?x4137 ((_ zero_extend 24) p25)))
 (let ((?x2313 ((_ zero_extend 24) p24)))
 (let ((?x2940 ((_ zero_extend 24) p23)))
 (let ((?x56 ((_ zero_extend 24) p22)))
 (let ((?x1316 ((_ zero_extend 24) p21)))
 (let ((?x3120 ((_ zero_extend 24) p20)))
 (let ((?x35 ((_ zero_extend 24) p19)))
 (let ((?x3923 ((_ zero_extend 24) p18)))
 (let ((?x82 ((_ zero_extend 24) p17)))
 (let ((?x3755 ((_ zero_extend 24) p16)))
 (let ((?x4067 ((_ zero_extend 24) p15)))
 (let ((?x341 ((_ zero_extend 24) p14)))
 (let ((?x4491 ((_ zero_extend 24) p13)))
 (let ((?x4304 ((_ zero_extend 24) p12)))
 (let ((?x3782 ((_ zero_extend 24) p11)))
 (let ((?x3727 ((_ zero_extend 24) p10)))
 (let ((?x3103 ((_ zero_extend 24) p9)))
 (let ((?x343 ((_ zero_extend 24) p8)))
 (let ((?x103 ((_ zero_extend 24) p7)))
 (let ((?x2279 ((_ zero_extend 24) p6)))
 (let ((?x4286 ((_ zero_extend 24) p5)))
 (let ((?x2383 ((_ zero_extend 24) p4)))
 (let ((?x4308 ((_ zero_extend 24) p3)))
 (let ((?x2344 ((_ zero_extend 24) p2)))
 (let ((?x4238 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) ((_ zero_extend 24) p0)) ((_ zero_extend 24) p1)) ?x2344) ?x4308)))
 (let ((?x3125 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x4238 ?x2383) ?x4286) ?x2279) ?x103) ?x343) ?x3103)))
 (let ((?x50 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x3125 ?x3727) ?x3782) ?x4304) ?x4491) ?x341) ?x4067)))
 (let ((?x1945 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x50 ?x3755) ?x82) ?x3923) ?x35) ?x3120) ?x1316)))
 (let ((?x3059 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x1945 ?x56) ?x2940) ?x2313) ?x4137) ?x1352) ?x2318)))
 (let ((?x2953 (bvadd (bvadd (bvadd (bvadd ?x3059 ?x2341) ?x3101) ?x51) ?x1243)))
 (let ((?x6 (concat (bvadd (_ bv0 16) (bvand (concat p31 p30) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p29 p28) (_ bv255 16))))))
 (let ((?x3657 (concat (bvadd (_ bv0 16) (bvlshr (concat p31 p30) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p29 p28) (_ bv8 16))))))
 (let ((?x364 (bvadd (bvadd (bvand ?x3657 (_ bv65535 32)) (bvlshr ?x3657 (_ bv16 32))) (bvand ?x6 (_ bv65535 32)))))
 (let ((?x4373 (concat (bvadd (_ bv0 16) (bvand (concat p27 p26) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p25 p24) (_ bv255 16))))))
 (let ((?x4298 (concat (bvadd (_ bv0 16) (bvlshr (concat p27 p26) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p25 p24) (_ bv8 16))))))
 (let ((?x3823 (bvadd (bvadd (bvand ?x4298 (_ bv65535 32)) (bvlshr ?x4298 (_ bv16 32))) (bvand ?x4373 (_ bv65535 32)))))
 (let ((?x1626 (concat (bvadd (_ bv0 16) (bvand (concat p23 p22) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p21 p20) (_ bv255 16))))))
 (let ((?x45 (concat (bvadd (_ bv0 16) (bvlshr (concat p23 p22) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p21 p20) (_ bv8 16))))))
 (let ((?x3998 (bvadd (bvadd (bvand ?x45 (_ bv65535 32)) (bvlshr ?x45 (_ bv16 32))) (bvand ?x1626 (_ bv65535 32)))))
 (let ((?x3035 (concat (bvadd (_ bv0 16) (bvand (concat p19 p18) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p17 p16) (_ bv255 16))))))
 (let ((?x3088 (concat (bvadd (_ bv0 16) (bvlshr (concat p19 p18) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p17 p16) (_ bv8 16))))))
 (let ((?x2227 (bvadd (bvadd (bvand ?x3088 (_ bv65535 32)) (bvlshr ?x3088 (_ bv16 32))) (bvand ?x3035 (_ bv65535 32)))))
 (let ((?x1851 (concat (bvadd (_ bv0 16) (bvand (concat p15 p14) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p13 p12) (_ bv255 16))))))
 (let ((?x4232 (concat (bvadd (_ bv0 16) (bvlshr (concat p15 p14) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p13 p12) (_ bv8 16))))))
 (let ((?x3226 (bvadd (bvadd (bvand ?x4232 (_ bv65535 32)) (bvlshr ?x4232 (_ bv16 32))) (bvand ?x1851 (_ bv65535 32)))))
 (let ((?x2996 (concat (bvadd (_ bv0 16) (bvand (concat p11 p10) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p9 p8) (_ bv255 16))))))
 (let ((?x2216 (concat (bvadd (_ bv0 16) (bvlshr (concat p11 p10) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p9 p8) (_ bv8 16))))))
 (let ((?x1465 (bvadd (bvadd (bvand ?x2216 (_ bv65535 32)) (bvlshr ?x2216 (_ bv16 32))) (bvand ?x2996 (_ bv65535 32)))))
 (let ((?x1215 (concat (bvadd (_ bv0 16) (bvand (concat p7 p6) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p5 p4) (_ bv255 16))))))
 (let ((?x3309 (concat (bvadd (_ bv0 16) (bvlshr (concat p7 p6) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p5 p4) (_ bv8 16))))))
 (let ((?x3850 (bvadd (bvadd (bvand ?x3309 (_ bv65535 32)) (bvlshr ?x3309 (_ bv16 32))) (bvand ?x1215 (_ bv65535 32)))))
 (let ((?x3317 (concat (bvadd (_ bv0 16) (bvand (concat p3 p2) (_ bv255 16))) (bvadd (_ bv0 16) (bvand (concat p1 p0) (_ bv255 16))))))
 (let ((?x3889 (concat (bvadd (_ bv0 16) (bvlshr (concat p3 p2) (_ bv8 16))) (bvadd (_ bv0 16) (bvlshr (concat p1 p0) (_ bv8 16))))))
 (let ((?x3852 (bvadd (bvadd (bvand ?x3889 (_ bv65535 32)) (bvlshr ?x3889 (_ bv16 32))) (bvand ?x3317 (_ bv65535 32)))))
 (let ((?x4317 (bvadd (bvadd ?x3852 (bvlshr ?x3317 (_ bv16 32))) (bvadd ?x3850 (bvlshr ?x1215 (_ bv16 32))))))
 (let ((?x1577 (bvadd (bvadd ?x4317 (bvadd ?x1465 (bvlshr ?x2996 (_ bv16 32)))) (bvadd ?x3226 (bvlshr ?x1851 (_ bv16 32))))))
 (let ((?x1546 (bvadd (bvadd ?x1577 (bvadd ?x2227 (bvlshr ?x3035 (_ bv16 32)))) (bvadd ?x3998 (bvlshr ?x1626 (_ bv16 32))))))
 (let ((?x1548 (bvadd (bvadd ?x1546 (bvadd ?x3823 (bvlshr ?x4373 (_ bv16 32)))) (bvadd ?x364 (bvlshr ?x6 (_ bv16 32))))))
 (let ((?x4113 (bvadd (_ bv0 32) (bvadd (_ bv0 32) ?x1548))))
 (and (distinct (bvadd ?x4113 (_ bv1 32)) (bvadd (bvadd ?x2953 (_ bv1 32)) (_ bv1 32))) true)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
(check-sat)

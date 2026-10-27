; LongHeap.push growth target (size*3+1)/2 >= size+1 for every reachable size
; core/.../util/LongHeap.java:76-80 push(): ArrayUtil.grow(heap, (size * 3 + 1) / 2)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun size () (_ BitVec 32))
(assert
 (let (($x236 (bvsle size (_ bv2147483631 32))))
 (and (bvsge size (_ bv2 32)) $x236)))
(assert
 (let (($x1118 (and (bvsgt (bvsdiv (bvadd (bvmul size (_ bv3 32)) (_ bv1 32)) (_ bv2 32)) size))))
 (not $x1118)))
(check-sat)

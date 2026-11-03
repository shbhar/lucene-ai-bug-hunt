# Replays every row of mbt/mbt-main-da.tsv (real jar) through the Z3 model of partition_smt.py (step=math, i.e. the
# code as it is on main) and through the fixed model (step=point) and reports agreement / predicted classes.
import sys
sys.path.insert(0, '.')
from z3 import BitVecVal, If, simplify
import importlib.util
spec = importlib.util.spec_from_file_location('m', 'partition_smt.py')
src = open('partition_smt.py').read().split('\nok = True')[0]   # model definitions only, no query run
ns = {}
exec(compile(src, 'partition_smt.py', 'exec'), ns)
mk = ns['mk']
models = {64: ns['mk'](64), 32: ns['mk'](32)}
agree = dis = 0; fixed_mismatch = []
for line in open(sys.argv[1]):
    if not line[:2] in ('64', '32'):
        continue
    w, form, bb, xb, n = line.split('\t'); w = int(w); n = int(n)
    m = models[w]; b = BitVecVal(int(bb, 16), w); x = BitVecVal(int(xb, 16), w)
    def nmodel(step):
        if form == 'A':
            down = m['step_down_math'] if step == 'math' else m['step_down_pt']
            lo = m['in_range'](x, m['NINF'], down(b)); hi = m['in_range'](x, b, m['PINF'])
        else:
            up = m['step_up_math'] if step == 'math' else m['step_up_pt']
            lo = m['in_range'](x, m['NINF'], b); hi = m['in_range'](x, up(b), m['PINF'])
        return simplify(If(lo, 1, 0) + If(hi, 1, 0)).as_long()
    if nmodel('math') == n: agree += 1
    else: dis += 1; print('DISAGREE', line.strip())
    nf = nmodel('point')
    if nf != 1: fixed_mismatch.append(f"{w} {form} b={bb} x={xb} n_fixed={nf}")
print(f"model(step=math) vs jar: agree={agree} disagree={dis}")
print(f"fixed model (DoublePoint/FloatPoint stepping) partition violations: {len(fixed_mismatch)}")
for r in fixed_mismatch: print('  ', r)

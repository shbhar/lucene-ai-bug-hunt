"""Run a z3py-built Solver through cvc5 (SMT-LIB2 QF_FPBV) and/or z3; parse sat/unsat."""
import os
import subprocess
import tempfile
import time

CVC5 = os.environ.get('CVC5', 'cvc5')
LOGDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'logs', 'smt2')
os.makedirs(LOGDIR, exist_ok=True)


def cvc5_check(solver, name, get_values=(), timeout_s=1800, logic='QF_FPBV'):
    fname = os.path.join(LOGDIR, name.replace(' ', '_').replace('/', '_')[:80] + '.smt2')
    with open(fname, 'w') as fh:
        fh.write('(set-logic %s)\n(set-option :produce-models true)\n' % logic)
        fh.write(solver.to_smt2().replace('(check-sat)', ''))
        fh.write('(check-sat)\n')
        if get_values:
            fh.write('(get-value (%s))\n' % ' '.join(get_values))
    t0 = time.time()
    try:
        p = subprocess.run([CVC5, '--tlimit=%d' % (timeout_s * 1000), fname],
                           capture_output=True, text=True, timeout=timeout_s + 60)
        out = p.stdout.strip()
    except subprocess.TimeoutExpired:
        out = 'timeout'
    dt = time.time() - t0
    first = out.split('\n')[0] if out else 'error:' + p.stderr[:200]
    return first, out, dt, fname

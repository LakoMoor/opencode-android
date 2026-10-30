#!/usr/bin/env python3
"""Runs a command on a real PTY. stdin/stdout are plain bytes; "\x00R<rows>,<cols>\n" resizes the PTY."""
import fcntl, os, select, signal, struct, sys, termios

def setsize(fd, rows, cols):
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack('HHHH', rows, cols, 0, 0))

def write_all(fd, data):
    while data:
        n = os.write(fd, data); data = data[n:]

import pty
pid, master = pty.fork()
if pid == 0:
    os.execvp(sys.argv[1], sys.argv[1:])
setsize(master, int(os.environ.get('ROWS', 24)), int(os.environ.get('COLS', 80)))
stdin, stdout, pending = sys.stdin.fileno(), sys.stdout.fileno(), b''
while True:
    ready, _, _ = select.select([stdin, master], [], [])
    if master in ready:
        try: data = os.read(master, 65536)
        except OSError: break
        if not data: break
        write_all(stdout, data)
    if stdin in ready:
        data = os.read(stdin, 65536)
        if not data:
            os.kill(pid, signal.SIGHUP); break
        pending += data
        while pending:
            i = pending.find(b'\x00R')
            if i < 0:
                keep = 1 if pending.endswith(b'\x00') else 0
                write_all(master, pending[:len(pending) - keep]); pending = pending[len(pending) - keep:]; break
            if i: write_all(master, pending[:i]); pending = pending[i:]
            j = pending.find(b'\n')
            if j < 0: break
            try:
                rows, cols = pending[2:j].decode().split(','); setsize(master, int(rows), int(cols))
                os.kill(pid, signal.SIGWINCH)
            except Exception: pass
            pending = pending[j + 1:]
try: _, status = os.waitpid(pid, 0)
except ChildProcessError: status = 0
sys.exit(os.waitstatus_to_exitcode(status))

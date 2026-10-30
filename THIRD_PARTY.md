# Included upstream components

The Android launcher is an independent adaptation. The OpenCode core and web UI are the original **2.2.2** distribution, unmodified. Bundled archives and native libraries are fetched by `scripts/prepare-runtime.py`, verified against `runtime/lock.json`, and excluded from Git.

| Component | Version | Source / license |
|---|---|---|
| OpenCode | 2.2.2, commit 34aaa9054bcd397027b0869a5ba83b2489129fcd | https://github.com/anomalyco/opencode/tree/34aaa9054bcd397027b0869a5ba83b2489129fcd · MIT |
| Ubuntu Base | 24.04.5 arm64 | https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ · individual package licenses in guest `/usr/share/doc/*/copyright` |
| Termux PRoot | 5.1.107.101 | https://github.com/termux/proot/tree/v5.1.107.101 · GPL-2.0 |
| libtalloc (Termux build) | 2.5.0 | https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz · see bundled COPYING |
| libandroid-shmem | 0.7 | https://github.com/termux/libandroid-shmem/tree/v0.7 · BSD-3-Clause |
| Apache Commons Compress | 1.27.1 | https://commons.apache.org/proper/commons-compress/ · Apache-2.0 |

License texts are included under Android assets `licenses/` and within the Ubuntu filesystem. PRoot/talloc/shmem binaries are unmodified Termux packages; build recipes are in https://github.com/termux/termux-packages/tree/master/packages/{proot,libtalloc,libandroid-shmem}. PRoot's build uses the Termux tag above, `ARG_MAX=131072`, `PROOT_WITH_LIBANDROID_SHMEM=true`, and an external loader (runtime path supplied with `PROOT_LOADER`). The launcher relocates libraries through `LD_LIBRARY_PATH`; it does not patch their code.

Before publishing a binary distribution, distribute the complete corresponding source and build materials for GPL components alongside the APK, and retain their notices. The source URLs and lock file document the ingredients for development; they do not replace the distributor's source obligations.

- xterm.js 5.3.0 and xterm-addon-fit 0.8.0 (MIT) — terminal emulator, bundled in `assets/terminal`; license in `assets/licenses/xterm.js-MIT.txt`.

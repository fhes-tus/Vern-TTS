# Repository conventions

- Preserve unrelated local changes. Android is the established application; do not change its Gradle build or UI as a side effect of desktop work.
- The selected desktop implementation is `desktop/`: Tauri 2, React, TypeScript, Vite, Rust and SQLite. `veritas-pc/` is the earlier Kotlin experiment and is retained as reference.
- Read `desktop/AGENTS.md` for desktop commands and `docs/desktop-architecture.md` for ownership boundaries.
- Source code is authoritative for parity claims. Update `progress.md` with commands actually run, observed failures and platforms actually tested. Browser checks cannot establish native behavior.

# Vern desktop conventions

- Read `../docs/desktop-architecture.md` and `../docs/desktop-feature-parity.md` before changing architecture.
- Rust owns all persistence, settings, file access, voice downloads and inference. Do not add localStorage/IndexedDB or frontend SQL/store as another persistence owner.
- Studio means Kokoro; Lite means Piper. Preserve the exact Kokoro v1.0 package and speaker mapping. No system/browser speech substitution.
- Keep imports and inference off the UI thread. Render books as plain text. Archive paths, extraction sizes, native requests and positions must be bounded/validated.
- Use shared CSS tokens and Android fonts; retain their notices. Keyboard controls, visible focus and reduced-motion support are required.
- Run `npm ci`, `npm run build`, `npm test`, `npm run lint`, `npm run format:check`, `npm run test:e2e` from this directory. Install Playwright Chromium once with `npx playwright install chromium`.
- Native: `cargo fmt --manifest-path src-tauri/Cargo.toml --all -- --check`, `cargo test --manifest-path src-tauri/Cargo.toml`, `cargo clippy --manifest-path src-tauri/Cargo.toml --all-targets -- -D warnings`, `npm run tauri build`.
- Windows local Rust bootstrap: `./scripts/native.ps1` starts the app and sets local toolchain environment. With a global Rust installation, use `npm run tauri dev`.
- Native verification: `cargo run --manifest-path src-tauri/Cargo.toml --example verify-native -- .artifacts/native --download`, then repeat without `--download` with networking disabled. Do not claim full offline verification unless actually performed.
- Keep credentials, models, build output, logs and generated WAVs out of Git. Track npm and Cargo lockfiles. Update parity/progress for scope changes and blockers.
- Do not publish releases or enable updater permissions during unsigned build validation.

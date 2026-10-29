# Ceet

Peramban privat untuk desktop dan Android. Satu produk untuk membuka web, mencari, dan memahami halaman, dengan semua pemrosesan di perangkat.

![logo](desktop/assets/icon-256.png)

## Isi repositori

| Folder | Bahasa | Fungsi |
|---|---|---|
| `engine/` | Rust | Inti: omnibox, pemblokir pelacak, deteksi penipuan, pencarian lokal, ringkasan. Tanpa dependensi |
| `desktop/` | JavaScript (Electron) | Aplikasi Windows, macOS, Linux |
| `android/` | Kotlin (Compose) | Aplikasi Android |
| `tools/` | Node, Python, Bash | Pembuat vektor emas, pembuat ikon, skrip pemeriksaan |
| `shared/vectors/` | JSON | Jawaban acuan dari mesin Rust yang harus dipenuhi port JS dan Kotlin |
| `data/` | teks | Daftar domain pelacak, dipakai semua platform |

Kenapa banyak bahasa: Rust untuk logika yang harus cepat dan aman memori, JavaScript karena Electron, Kotlin karena Android, Python dan Bash untuk perkakas. Logika inti ada di tiga bahasa, jadi ketiganya diuji terhadap **vektor emas** yang sama agar tidak berbeda perilaku.

## Mulai

```bash
bash tools/check.sh                 # tes Rust, vektor emas, ikon, tes desktop

# Desktop
cargo build --release --manifest-path engine/Cargo.toml
cd desktop && npm install && npm start

# Android: perlu Android SDK. Atau biarkan GitHub Actions membangun APK
cd android && gradle assembleDebug
```

Alur CI di `.github/workflows` menjalankan semua tes, membangun APK debug, dan membuat installer desktop saat ada tag `v*`.

## Status

Baca [docs/FEATURES.md](docs/FEATURES.md). Ringkasnya:

- Mesin Rust: **diuji** (35 tes) dan JS dicocokkan dengan hasilnya.
- Tampilan desktop: dirender dan dicek di Chromium dengan data tiruan, **belum dijalankan di Electron asli**.
- Android: kode lengkap dan tes unit ditulis, **belum dikompilasi** karena Android SDK tidak tersedia di tempat pembuatan. CI yang akan membuktikannya.
- AI yang butuh training: ditunda. Ringkasan memakai TextRank, bukan model.

Lihat [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) untuk cara kerjanya.

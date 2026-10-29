# Status fitur Ceet

Mengikuti daftar status di `CEET_Spesifikasi_Induk_Fitur.txt` (bagian 23). Tabel ini hanya menandai **TERSEDIA** untuk hal yang sudah dijalankan dan diuji di sini. Selebihnya diberi status apa adanya.

Cara memeriksa tiap klaim ada di kolom "Bukti".

| Status | Arti |
|---|---|
| TERSEDIA | Sudah dijalankan dan lolos tes otomatis |
| SIAP DIUJI | Kode lengkap, tetapi belum dijalankan di perangkat targetnya (Electron asli, HP Android) |
| MEMERLUKAN API / LAYANAN | Butuh layanan luar atau infrastruktur |
| DIRANCANG | Belum ditulis |
| DITUNDA | Sengaja ditunda |

## Inti (Rust, dipakai desktop)

| Fitur | Status | Bukti |
|---|---|---|
| Klasifikasi omnibox: alamat, pencarian, pertanyaan, perintah | TERSEDIA | 4 tes Rust, 19 vektor emas, juga dicocokkan di JS dan Kotlin |
| Pembersih parameter pelacak dan peningkatan HTTPS | TERSEDIA | 6 vektor emas, cocok di Rust dan JS |
| Pemblokir pelacak (174 domain) dengan pengecualian dan induk domain | TERSEDIA | tes Rust dan 7 vektor emas |
| Deteksi situs penipuan heuristik (merek palsu, huruf pengganti, punycode, IP, kata pancingan) | TERSEDIA | 14 vektor emas. Ini perkiraan, bisa keliru |
| Pencarian hibrida lokal: BM25 + vektor n-gram + RRF + frecency, toleran salah ketik | TERSEDIA | 5 tes Rust. Hanya mencari riwayat dan penanda milik pengguna |
| Ringkasan ekstraktif TextRank dan mode baca | TERSEDIA | 3 tes Rust. Kalimat selalu kutipan asli, tanpa AI |
| Protokol JSON antara mesin dan aplikasi | TERSEDIA | 4 tes Rust, tes jembatan JS |

## Desktop (Electron, JavaScript)

| Bagian spesifikasi | Fitur | Status |
|---|---|---|
| 2 | Tab: buka, tutup, sematkan, gandakan, buka kembali tab tertutup, pulihkan sesi, tab privat | SIAP DIUJI |
| 2 | Kembali, maju, muat ulang, beranda, layar penuh, zoom, pintasan keyboard | SIAP DIUJI |
| 2 | Saran omnibox dari riwayat dan penanda, perintah dengan awalan `>` | SIAP DIUJI |
| 2 | Tema terang, gelap, otomatis | SIAP DIUJI (tampilan sudah dicek di Chromium) |
| 3 | Pilihan mesin pencari: DuckDuckGo, Brave, Startpage, Ecosia, Google, Bing | SIAP DIUJI |
| 3 | Pencarian riwayat berdasarkan makna | SIAP DIUJI |
| 4, 5 | Ringkasan halaman tanpa AI (TextRank) | SIAP DIUJI |
| 6 | Blokir pelacak, HTTPS dulu, buang parameter, sinyal GPC, pangkas Referer, tolak izin kamera/mikrofon/lokasi | SIAP DIUJI |
| 6 | Peringatan penipuan sebelum halaman dimuat | SIAP DIUJI |
| 6 | Dasbor privasi dengan penjelasan batas perlindungan | SIAP DIUJI |
| 8 | Penanda (tanpa folder dan tag), riwayat dapat dicari dan dihapus | SIAP DIUJI |
| 9 | Daftar unduhan | SIAP DIUJI |
| 12 | Cari di halaman, menu konteks, alat pengembang | SIAP DIUJI |

## Android (Kotlin, Jetpack Compose)

| Fitur | Status |
|---|---|
| Tab, tab privat (profil WebView terpisah bila perangkat mendukung), pulihkan sesi | SIAP DIUJI |
| Omnibox, saran dari riwayat, perintah | SIAP DIUJI. Logika dicocokkan dengan vektor emas Rust lewat `GoldenTest` |
| Blokir pelacak pada sub-sumber, HTTPS dulu, buang parameter, GPC, tolak izin | SIAP DIUJI |
| Peringatan penipuan | SIAP DIUJI |
| Penanda, riwayat, dasbor privasi, pengaturan, tema | SIAP DIUJI |
| Unduhan lewat DownloadManager | SIAP DIUJI |
| Pencarian makna, ringkasan, mode baca | DIRANCANG. Mesin Rust belum dihubungkan ke Android (butuh JNI dan NDK) |

## Belum ada

| Fitur | Status | Catatan |
|---|---|---|
| AI Chat, AI Search, AI Research, agen multi-langkah, terjemahan AI | DITUNDA | Sesuai permintaan: AI yang butuh training ditunda |
| Mesin pencari dan indeks web sendiri (crawler, ranking) | MEMERLUKAN INFRASTRUKTUR | Saat ini Ceet memakai mesin pencari pihak ketiga pilihan pengguna |
| Pencarian gambar, video, berita, peta, produk | MEMERLUKAN API | |
| Akun, sinkronisasi, kata sandi, passkey | DIRANCANG | Butuh server akun dan enkripsi ujung ke ujung |
| VPN | MEMERLUKAN INFRASTRUKTUR | Ceet tidak mengklaim VPN |
| DNS-over-HTTPS | DIRANCANG | |
| Ekstensi | DIRANCANG | |
| Grup tab, tab vertikal, split screen, picture-in-picture | DIRANCANG | |
| Penampil PDF dengan anotasi | DIRANCANG | |
| Terjemahan halaman | MEMERLUKAN MODEL / API | |
| Penanda dengan folder dan tag, daftar bacaan, catatan | DIRANCANG | |
| Pemeriksaan malware dan daftar Safe Browsing | MEMERLUKAN LAYANAN | Deteksi penipuan Ceet bersifat heuristik lokal, bukan basis data |
| Profil dan workspace | DIRANCANG | |

## Batas yang perlu diketahui

- Ceet tidak menyembunyikan alamat IP dan tidak membuat pengguna anonim.
- Pemblokir hanya mengenali domain di `data/blocklist.txt`. Pelacak baru bisa lolos.
- Tab privat hanya tidak menyimpan riwayat dan data situs di perangkat ini.
- Di desktop, pengalihan server yang menuju alamat bertanda pelacak dibersihkan lewat hook permintaan. Di Android, pembersihan hanya berlaku untuk navigasi yang dilihat WebView, bukan setiap pengalihan di server.

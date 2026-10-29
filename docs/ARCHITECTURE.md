# Arsitektur

## Prinsip

1. **Di perangkat dulu.** Penilaian penipuan, pencarian riwayat, dan ringkasan berjalan lokal. Tidak ada alamat atau isi halaman yang dikirim ke server Ceet, karena belum ada server Ceet.
2. **Satu sumber kebenaran.** Daftar pelacak ada di `data/blocklist.txt`. Ikon ada di `desktop/ui/icons.js` dan diterjemahkan ke Kotlin oleh `tools/gen_icons.py`. Token warna ada di `desktop/ui/theme.css` dan `Theme.kt`.
3. **Jujur soal batas.** Dasbor privasi dan FEATURES.md menyebut apa yang tidak dilakukan.

## Algoritma di mesin Rust

**Omnibox.** Aturan berlapis: awalan `>` jadi perintah. Tanpa spasi dan berskema yang dikenal jadi alamat. Tanpa spasi dengan akhiran domain dikenal, `localhost`, atau IP jadi alamat. Dimulai kata tanya atau berakhiran `?` jadi pertanyaan. Sisanya pencarian.

**Pemblokir.** Himpunan domain. Cocokkan host dan setiap induk domainnya (`a.b.c.com`, `b.c.com`, `c.com`) dalam waktu sebanding jumlah label. Permintaan ke domain terdaftar yang sama dengan halaman tidak diblokir. Awalan `@@` membuat pengecualian.

**Deteksi penipuan.** Skor tambahan dari sinyal: `@` di alamat, IP, punycode, terlalu banyak subdomain, akhiran berisiko, nama merek di subdomain, huruf pengganti (`paypa1`), jarak edit satu dari merek, kata pancingan. Domain resmi merek dibebaskan. Skor 25 ke atas waspada, 60 ke atas berbahaya.

**Pencarian lokal.**
1. Tokenisasi, buang kata henti Indonesia dan Inggris, stemming ringan (awalan dan akhiran Indonesia, akhiran Inggris).
2. Peringkat leksikal BM25 (k1=1.4, b=0.75), judul dihitung tiga kali.
3. Peringkat semantik: vektor 256 dimensi dari n-gram karakter (3 dan 4) dan kata dengan *feature hashing* bertanda, lalu kemiripan kosinus. Ini menangkap salah ketik dan variasi kata. Tidak ada pelatihan.
4. Gabungkan dengan Reciprocal Rank Fusion (k=60).
5. Kalikan dengan frecency (peluruhan setengah-hidup 14 hari) dan bonus bila kata ada di alamat atau judul.

**Ringkasan.** Pecah kalimat, bobot kemiripan antarkalimat, lalu PageRank (damping 0.85, maksimal 40 iterasi). Kalimat awal diunggulkan, kalimat terlalu pendek atau panjang dikurangi, dan kalimat yang terlalu mirip dengan yang sudah dipilih dilewati.

**Mode baca.** Buang blok skrip, navigasi, kaki halaman. Ambil paragraf berdasarkan kepadatan teks dan rasio tautan.

## Desktop

```
Proses utama (main.js)
 ├─ sesi Chromium: hook permintaan (blokir, HTTPS, parameter, header)
 ├─ tab = WebContentsView, tersandbox, tanpa Node
 ├─ shell (index.html): tab, omnibox
 ├─ overlay (overlay.html): saran, perisai, menu, pembaca, di atas tab
 ├─ ceet://app/*: halaman internal dengan API terbatas (preload-page.js)
 └─ ceet-engine (Rust) lewat stdin/stdout JSON per baris, mulai ulang otomatis
```

Jika biner mesin tidak ada, aplikasi tetap jalan dengan klasifikasi dasar dan menampilkan status "Terbatas" di Pengaturan. Tidak ada fitur yang berpura-pura aktif.

## Android

`BrowserState` memegang tab dan pengaturan. `CeetClient` (WebViewClient) memblokir sub-sumber lewat `shouldInterceptRequest` dan membersihkan navigasi utama. UI Compose dengan bilah bawah agar terjangkau satu tangan. Logika inti adalah port Kotlin dari mesin Rust, diuji dengan `GoldenTest`.

## Langkah berikutnya yang disarankan

1. Jalankan CI dan perbaiki kesalahan kompilasi Kotlin bila ada.
2. Jalankan desktop di Electron asli dan perbaiki hal yang tidak terlihat dari pratinjau.
3. Hubungkan mesin Rust ke Android lewat JNI agar pencarian makna dan ringkasan ikut tersedia.
4. Tambah penampil PDF, grup tab, dan terjemahan.
5. Rancang sinkronisasi terenkripsi ujung ke ujung sebelum membuat akun.

//! Deteksi phishing heuristik, berjalan lokal tanpa mengirim URL ke server mana pun.

use crate::url::{registrable_domain, Url};

#[derive(Debug, Clone, PartialEq)]
pub enum Risk {
    Aman,
    Waspada,
    Berbahaya,
}

#[derive(Debug, Clone)]
pub struct Assessment {
    pub risk: Risk,
    pub score: u32,
    pub reasons: Vec<String>,
}

const BRANDS: &[&str] = &[
    "google", "facebook", "instagram", "whatsapp", "tokopedia", "shopee", "bukalapak", "gojek", "grab",
    "dana", "ovo", "gopay", "linkaja", "bca", "bni", "bri", "mandiri", "cimb", "permata", "jenius",
    "paypal", "apple", "microsoft", "amazon", "netflix", "tiktok", "telkomsel", "indosat", "traveloka",
    "github", "twitter", "linkedin", "youtube", "spotify", "binance", "coinbase",
];
/// Domain resmi merek yang tidak boleh ditandai meniru dirinya sendiri.
const OFFICIAL: &[&str] = &[
    "google.com", "google.co.id", "facebook.com", "instagram.com", "whatsapp.com", "tokopedia.com",
    "shopee.co.id", "shopee.com", "bukalapak.com", "gojek.com", "grab.com", "dana.id", "ovo.id",
    "linkaja.id", "bca.co.id", "bni.co.id", "bri.co.id", "bankmandiri.co.id", "mandiri.co.id", "cimbniaga.co.id",
    "permatabank.com", "jenius.com", "paypal.com", "apple.com", "microsoft.com", "amazon.com",
    "netflix.com", "tiktok.com", "telkomsel.com", "indosat.com", "traveloka.com", "github.com",
    "twitter.com", "x.com", "linkedin.com", "youtube.com", "spotify.com", "binance.com", "coinbase.com",
];
const RISKY_TLDS: &[&str] = &["zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz", "click", "country", "kim", "loan", "work", "rest"];
const LURE_WORDS: &[&str] = &[
    "login", "signin", "verify", "verifikasi", "secure", "account", "akun", "update", "konfirmasi",
    "banking", "wallet", "bonus", "hadiah", "undian", "klaim", "suspend", "blokir", "password", "otp",
];
const SHORTENERS: &[&str] = &["bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rebrand.ly", "s.id", "shorturl.at"];

pub fn levenshtein(a: &str, b: &str) -> usize {
    let a: Vec<char> = a.chars().collect();
    let b: Vec<char> = b.chars().collect();
    let mut prev: Vec<usize> = (0..=b.len()).collect();
    for i in 1..=a.len() {
        let mut cur = vec![i; b.len() + 1];
        for j in 1..=b.len() {
            let cost = if a[i - 1] == b[j - 1] { 0 } else { 1 };
            cur[j] = (prev[j] + 1).min(cur[j - 1] + 1).min(prev[j - 1] + cost);
        }
        prev = cur;
    }
    prev[b.len()]
}

/// Ganti angka dan huruf mirip: 0->o, 1->l, 3->e, 5->s, rn->m, vv->w.
fn deleet(s: &str) -> String {
    s.replace("rn", "m")
        .replace("vv", "w")
        .chars()
        .map(|c| match c {
            '0' => 'o',
            '1' | '!' | '|' => 'l',
            '3' => 'e',
            '4' => 'a',
            '5' | '$' => 's',
            '7' => 't',
            _ => c,
        })
        .collect()
}

pub fn assess(url: &str) -> Assessment {
    let mut score: u32 = 0;
    let mut reasons: Vec<String> = Vec::new();
    let mut add = |pts: u32, why: &str| {
        score += pts;
        reasons.push(why.to_string());
    };

    let u = match Url::parse(url) {
        Some(u) => u,
        None => return Assessment { risk: Risk::Aman, score: 0, reasons },
    };
    let reg = registrable_domain(&u.host);
    let official = OFFICIAL.contains(&reg.as_str());

    if u.scheme == "http" && !u.is_local_host() {
        add(10, "Koneksi tidak terenkripsi (http)");
    }
    if u.userinfo {
        add(35, "Alamat memuat tanda @ yang menyembunyikan tujuan asli");
    }
    if u.is_ip_host() && !u.is_local_host() {
        add(30, "Memakai alamat IP, bukan nama situs");
    }
    if u.host.contains("xn--") {
        add(35, "Nama situs memakai karakter menyerupai (punycode)");
    } else if !u.host.is_ascii() {
        add(35, "Nama situs memuat karakter non-Latin yang bisa menyerupai huruf biasa");
    }
    let labels = u.host.split('.').count();
    if labels >= 5 {
        add(15, "Terlalu banyak subdomain");
    }
    if u.host.len() > 45 {
        add(10, "Nama situs sangat panjang");
    }
    if u.host.matches('-').count() >= 3 {
        add(10, "Banyak tanda hubung di nama situs");
    }
    if let Some(tld) = u.host.rsplit('.').next() {
        if RISKY_TLDS.contains(&tld) {
            add(12, "Akhiran domain sering dipakai penipuan");
        }
    }
    if SHORTENERS.contains(&u.host.as_str()) {
        add(8, "Tautan pendek menyembunyikan tujuan asli");
    }

    if !official {
        let host_flat = deleet(&u.host.replace('-', ""));
        let first = reg.split('.').next().unwrap_or("");
        let first_d = deleet(first);
        for b in BRANDS {
            let in_sub = u.host.split('.').rev().skip(reg.split('.').count()).any(|l| l.contains(b));
            if in_sub {
                add(40, &format!("Memakai nama \"{}\" di subdomain padahal domain aslinya {}", b, reg));
                break;
            }
            if first_d == *b && first != *b {
                add(60, &format!("Menyamar sebagai \"{}\" dengan huruf pengganti", b));
                break;
            }
            if first_d.len() >= 5 && b.len() >= 5 && levenshtein(&first_d, b) == 1 {
                add(40, &format!("Ejaan hampir sama dengan \"{}\"", b));
                break;
            }
            if first_d.contains(b) && first_d != *b {
                add(25, &format!("Nama situs menempelkan merek \"{}\"", b));
                break;
            }
            if host_flat.contains(b) && !first_d.contains(b) {
                add(20, &format!("Nama \"{}\" muncul di bagian lain alamat", b));
                break;
            }
        }
    }

    let lower = format!("{}{}", u.host, u.path).to_ascii_lowercase();
    let lures: Vec<&&str> = LURE_WORDS.iter().filter(|w| lower.contains(**w)).collect();
    if !official {
        match lures.len() {
            0 => {}
            1 => add(8, "Kata pancingan di alamat (login, verifikasi, dan sejenisnya)"),
            _ => add(18, "Beberapa kata pancingan di alamat (login, verifikasi, dan sejenisnya)"),
        }
    }

    let (risk, score) = if official && score < 40 {
        (Risk::Aman, 0)
    } else if score >= 60 {
        (Risk::Berbahaya, score.min(100))
    } else if score >= 25 {
        (Risk::Waspada, score)
    } else {
        (Risk::Aman, score)
    };
    if official && risk == Risk::Aman {
        reasons.clear();
    }
    Assessment { risk, score, reasons }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn situs_resmi_aman() {
        for u in ["https://www.google.com/search?q=a", "https://accounts.google.com/signin", "https://www.bca.co.id/id/login", "https://github.com/x/y"] {
            assert_eq!(assess(u).risk, Risk::Aman, "{}", u);
        }
    }

    #[test]
    fn meniru_merek() {
        assert_eq!(assess("https://paypa1.com/login").risk, Risk::Berbahaya);
        assert_ne!(assess("https://tokopedla.com/").risk, Risk::Aman);
        assert_ne!(assess("https://login.bca.co.id.verifikasi-akun.xyz/").risk, Risk::Aman);
        assert_eq!(assess("http://secure-dana-hadiah.top/klaim/login").risk, Risk::Berbahaya);
    }

    #[test]
    fn karakter_menyerupai_dan_ip() {
        assert_ne!(assess("https://xn--pypal-4ve.com/").risk, Risk::Aman);
        assert_ne!(assess("http://45.12.77.9/bank/login").risk, Risk::Aman);
        assert_ne!(assess("https://google.com@evil.example/").risk, Risk::Aman);
    }

    #[test]
    fn situs_biasa_tidak_ditandai() {
        assert_eq!(assess("https://id.wikipedia.org/wiki/Kopi").risk, Risk::Aman);
        assert_eq!(assess("https://kompas.com/read/2026").risk, Risk::Aman);
        assert_eq!(assess("http://localhost:8080/login").risk, Risk::Aman);
    }

    #[test]
    fn jarak_edit() {
        assert_eq!(levenshtein("kitten", "sitting"), 3);
        assert_eq!(levenshtein("", "abc"), 3);
    }
}

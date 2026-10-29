//! Pencarian hibrida lokal untuk riwayat, penanda, tab, dan halaman tersimpan.
//! Lexical BM25 + vektor hashing + Reciprocal Rank Fusion + frecency. Tanpa pelatihan, tanpa server.

use crate::text::{cosine, embed, tokens};
use std::collections::HashMap;

const DIM: usize = 256;
const K1: f32 = 1.4;
const B: f32 = 0.75;
const RRF_K: f32 = 60.0;

#[derive(Debug, Clone)]
pub struct Doc {
    pub id: String,
    pub url: String,
    pub title: String,
    pub body: String,
    pub visits: u32,
    /// Detik sejak epoch untuk kunjungan terakhir.
    pub last_visit: u64,
}

#[derive(Debug, Clone)]
pub struct Hit {
    pub id: String,
    pub score: f32,
    pub why: Vec<String>,
}

struct Entry {
    doc: Doc,
    tf: HashMap<String, u32>,
    len: f32,
    vec: Vec<f32>,
    url_tokens: Vec<String>,
}

#[derive(Default)]
pub struct Index {
    entries: Vec<Entry>,
    df: HashMap<String, u32>,
    total_len: f32,
}

impl Index {
    pub fn new() -> Index {
        Index::default()
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn add(&mut self, doc: Doc) {
        self.remove(&doc.id);
        // Judul dihitung tiga kali agar lebih berbobot daripada isi.
        let text = format!("{0} {0} {0} {1} {2}", doc.title, doc.url, doc.body);
        let toks = tokens(&text);
        let mut tf: HashMap<String, u32> = HashMap::new();
        for t in &toks {
            *tf.entry(t.clone()).or_insert(0) += 1;
        }
        for t in tf.keys() {
            *self.df.entry(t.clone()).or_insert(0) += 1;
        }
        let len = toks.len() as f32;
        self.total_len += len;
        let vec = embed(&format!("{} {} {}", doc.title, doc.title, truncate(&doc.body, 2000)), DIM);
        let url_tokens = tokens(&doc.url);
        self.entries.push(Entry { doc, tf, len, vec, url_tokens });
    }

    pub fn remove(&mut self, id: &str) -> bool {
        if let Some(pos) = self.entries.iter().position(|e| e.doc.id == id) {
            let e = self.entries.swap_remove(pos);
            for t in e.tf.keys() {
                if let Some(c) = self.df.get_mut(t) {
                    *c -= 1;
                    if *c == 0 {
                        self.df.remove(t);
                    }
                }
            }
            self.total_len -= e.len;
            true
        } else {
            false
        }
    }

    fn bm25(&self, q: &[String]) -> Vec<(usize, f32)> {
        let n = self.entries.len() as f32;
        let avg = (self.total_len / n.max(1.0)).max(1.0);
        let mut out = Vec::new();
        for (i, e) in self.entries.iter().enumerate() {
            let mut s = 0.0;
            for t in q {
                if let Some(&f) = e.tf.get(t) {
                    let df = *self.df.get(t).unwrap_or(&0) as f32;
                    let idf = ((n - df + 0.5) / (df + 0.5) + 1.0).ln();
                    let f = f as f32;
                    s += idf * (f * (K1 + 1.0)) / (f + K1 * (1.0 - B + B * e.len / avg));
                }
            }
            if s > 0.0 {
                out.push((i, s));
            }
        }
        out.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        out
    }

    fn semantic(&self, qtext: &str) -> Vec<(usize, f32)> {
        let qv = embed(qtext, DIM);
        let mut out: Vec<(usize, f32)> = self
            .entries
            .iter()
            .enumerate()
            .map(|(i, e)| (i, cosine(&qv, &e.vec)))
            .filter(|(_, s)| *s > 0.12)
            .collect();
        out.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        out
    }

    /// Frecency: kunjungan sering dan baru-baru ini naik, dengan peluruhan setengah-hidup 14 hari.
    fn frecency(doc: &Doc, now: u64) -> f32 {
        let age_days = now.saturating_sub(doc.last_visit) as f32 / 86400.0;
        let decay = 0.5f32.powf(age_days / 14.0);
        (1.0 + (doc.visits as f32).ln_1p()) * decay
    }

    pub fn search(&self, query: &str, now: u64, limit: usize) -> Vec<Hit> {
        let q = tokens(query);
        if q.is_empty() && query.trim().is_empty() {
            return Vec::new();
        }
        let lex = self.bm25(&q);
        let sem = self.semantic(query);
        let mut fused: HashMap<usize, (f32, Vec<String>)> = HashMap::new();
        for (rank, (i, _)) in lex.iter().take(50).enumerate() {
            let e = fused.entry(*i).or_insert((0.0, Vec::new()));
            e.0 += 1.0 / (RRF_K + rank as f32 + 1.0);
            e.1.push("kata cocok".into());
        }
        for (rank, (i, _)) in sem.iter().take(50).enumerate() {
            let e = fused.entry(*i).or_insert((0.0, Vec::new()));
            e.0 += 0.8 / (RRF_K + rank as f32 + 1.0);
            e.1.push("mirip makna".into());
        }
        let ql = query.to_ascii_lowercase();
        let mut hits: Vec<Hit> = fused
            .into_iter()
            .map(|(i, (mut s, mut why))| {
                let e = &self.entries[i];
                let fr = Self::frecency(&e.doc, now);
                s *= 1.0 + 0.15 * fr.min(4.0);
                if fr > 1.5 {
                    why.push("sering dibuka".into());
                }
                if !q.is_empty() && q.iter().all(|t| e.url_tokens.contains(t)) {
                    s *= 1.25;
                    why.push("ada di alamat".into());
                }
                if e.doc.title.to_ascii_lowercase().contains(&ql) {
                    s *= 1.3;
                    why.push("ada di judul".into());
                }
                Hit { id: e.doc.id.clone(), score: s, why }
            })
            .collect();
        hits.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal).then(a.id.cmp(&b.id)));
        hits.truncate(limit);
        hits
    }

    /// Saran saat mengetik: awalan judul/alamat lebih dulu, lalu frecency.
    pub fn suggest(&self, prefix: &str, now: u64, limit: usize) -> Vec<Hit> {
        let p = prefix.trim().to_ascii_lowercase();
        if p.is_empty() {
            return Vec::new();
        }
        let mut hits: Vec<Hit> = self
            .entries
            .iter()
            .filter_map(|e| {
                let t = e.doc.title.to_ascii_lowercase();
                let u = e.doc.url.to_ascii_lowercase();
                let u_bare = u.trim_start_matches("https://").trim_start_matches("http://").trim_start_matches("www.");
                let fr = Self::frecency(&e.doc, now);
                if u_bare.starts_with(&p) {
                    Some(Hit { id: e.doc.id.clone(), score: 3.0 + fr, why: vec!["awalan alamat".into()] })
                } else if t.starts_with(&p) || t.split_whitespace().any(|w| w.starts_with(&p)) {
                    Some(Hit { id: e.doc.id.clone(), score: 2.0 + fr, why: vec!["awalan judul".into()] })
                } else if u_bare.contains(&p) {
                    Some(Hit { id: e.doc.id.clone(), score: 1.0 + fr, why: vec!["ada di alamat".into()] })
                } else {
                    None
                }
            })
            .collect();
        hits.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal).then(a.id.cmp(&b.id)));
        hits.truncate(limit);
        hits
    }
}

fn truncate(s: &str, max: usize) -> &str {
    if s.len() <= max {
        return s;
    }
    let mut i = max;
    while !s.is_char_boundary(i) {
        i -= 1;
    }
    &s[..i]
}

#[cfg(test)]
mod tests {
    use super::*;

    fn doc(id: &str, title: &str, url: &str, body: &str, visits: u32, last: u64) -> Doc {
        Doc { id: id.into(), url: url.into(), title: title.into(), body: body.into(), visits, last_visit: last }
    }

    fn sample() -> Index {
        let mut ix = Index::new();
        ix.add(doc("1", "Resep nasi goreng kampung", "https://masak.id/nasi-goreng", "Bumbu bawang merah, cabai, kecap manis, nasi putih dingin.", 3, 1_000_000));
        ix.add(doc("2", "Cara merawat sepeda motor", "https://otomotif.id/rawat", "Ganti oli, cek rantai, dan servis berkala motor kesayangan.", 1, 1_000_000));
        ix.add(doc("3", "Belajar Rust untuk pemula", "https://belajar.dev/rust", "Ownership, borrowing, dan lifetime dijelaskan dengan contoh.", 9, 1_000_000));
        ix
    }

    #[test]
    fn cocok_kata_dan_stem() {
        let ix = sample();
        let h = ix.search("memasak nasi", 1_000_100, 5);
        assert_eq!(h[0].id, "1");
        let h = ix.search("servis motor", 1_000_100, 5);
        assert_eq!(h[0].id, "2");
    }

    #[test]
    fn toleran_salah_ketik() {
        let ix = sample();
        let h = ix.search("rwat sepeda motr", 1_000_100, 5);
        assert!(!h.is_empty());
        assert_eq!(h[0].id, "2");
    }

    #[test]
    fn frecency_mengangkat_yang_sering() {
        let mut ix = Index::new();
        ix.add(doc("a", "Berita ekonomi", "https://a.id/x", "ekonomi pasar saham", 1, 0));
        ix.add(doc("b", "Berita ekonomi", "https://b.id/x", "ekonomi pasar saham", 40, 1_000_000));
        let h = ix.search("ekonomi pasar", 1_000_000, 5);
        assert_eq!(h[0].id, "b");
    }

    #[test]
    fn hapus_dan_ganti() {
        let mut ix = sample();
        assert!(ix.remove("2"));
        assert_eq!(ix.len(), 2);
        assert!(ix.search("rantai oli", 1_000_100, 5).is_empty());
        ix.add(doc("1", "Resep soto ayam", "https://masak.id/soto", "kuah kuning ayam", 1, 1_000_000));
        assert_eq!(ix.len(), 2);
        assert_eq!(ix.search("soto", 1_000_100, 5)[0].id, "1");
    }

    #[test]
    fn saran_awalan() {
        let ix = sample();
        let s = ix.suggest("bela", 1_000_100, 5);
        assert_eq!(s[0].id, "3");
        let s = ix.suggest("masak.id", 1_000_100, 5);
        assert_eq!(s[0].id, "1");
    }
}

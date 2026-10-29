//! Perlindungan privasi: pemblokir pelacak, peningkatan HTTPS, dan penilaian tingkat perlindungan.

use crate::url::{registrable_domain, Url};
use std::collections::HashSet;

/// Daftar bawaan domain pelacak dan iklan yang dikenal. Daftar lengkap dimuat dari data/blocklist.txt.
const BUILTIN: &str = include_str!("../../data/blocklist.txt");

pub struct Blocker {
    domains: HashSet<String>,
    allow: HashSet<String>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Decision {
    Allow,
    Block { rule: String },
}

impl Blocker {
    pub fn builtin() -> Blocker {
        Blocker::from_list(BUILTIN)
    }

    /// Format: satu domain per baris, `#` komentar, awalan `@@` untuk pengecualian.
    pub fn from_list(text: &str) -> Blocker {
        let mut domains = HashSet::new();
        let mut allow = HashSet::new();
        for line in text.lines() {
            let l = line.trim().to_ascii_lowercase();
            if l.is_empty() || l.starts_with('#') {
                continue;
            }
            if let Some(a) = l.strip_prefix("@@") {
                allow.insert(a.trim().to_string());
            } else {
                domains.insert(l);
            }
        }
        Blocker { domains, allow }
    }

    pub fn len(&self) -> usize {
        self.domains.len()
    }

    /// Cocokkan host dan semua induk domainnya (a.b.c.com -> b.c.com -> c.com).
    fn matches(set: &HashSet<String>, host: &str) -> Option<String> {
        let mut h = host;
        loop {
            if set.contains(h) {
                return Some(h.to_string());
            }
            match h.find('.') {
                Some(i) => h = &h[i + 1..],
                None => return None,
            }
        }
    }

    /// `page_host` adalah situs yang sedang dibuka. Permintaan ke domain yang sama tidak diblokir.
    pub fn check(&self, request_host: &str, page_host: &str) -> Decision {
        let rh = request_host.to_ascii_lowercase();
        if registrable_domain(&rh) == registrable_domain(&page_host.to_ascii_lowercase()) {
            return Decision::Allow;
        }
        if Self::matches(&self.allow, &rh).is_some() {
            return Decision::Allow;
        }
        match Self::matches(&self.domains, &rh) {
            Some(rule) => Decision::Block { rule },
            None => Decision::Allow,
        }
    }
}

/// Naikkan http ke https kecuali host lokal atau alamat IP.
pub fn upgrade_https(url: &str) -> (String, bool) {
    if let Some(u) = Url::parse(url) {
        if u.scheme == "http" && !u.is_local_host() && !u.is_ip_host() {
            let mut n = u.clone();
            n.scheme = "https".into();
            if n.port == Some(80) {
                n.port = None;
            }
            return (n.to_string(), true);
        }
    }
    (url.to_string(), false)
}

#[derive(Debug, Clone)]
pub struct PageReport {
    pub blocked: usize,
    pub trackers: Vec<String>,
    pub params_stripped: usize,
    pub upgraded: bool,
    pub secure: bool,
    pub score: u32,
    pub grade: &'static str,
}

/// Ringkasan perlindungan satu halaman. Skor 0-100, bukan jaminan anonimitas.
pub fn report(blocker: &Blocker, page_url: &str, request_hosts: &[String]) -> PageReport {
    let (up, upgraded) = upgrade_https(page_url);
    let parsed = Url::parse(&up);
    let (ph, secure, stripped) = match &parsed {
        Some(u) => (u.host.clone(), u.scheme == "https", u.strip_tracking().1.len()),
        None => (String::new(), false, 0),
    };
    let mut trackers: Vec<String> = Vec::new();
    for h in request_hosts {
        if let Decision::Block { rule } = blocker.check(h, &ph) {
            if !trackers.contains(&rule) {
                trackers.push(rule);
            }
        }
    }
    let blocked = request_hosts
        .iter()
        .filter(|h| matches!(blocker.check(h, &ph), Decision::Block { .. }))
        .count();
    let mut score: i32 = 100;
    if !secure {
        score -= 40;
    }
    let third_party = request_hosts
        .iter()
        .filter(|h| registrable_domain(h) != registrable_domain(&ph))
        .count() as i32;
    score -= (third_party.min(20)) * 1;
    // Pelacak yang berhasil diblokir tidak mengurangi skor. Hanya yang lolos.
    score = score.clamp(0, 100);
    let grade = match score {
        90..=100 => "A",
        75..=89 => "B",
        55..=74 => "C",
        _ => "D",
    };
    PageReport { blocked, trackers, params_stripped: stripped, upgraded, secure, score: score as u32, grade }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blokir_subdomain_dan_pengecualian() {
        let b = Blocker::from_list("pelacak.example\n@@ok.pelacak.example\n");
        assert!(matches!(b.check("cdn.pelacak.example", "berita.id"), Decision::Block { .. }));
        assert_eq!(b.check("ok.pelacak.example", "berita.id"), Decision::Allow);
        assert_eq!(b.check("pelacak.example", "pelacak.example"), Decision::Allow);
    }

    #[test]
    fn bawaan_memuat_domain_umum() {
        let b = Blocker::builtin();
        assert!(b.len() > 100);
        assert!(matches!(b.check("www.google-analytics.com", "toko.id"), Decision::Block { .. }));
        assert_eq!(b.check("fonts.gstatic.com", "toko.id"), Decision::Allow);
    }

    #[test]
    fn naik_https() {
        assert_eq!(upgrade_https("http://contoh.id/a"), ("https://contoh.id/a".to_string(), true));
        assert!(!upgrade_https("http://localhost:3000/").1);
        assert!(!upgrade_https("http://192.168.0.1/").1);
    }

    #[test]
    fn laporan_halaman() {
        let b = Blocker::builtin();
        let r = report(&b, "http://toko.id/?utm_source=a", &["www.google-analytics.com".into(), "toko.id".into()]);
        assert!(r.upgraded && r.secure);
        assert_eq!(r.blocked, 1);
        assert_eq!(r.params_stripped, 1);
        assert_eq!(r.grade, "A");
    }
}

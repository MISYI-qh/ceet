//! Omnibox: satu kolom untuk alamat, pencarian, pertanyaan, dan perintah.

use crate::url::{percent_encode, Url};

#[derive(Debug, Clone, PartialEq)]
pub enum Intent {
    Url { url: String },
    Search { query: String },
    Question { query: String },
    Command { name: String, arg: String },
    Empty,
}

const QUESTION_WORDS: &[&str] = &[
    "apa", "siapa", "kapan", "dimana", "di mana", "kenapa", "mengapa", "bagaimana", "berapa", "mana", "apakah",
    "what", "who", "when", "where", "why", "how", "which", "is", "are", "can", "does",
];
const KNOWN_TLDS: &[&str] = &[
    "com", "id", "net", "org", "io", "co", "dev", "app", "ai", "me", "tv", "xyz", "info", "edu", "gov", "biz",
    "my", "sg", "jp", "uk", "de", "fr", "cn", "in", "us", "au", "ru", "br", "kr", "nl", "es", "it", "ca", "nz", "th", "vn", "ph", "za", "tr", "pl", "se", "ch", "tech", "online", "site", "store", "news",
];

pub fn classify(raw: &str) -> Intent {
    let s = raw.trim();
    if s.is_empty() {
        return Intent::Empty;
    }
    if let Some(rest) = s.strip_prefix('>') {
        let rest = rest.trim();
        let (name, arg) = rest.split_once(' ').unwrap_or((rest, ""));
        return Intent::Command { name: name.to_ascii_lowercase(), arg: arg.trim().to_string() };
    }
    let lower = s.to_ascii_lowercase();
    if !s.contains(char::is_whitespace) {
        if let Some(u) = Url::parse(s) {
            if matches!(u.scheme.as_str(), "http" | "https" | "file" | "ceet" | "about") {
                return Intent::Url { url: s.to_string() };
            }
        }
        if let Some(url) = looks_like_host(s) {
            return Intent::Url { url };
        }
    }
    let q = s.to_string();
    let first = lower.split_whitespace().next().unwrap_or("");
    let words = lower.split_whitespace().count();
    if s.ends_with('?') || (words >= 3 && QUESTION_WORDS.contains(&first)) {
        Intent::Question { query: q.trim_end_matches('?').trim().to_string() }
    } else {
        Intent::Search { query: q }
    }
}

fn looks_like_host(s: &str) -> Option<String> {
    let (hostport, rest) = match s.find(|c| c == '/' || c == '?' || c == '#') {
        Some(i) => (&s[..i], &s[i..]),
        None => (s, ""),
    };
    let (host, port_ok) = match hostport.rsplit_once(':') {
        Some((h, p)) if !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()) => (h, true),
        _ => (hostport, false),
    };
    if host == "localhost" {
        return Some(format!("http://{}{}", hostport, rest));
    }
    let parts: Vec<&str> = host.split('.').collect();
    let ip = parts.len() == 4 && parts.iter().all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()));
    if ip {
        return Some(format!("http://{}{}", hostport, rest));
    }
    if parts.len() < 2 || parts.iter().any(|p| p.is_empty() || p.starts_with('-') || p.ends_with('-')) {
        return None;
    }
    if !parts.iter().all(|p| p.chars().all(|c| c.is_alphanumeric() || c == '-')) {
        return None;
    }
    let tld = parts[parts.len() - 1].to_ascii_lowercase();
    if !KNOWN_TLDS.contains(&tld.as_str()) && !port_ok {
        return None;
    }
    // Hindari menganggap "versi.2" atau "file.rs" sebagai situs tanpa alasan kuat.
    if tld.chars().any(|c| c.is_ascii_digit()) {
        return None;
    }
    Some(format!("https://{}{}", hostport, rest))
}

pub fn search_url(template: &str, query: &str) -> String {
    template.replace("{q}", &percent_encode(query))
}

/// Tujuan akhir navigasi untuk sebuah masukan. None berarti perintah internal.
pub fn resolve(raw: &str, search_template: &str) -> Option<String> {
    match classify(raw) {
        Intent::Url { url } => Some(url),
        Intent::Search { query } | Intent::Question { query } => Some(search_url(search_template, &query)),
        Intent::Command { .. } | Intent::Empty => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn alamat_dikenali() {
        assert_eq!(classify("kompas.com"), Intent::Url { url: "https://kompas.com".into() });
        assert_eq!(classify("detik.com/news?x=1"), Intent::Url { url: "https://detik.com/news?x=1".into() });
        assert_eq!(classify("localhost:3000"), Intent::Url { url: "http://localhost:3000".into() });
        assert_eq!(classify("https://a.id/x"), Intent::Url { url: "https://a.id/x".into() });
        assert_eq!(classify("192.168.1.1"), Intent::Url { url: "http://192.168.1.1".into() });
    }

    #[test]
    fn bukan_alamat() {
        assert!(matches!(classify("resep nasi goreng"), Intent::Search { .. }));
        assert!(matches!(classify("file.rs"), Intent::Search { .. }));
        assert!(matches!(classify("versi 2.0"), Intent::Search { .. }));
        assert!(matches!(classify("kopi"), Intent::Search { .. }));
    }

    #[test]
    fn pertanyaan_dan_perintah() {
        assert!(matches!(classify("kenapa langit berwarna biru"), Intent::Question { .. }));
        assert!(matches!(classify("harga emas hari ini?"), Intent::Question { .. }));
        assert_eq!(classify("> tema gelap"), Intent::Command { name: "tema".into(), arg: "gelap".into() });
        assert_eq!(classify("   "), Intent::Empty);
    }

    #[test]
    fn resolusi_pencarian() {
        assert_eq!(
            resolve("kopi & teh", "https://duckduckgo.com/?q={q}").unwrap(),
            "https://duckduckgo.com/?q=kopi%20%26%20teh"
        );
    }
}

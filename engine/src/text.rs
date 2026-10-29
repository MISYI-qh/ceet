//! Pengolah teks: tokenisasi, kata henti, stemming Indonesia + Inggris ringan, n-gram karakter.

const STOP: &[&str] = &[
    "yang", "dan", "di", "ke", "dari", "ini", "itu", "dengan", "untuk", "pada", "adalah", "dalam", "juga",
    "atau", "akan", "oleh", "saat", "para", "sebagai", "karena", "agar", "bisa", "dapat", "ada", "tidak",
    "lebih", "sudah", "telah", "serta", "bahwa", "namun", "tetapi", "sebuah", "suatu", "saya", "kamu",
    "kita", "kami", "mereka", "dia", "nya", "lah", "kah", "pun", "the", "a", "an", "of", "and", "to", "in",
    "is", "are", "was", "were", "for", "on", "with", "as", "by", "at", "it", "this", "that", "from", "or",
    "be", "been", "has", "have", "had", "not", "but", "if", "so", "than", "then", "its", "into",
];

pub fn is_stop(w: &str) -> bool {
    STOP.contains(&w)
}

/// Pecah teks menjadi kata huruf kecil. Tanda baca membatasi kata, angka dipertahankan.
pub fn words(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    for c in text.chars() {
        if c.is_alphanumeric() {
            for l in c.to_lowercase() {
                cur.push(l);
            }
        } else if !cur.is_empty() {
            out.push(std::mem::take(&mut cur));
        }
    }
    if !cur.is_empty() {
        out.push(cur);
    }
    out
}

/// Stemmer ringan. Cukup untuk menyatukan bentuk kata umum, bukan analisis morfologi lengkap.
pub fn stem(w: &str) -> String {
    let mut s = w.to_string();
    if s.len() <= 4 || !s.is_ascii() {
        return s;
    }
    for suf in ["lah", "kah", "pun"] {
        if s.len() > suf.len() + 3 && s.ends_with(suf) {
            s.truncate(s.len() - suf.len());
        }
    }
    for suf in ["nya", "kan", "an", "i"] {
        if s.len() > suf.len() + 3 && s.ends_with(suf) {
            s.truncate(s.len() - suf.len());
            break;
        }
    }
    for pre in ["meng", "meny", "mem", "men", "me", "peng", "peny", "pem", "pen", "ber", "per", "ter", "di", "ke", "se"] {
        if s.len() > pre.len() + 3 && s.starts_with(pre) {
            // mem/men/pem/pen + huruf hidup: huruf m/n kemungkinan bagian kata dasar (memasak -> masak).
            let single = matches!(pre, "mem" | "men" | "pem" | "pen");
            let after = &s[pre.len()..];
            let b_ok = single && {
                let tail = &pre[2..3];
                let b = &s[2..];
                b.len() > 3 && b.starts_with(tail) && b.as_bytes().get(1).map_or(false, |c| b"aeiou".contains(c))
            };
            s = if b_ok { s[2..].to_string() } else { after.to_string() };
            break;
        }
    }
    for suf in ["ingly", "ing", "ies", "ed", "es", "s"] {
        if s.len() > suf.len() + 3 && s.ends_with(suf) {
            if suf == "ies" {
                s.truncate(s.len() - 3);
                s.push('y');
            } else {
                s.truncate(s.len() - suf.len());
            }
            break;
        }
    }
    s
}

/// Token untuk indeks: tanpa kata henti, sudah di-stem.
pub fn tokens(text: &str) -> Vec<String> {
    words(text).into_iter().filter(|w| !is_stop(w)).map(|w| stem(&w)).collect()
}

/// Hash FNV-1a 64-bit.
pub fn fnv(s: &str) -> u64 {
    let mut h: u64 = 0xcbf29ce484222325;
    for b in s.as_bytes() {
        h ^= *b as u64;
        h = h.wrapping_mul(0x100000001b3);
    }
    h
}

/// Vektor semantik ringan lewat feature hashing: n-gram karakter (3-4) dan kata, dengan tanda hash.
/// Dimensi tetap, tanpa pelatihan. Kemiripan kosinus menangkap salah ketik dan variasi kata.
pub fn embed(text: &str, dim: usize) -> Vec<f32> {
    let mut v = vec![0f32; dim];
    for w in words(text) {
        if is_stop(&w) {
            continue;
        }
        let st = stem(&w);
        add(&mut v, &format!("w:{}", st), 1.5);
        let padded: Vec<char> = format!("^{}$", st).chars().collect();
        for n in 3..=4 {
            if padded.len() < n {
                continue;
            }
            for i in 0..=padded.len() - n {
                let g: String = padded[i..i + n].iter().collect();
                add(&mut v, &format!("g:{}", g), 1.0);
            }
        }
    }
    let norm = v.iter().map(|x| x * x).sum::<f32>().sqrt();
    if norm > 0.0 {
        for x in v.iter_mut() {
            *x /= norm;
        }
    }
    v
}

fn add(v: &mut [f32], feat: &str, weight: f32) {
    let h = fnv(feat);
    let idx = (h % v.len() as u64) as usize;
    let sign = if (h >> 63) & 1 == 0 { 1.0 } else { -1.0 };
    v[idx] += sign * weight;
}

pub fn cosine(a: &[f32], b: &[f32]) -> f32 {
    a.iter().zip(b).map(|(x, y)| x * y).sum()
}

/// Pecah teks menjadi kalimat.
pub fn sentences(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let chars: Vec<char> = text.chars().collect();
    for (i, &c) in chars.iter().enumerate() {
        cur.push(c);
        let end = matches!(c, '.' | '!' | '?' | '\n');
        if end {
            let next_space = chars.get(i + 1).map_or(true, |n| n.is_whitespace());
            let prev_digit = i > 0 && chars[i - 1].is_ascii_digit() && chars.get(i + 1).map_or(false, |n| n.is_ascii_digit());
            let short_abbr = c == '.' && cur.trim().len() <= 3;
            if next_space && !prev_digit && !short_abbr {
                let t = cur.trim().to_string();
                if t.chars().filter(|c| c.is_alphanumeric()).count() >= 3 {
                    out.push(t);
                }
                cur.clear();
            }
        }
    }
    let t = cur.trim().to_string();
    if t.chars().filter(|c| c.is_alphanumeric()).count() >= 3 {
        out.push(t);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn kata_dan_stem() {
        assert_eq!(words("Kopi-Susu, 2 gelas!"), vec!["kopi", "susu", "2", "gelas"]);
        assert_eq!(stem("memasak"), stem("masak"));
        assert_eq!(stem("pencarian"), stem("cari"));
        assert_eq!(stem("running"), "runn");
        assert_eq!(stem("studies"), "study");
    }

    #[test]
    fn embed_toleran_salah_ketik() {
        let a = embed("tokopedia", 256);
        let b = embed("tokopedai", 256);
        let c = embed("sepeda motor", 256);
        assert!(cosine(&a, &b) > cosine(&a, &c));
        assert!((cosine(&a, &a) - 1.0).abs() < 1e-4);
    }

    #[test]
    fn kalimat() {
        let s = sentences("Harga naik 3.5 persen. Apakah benar? Ya, benar!");
        assert_eq!(s.len(), 3);
        assert!(s[0].contains("3.5"));
    }
}

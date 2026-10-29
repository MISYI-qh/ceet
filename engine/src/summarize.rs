//! Ringkasan ekstraktif TextRank dan mode baca. Berjalan lokal, tanpa model AI, tanpa pelatihan.
//! Setiap kalimat ringkasan adalah kalimat asli dari halaman, sehingga tidak ada isi yang dikarang.

use crate::text::{is_stop, sentences, stem, words};
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct Summary {
    pub sentences: Vec<String>,
    pub keywords: Vec<String>,
    pub reading_minutes: u32,
}

pub fn summarize(text: &str, max_sentences: usize) -> Summary {
    let sents = sentences(text);
    let n = sents.len();
    let wc = words(text).len();
    let reading_minutes = ((wc as f32 / 220.0).ceil() as u32).max(1);
    let sets: Vec<HashMap<String, u32>> = sents
        .iter()
        .map(|s| {
            let mut m = HashMap::new();
            for w in words(s) {
                if !is_stop(&w) && w.chars().count() > 2 {
                    *m.entry(stem(&w)).or_insert(0) += 1;
                }
            }
            m
        })
        .collect();

    // Kata kunci: frekuensi tertinggi di seluruh dokumen.
    let mut freq: HashMap<String, u32> = HashMap::new();
    for m in &sets {
        for (k, v) in m {
            *freq.entry(k.clone()).or_insert(0) += v;
        }
    }
    let mut kw: Vec<(String, u32)> = freq.into_iter().filter(|(_, c)| *c >= 2).collect();
    kw.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
    let keywords: Vec<String> = kw.into_iter().take(6).map(|(k, _)| k).collect();

    if n <= max_sentences {
        return Summary { sentences: sents, keywords, reading_minutes };
    }

    // Graf kemiripan antar kalimat (irisan kata dinormalkan dengan panjang).
    let mut w = vec![vec![0f32; n]; n];
    for i in 0..n {
        for j in (i + 1)..n {
            let common = sets[i].keys().filter(|k| sets[j].contains_key(*k)).count() as f32;
            if common > 0.0 {
                let denom = ((sets[i].len().max(1) as f32).ln() + 1.0) + ((sets[j].len().max(1) as f32).ln() + 1.0);
                let s = common / denom;
                w[i][j] = s;
                w[j][i] = s;
            }
        }
    }
    let out_sum: Vec<f32> = (0..n).map(|i| w[i].iter().sum::<f32>()).collect();
    let d = 0.85f32;
    let mut score = vec![1.0f32 / n as f32; n];
    for _ in 0..40 {
        let mut next = vec![(1.0 - d) / n as f32; n];
        for i in 0..n {
            for j in 0..n {
                if w[j][i] > 0.0 && out_sum[j] > 0.0 {
                    next[i] += d * score[j] * w[j][i] / out_sum[j];
                }
            }
        }
        let delta: f32 = next.iter().zip(&score).map(|(a, b)| (a - b).abs()).sum();
        score = next;
        if delta < 1e-6 {
            break;
        }
    }
    // Kalimat awal sedikit diunggulkan, kalimat sangat pendek atau sangat panjang dikurangi.
    for i in 0..n {
        let len = sents[i].chars().count();
        let pos = 1.0 + 0.25 * (1.0 - i as f32 / n as f32);
        let lenf = if !(40..=320).contains(&len) { 0.6 } else { 1.0 };
        score[i] *= pos * lenf;
    }
    let mut idx: Vec<usize> = (0..n).collect();
    idx.sort_by(|a, b| score[*b].partial_cmp(&score[*a]).unwrap_or(std::cmp::Ordering::Equal));
    // Pilih tanpa redundansi: lewati kalimat yang terlalu mirip dengan yang sudah dipilih.
    let mut chosen: Vec<usize> = Vec::new();
    for i in idx {
        if chosen.len() >= max_sentences {
            break;
        }
        let redundant = chosen.iter().any(|&c| {
            let common = sets[i].keys().filter(|k| sets[c].contains_key(*k)).count() as f32;
            let small = sets[i].len().min(sets[c].len()).max(1) as f32;
            common / small > 0.7
        });
        if !redundant {
            chosen.push(i);
        }
    }
    chosen.sort();
    Summary { sentences: chosen.into_iter().map(|i| sents[i].clone()).collect(), keywords, reading_minutes }
}

/// Mode baca: ambil teks utama dari HTML dengan memberi skor blok berdasarkan kepadatan teks.
pub fn extract_readable(html: &str) -> (String, String) {
    let cleaned = strip_blocks(html, &["script", "style", "noscript", "svg", "nav", "footer", "aside", "form", "iframe", "header"]);
    let title = tag_text(&cleaned, "title").or_else(|| tag_text(&cleaned, "h1")).unwrap_or_default();
    let mut paras: Vec<(String, f32)> = Vec::new();
    let lower = cleaned.to_ascii_lowercase();
    let mut pos = 0;
    while let Some(s) = lower[pos..].find("<p") {
        let start = pos + s;
        let after = lower.as_bytes().get(start + 2).copied().unwrap_or(b' ');
        if !(after == b'>' || after == b' ') {
            pos = start + 2;
            continue;
        }
        let open_end = match lower[start..].find('>') {
            Some(e) => start + e + 1,
            None => break,
        };
        let close = lower[open_end..].find("</p>").map(|e| open_end + e).unwrap_or(lower.len());
        let raw = &cleaned[open_end..close];
        let link_chars: usize = raw_link_len(raw);
        let text = decode_entities(&strip_tags(raw));
        let t = text.split_whitespace().collect::<Vec<_>>().join(" ");
        let len = t.chars().count();
        if len >= 40 {
            let link_ratio = link_chars as f32 / raw.len().max(1) as f32;
            if link_ratio < 0.5 {
                paras.push((t, len as f32 * (1.0 - link_ratio)));
            }
        }
        pos = close.max(start + 2);
        if pos >= lower.len() {
            break;
        }
    }
    let body = paras.into_iter().map(|(t, _)| t).collect::<Vec<_>>().join("\n\n");
    (decode_entities(title.trim()), body)
}

fn raw_link_len(raw: &str) -> usize {
    let l = raw.to_ascii_lowercase();
    let mut total = 0;
    let mut pos = 0;
    while let Some(s) = l[pos..].find("<a") {
        let st = pos + s;
        let e = l[st..].find("</a>").map(|e| st + e + 4).unwrap_or(l.len());
        total += e - st;
        pos = e;
        if pos >= l.len() {
            break;
        }
    }
    total
}

fn strip_blocks(html: &str, tags: &[&str]) -> String {
    let mut s = html.to_string();
    for t in tags {
        loop {
            let low = s.to_ascii_lowercase();
            let open = format!("<{}", t);
            let Some(a) = low.find(&open) else { break };
            let ch = low.as_bytes().get(a + open.len()).copied().unwrap_or(b'>');
            if !(ch == b'>' || ch == b' ' || ch == b'\n' || ch == b'/') {
                // Tag lain berawalan sama, misalnya <nav-x>. Lewati dengan menandai.
                s.replace_range(a..a + 1, " ");
                continue;
            }
            let close = format!("</{}>", t);
            let end = match low[a..].find(&close) {
                Some(e) => a + e + close.len(),
                None => low[a..].find('>').map(|e| a + e + 1).unwrap_or(low.len()),
            };
            s.replace_range(a..end, " ");
        }
    }
    s
}

fn strip_tags(s: &str) -> String {
    let mut out = String::new();
    let mut in_tag = false;
    for c in s.chars() {
        match c {
            '<' => in_tag = true,
            '>' if in_tag => {
                in_tag = false;
                out.push(' ');
            }
            c if !in_tag => out.push(c),
            _ => {}
        }
    }
    out
}

fn tag_text(html: &str, tag: &str) -> Option<String> {
    let low = html.to_ascii_lowercase();
    let a = low.find(&format!("<{}", tag))?;
    let s = low[a..].find('>')? + a + 1;
    let e = low[s..].find(&format!("</{}>", tag))? + s;
    Some(strip_tags(&html[s..e]).split_whitespace().collect::<Vec<_>>().join(" "))
}

fn decode_entities(s: &str) -> String {
    s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&ldquo;", "\"").replace("&rdquo;", "\"")
        .replace("&ndash;", "-").replace("&mdash;", "-")
}

#[cfg(test)]
mod tests {
    use super::*;

    const ARTIKEL: &str = "Kopi Indonesia dikenal di seluruh dunia karena ragam cita rasanya. \
        Kopi Gayo dari Aceh punya aroma rempah dan tingkat keasaman yang rendah. \
        Petani kopi di Toraja menanam kopi di dataran tinggi yang sejuk. \
        Cuaca hari ini cerah di sebagian besar wilayah. \
        Kopi dari Flores memiliki rasa cokelat yang khas dan banyak dicari pembeli luar negeri. \
        Ekspor kopi Indonesia meningkat setiap tahun karena permintaan global yang kuat. \
        Beberapa kafe di kota besar kini menyajikan kopi dari petani lokal secara langsung.";

    #[test]
    fn ringkasan_memakai_kalimat_asli() {
        let s = summarize(ARTIKEL, 3);
        assert_eq!(s.sentences.len(), 3);
        for k in &s.sentences {
            assert!(ARTIKEL.contains(k.as_str()));
        }
        assert!(!s.sentences.iter().any(|k| k.contains("Cuaca hari ini")));
        assert!(s.keywords.contains(&"kopi".to_string()));
    }

    #[test]
    fn teks_pendek_dikembalikan_utuh() {
        let s = summarize("Satu kalimat saja di sini.", 3);
        assert_eq!(s.sentences.len(), 1);
        assert_eq!(s.reading_minutes, 1);
    }

    #[test]
    fn mode_baca_membuang_sampah() {
        let html = r#"<html><head><title>Judul Berita &amp; Kopi</title><script>var x=1;</script></head>
        <body><nav><p>Menu menu menu menu menu menu menu menu menu menu menu</p></nav>
        <article><p>Ini adalah paragraf pertama yang cukup panjang untuk dianggap isi utama artikel.</p>
        <p><a href="/a">tautan tautan tautan tautan tautan tautan tautan tautan</a></p>
        <p>Paragraf kedua juga panjang dan berisi informasi penting tentang topik yang dibahas &mdash; lengkap.</p></article>
        <footer><p>Hak cipta semua pihak dilindungi undang-undang yang berlaku di negara ini.</p></footer></body></html>"#;
        let (t, b) = extract_readable(html);
        assert_eq!(t, "Judul Berita & Kopi");
        assert!(b.contains("paragraf pertama"));
        assert!(b.contains("paragraf kedua") || b.contains("Paragraf kedua"));
        assert!(!b.contains("Menu menu"));
        assert!(!b.contains("Hak cipta"));
        assert!(!b.contains("tautan tautan"));
    }
}

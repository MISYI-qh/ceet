//! Pengurai URL ringan dan pembersih pelacak.

#[derive(Debug, Clone, PartialEq)]
pub struct Url {
    pub scheme: String,
    pub userinfo: bool,
    pub host: String,
    pub port: Option<u16>,
    pub path: String,
    pub query: Vec<(String, String)>,
    pub fragment: String,
}

/// Parameter pelacak yang aman dibuang. Parameter fungsional (q, id, v, page) tidak pernah disentuh.
const TRACKING_PARAMS: &[&str] = &[
    "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
    "mc_cid", "mc_eid", "igshid", "igsh", "_hsenc", "_hsmi", "hsctatracking", "mkt_tok",
    "vero_id", "wickedid", "oly_enc_id", "oly_anon_id", "rb_clickid", "s_cid", "ref_src",
    "ref_url", "spm", "scm", "cmpid", "ncid", "sr_share", "si", "feature", "pp",
];
const TRACKING_PREFIXES: &[&str] = &["utm_", "pk_", "mtm_", "matomo_", "hsa_", "ga_", "itm_", "_ga"];

pub fn percent_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        if b[i] == b'%' && i + 2 < b.len() {
            let h = std::str::from_utf8(&b[i + 1..i + 3]).ok().and_then(|t| u8::from_str_radix(t, 16).ok());
            if let Some(v) = h {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(if b[i] == b'+' { b' ' } else { b[i] });
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn percent_encode(s: &str) -> String {
    let mut out = String::new();
    for &c in s.as_bytes() {
        if c.is_ascii_alphanumeric() || matches!(c, b'-' | b'_' | b'.' | b'~') {
            out.push(c as char);
        } else {
            out.push_str(&format!("%{:02X}", c));
        }
    }
    out
}

impl Url {
    pub fn parse(input: &str) -> Option<Url> {
        let input = input.trim();
        let (scheme, rest) = input.split_once("://")?;
        if scheme.is_empty() || !scheme.chars().all(|c| c.is_ascii_alphanumeric() || c == '+' || c == '-' || c == '.') {
            return None;
        }
        let (rest, fragment) = match rest.split_once('#') {
            Some((a, b)) => (a, b.to_string()),
            None => (rest, String::new()),
        };
        let (rest, query_s) = match rest.split_once('?') {
            Some((a, b)) => (a, b),
            None => (rest, ""),
        };
        let (authority, path) = match rest.find('/') {
            Some(i) => (&rest[..i], rest[i..].to_string()),
            None => (rest, "/".to_string()),
        };
        let (userinfo, hostport) = match authority.rsplit_once('@') {
            Some((_, h)) => (true, h),
            None => (false, authority),
        };
        let (host, port) = if hostport.starts_with('[') {
            let end = hostport.find(']')?;
            let port = hostport[end + 1..].strip_prefix(':').and_then(|p| p.parse().ok());
            (hostport[..=end].to_string(), port)
        } else {
            match hostport.rsplit_once(':') {
                Some((h, p)) if p.chars().all(|c| c.is_ascii_digit()) && !p.is_empty() => (h.to_string(), p.parse().ok()),
                _ => (hostport.to_string(), None),
            }
        };
        if host.is_empty() {
            return None;
        }
        let query = query_s
            .split('&')
            .filter(|p| !p.is_empty())
            .map(|p| match p.split_once('=') {
                Some((k, v)) => (k.to_string(), v.to_string()),
                None => (p.to_string(), String::new()),
            })
            .collect();
        Some(Url {
            scheme: scheme.to_ascii_lowercase(),
            userinfo,
            host: host.to_ascii_lowercase(),
            port,
            path,
            query,
            fragment,
        })
    }

    pub fn is_ip_host(&self) -> bool {
        if self.host.starts_with('[') {
            return true;
        }
        let parts: Vec<&str> = self.host.split('.').collect();
        parts.len() == 4 && parts.iter().all(|p| !p.is_empty() && p.len() <= 3 && p.chars().all(|c| c.is_ascii_digit()))
    }

    pub fn is_local_host(&self) -> bool {
        let h = self.host.as_str();
        if h == "localhost" || h.ends_with(".local") || h.ends_with(".localhost") || h == "[::1]" {
            return true;
        }
        if self.is_ip_host() && !self.host.starts_with('[') {
            let p: Vec<u32> = h.split('.').filter_map(|x| x.parse().ok()).collect();
            if p.len() == 4 {
                return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && (16..32).contains(&p[1]));
            }
        }
        false
    }

    /// Domain terdaftar (eTLD+1) dengan daftar akhiran ganda yang umum, termasuk Indonesia.
    pub fn registrable_domain(&self) -> String {
        registrable_domain(&self.host)
    }

    pub fn to_string(&self) -> String {
        let mut s = format!("{}://{}", self.scheme, self.host);
        if let Some(p) = self.port {
            s.push_str(&format!(":{}", p));
        }
        s.push_str(&self.path);
        if !self.query.is_empty() {
            s.push('?');
            let q: Vec<String> = self
                .query
                .iter()
                .map(|(k, v)| if v.is_empty() { k.clone() } else { format!("{}={}", k, v) })
                .collect();
            s.push_str(&q.join("&"));
        }
        if !self.fragment.is_empty() {
            s.push('#');
            s.push_str(&self.fragment);
        }
        s
    }

    /// Buang parameter pelacak. Mengembalikan URL bersih dan nama parameter yang dibuang.
    pub fn strip_tracking(&self) -> (Url, Vec<String>) {
        let mut removed = Vec::new();
        let mut kept = Vec::new();
        for (k, v) in &self.query {
            let lk = k.to_ascii_lowercase();
            let bad = TRACKING_PARAMS.contains(&lk.as_str()) || TRACKING_PREFIXES.iter().any(|p| lk.starts_with(p));
            if bad {
                removed.push(k.clone());
            } else {
                kept.push((k.clone(), v.clone()));
            }
        }
        let mut u = self.clone();
        u.query = kept;
        (u, removed)
    }
}

const MULTI_SUFFIX: &[&str] = &[
    "co.id", "or.id", "ac.id", "go.id", "sch.id", "web.id", "my.id", "biz.id", "net.id", "mil.id", "desa.id",
    "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "co.in", "co.nz", "com.sg",
    "com.my", "com.br", "com.cn", "com.hk", "com.tw", "co.kr", "co.za", "com.mx", "com.tr",
];

pub fn registrable_domain(host: &str) -> String {
    let labels: Vec<&str> = host.split('.').collect();
    if labels.len() <= 2 {
        return host.to_string();
    }
    let last2 = labels[labels.len() - 2..].join(".");
    if MULTI_SUFFIX.contains(&last2.as_str()) && labels.len() >= 3 {
        return labels[labels.len() - 3..].join(".");
    }
    last2
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_dasar() {
        let u = Url::parse("HTTPS://User@Www.Contoh.co.id:8443/a/b?x=1&utm_source=z#top").unwrap();
        assert_eq!(u.scheme, "https");
        assert!(u.userinfo);
        assert_eq!(u.host, "www.contoh.co.id");
        assert_eq!(u.port, Some(8443));
        assert_eq!(u.path, "/a/b");
        assert_eq!(u.fragment, "top");
        assert_eq!(u.registrable_domain(), "contoh.co.id");
    }

    #[test]
    fn bersihkan_pelacak() {
        let u = Url::parse("https://toko.id/p?id=7&utm_campaign=a&fbclid=xyz&_ga=1&warna=biru").unwrap();
        let (c, removed) = u.strip_tracking();
        assert_eq!(c.to_string(), "https://toko.id/p?id=7&warna=biru");
        assert_eq!(removed.len(), 3);
    }

    #[test]
    fn host_lokal_dan_ip() {
        assert!(Url::parse("http://192.168.1.2/x").unwrap().is_local_host());
        assert!(!Url::parse("http://8.8.8.8/").unwrap().is_local_host());
        assert!(Url::parse("http://8.8.8.8/").unwrap().is_ip_host());
    }

    #[test]
    fn percent_bolak_balik() {
        let s = "cari kopi & teh?";
        assert_eq!(percent_decode(&percent_encode(s)), s);
    }
}

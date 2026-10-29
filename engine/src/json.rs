//! JSON minimal tanpa dependensi (cukup untuk protokol mesin Ceet).

use std::fmt::Write as _;

#[derive(Debug, Clone, PartialEq)]
pub enum Json {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<Json>),
    Obj(Vec<(String, Json)>),
}

impl Json {
    pub fn get(&self, key: &str) -> Option<&Json> {
        match self {
            Json::Obj(items) => items.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }
    pub fn as_str(&self) -> Option<&str> {
        if let Json::Str(s) = self { Some(s) } else { None }
    }
    pub fn as_f64(&self) -> Option<f64> {
        if let Json::Num(n) = self { Some(*n) } else { None }
    }
    pub fn as_bool(&self) -> Option<bool> {
        if let Json::Bool(b) = self { Some(*b) } else { None }
    }
    pub fn as_arr(&self) -> Option<&[Json]> {
        if let Json::Arr(a) = self { Some(a) } else { None }
    }
    pub fn str_of(&self, key: &str) -> Option<&str> {
        self.get(key).and_then(Json::as_str)
    }
    pub fn num_of(&self, key: &str) -> Option<f64> {
        self.get(key).and_then(Json::as_f64)
    }
    pub fn is_null(&self) -> bool {
        matches!(self, Json::Null)
    }

    pub fn obj(items: Vec<(&str, Json)>) -> Json {
        Json::Obj(items.into_iter().map(|(k, v)| (k.to_string(), v)).collect())
    }
    pub fn str(s: impl Into<String>) -> Json {
        Json::Str(s.into())
    }
    pub fn num(n: impl Into<f64>) -> Json {
        Json::Num(n.into())
    }
    pub fn opt_str(s: Option<String>) -> Json {
        match s { Some(s) => Json::Str(s), None => Json::Null }
    }
    pub fn str_arr(items: &[String]) -> Json {
        Json::Arr(items.iter().map(|s| Json::Str(s.clone())).collect())
    }

    pub fn parse(src: &str) -> Result<Json, String> {
        let mut p = Parser { s: src.as_bytes(), i: 0, depth: 0 };
        p.ws();
        let v = p.value()?;
        p.ws();
        if p.i != p.s.len() {
            return Err(format!("karakter berlebih di posisi {}", p.i));
        }
        Ok(v)
    }

    pub fn stringify(&self) -> String {
        let mut out = String::new();
        self.write(&mut out);
        out
    }

    fn write(&self, out: &mut String) {
        match self {
            Json::Null => out.push_str("null"),
            Json::Bool(b) => out.push_str(if *b { "true" } else { "false" }),
            Json::Num(n) => {
                if !n.is_finite() {
                    out.push_str("null");
                } else if n.fract() == 0.0 && n.abs() < 9.0e15 {
                    let _ = write!(out, "{}", *n as i64);
                } else {
                    let _ = write!(out, "{}", n);
                }
            }
            Json::Str(s) => write_str(s, out),
            Json::Arr(a) => {
                out.push('[');
                for (i, v) in a.iter().enumerate() {
                    if i > 0 { out.push(','); }
                    v.write(out);
                }
                out.push(']');
            }
            Json::Obj(o) => {
                out.push('{');
                for (i, (k, v)) in o.iter().enumerate() {
                    if i > 0 { out.push(','); }
                    write_str(k, out);
                    out.push(':');
                    v.write(out);
                }
                out.push('}');
            }
        }
    }
}

fn write_str(s: &str, out: &mut String) {
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
}

struct Parser<'a> {
    s: &'a [u8],
    i: usize,
    depth: usize,
}

impl<'a> Parser<'a> {
    fn ws(&mut self) {
        while self.i < self.s.len() && matches!(self.s[self.i], b' ' | b'\n' | b'\r' | b'\t') {
            self.i += 1;
        }
    }
    fn peek(&self) -> Option<u8> {
        self.s.get(self.i).copied()
    }
    fn expect(&mut self, c: u8) -> Result<(), String> {
        if self.peek() == Some(c) {
            self.i += 1;
            Ok(())
        } else {
            Err(format!("diharapkan '{}' di posisi {}", c as char, self.i))
        }
    }
    fn lit(&mut self, word: &str, v: Json) -> Result<Json, String> {
        if self.s[self.i..].starts_with(word.as_bytes()) {
            self.i += word.len();
            Ok(v)
        } else {
            Err(format!("token tidak dikenal di posisi {}", self.i))
        }
    }
    fn value(&mut self) -> Result<Json, String> {
        match self.peek() {
            None => Err("JSON berakhir terlalu cepat".into()),
            Some(b'n') => self.lit("null", Json::Null),
            Some(b't') => self.lit("true", Json::Bool(true)),
            Some(b'f') => self.lit("false", Json::Bool(false)),
            Some(b'"') => Ok(Json::Str(self.string()?)),
            Some(b'[') => {
                self.enter()?;
                self.i += 1;
                let mut a = Vec::new();
                self.ws();
                if self.peek() == Some(b']') {
                    self.i += 1;
                } else {
                    loop {
                        self.ws();
                        a.push(self.value()?);
                        self.ws();
                        match self.peek() {
                            Some(b',') => self.i += 1,
                            Some(b']') => { self.i += 1; break; }
                            _ => return Err(format!("array tidak valid di posisi {}", self.i)),
                        }
                    }
                }
                self.depth -= 1;
                Ok(Json::Arr(a))
            }
            Some(b'{') => {
                self.enter()?;
                self.i += 1;
                let mut o = Vec::new();
                self.ws();
                if self.peek() == Some(b'}') {
                    self.i += 1;
                } else {
                    loop {
                        self.ws();
                        let k = self.string()?;
                        self.ws();
                        self.expect(b':')?;
                        self.ws();
                        let v = self.value()?;
                        o.push((k, v));
                        self.ws();
                        match self.peek() {
                            Some(b',') => self.i += 1,
                            Some(b'}') => { self.i += 1; break; }
                            _ => return Err(format!("objek tidak valid di posisi {}", self.i)),
                        }
                    }
                }
                self.depth -= 1;
                Ok(Json::Obj(o))
            }
            Some(c) if c == b'-' || c.is_ascii_digit() => self.number(),
            Some(c) => Err(format!("karakter tak terduga '{}' di posisi {}", c as char, self.i)),
        }
    }
    fn enter(&mut self) -> Result<(), String> {
        self.depth += 1;
        if self.depth > 64 { Err("JSON terlalu dalam".into()) } else { Ok(()) }
    }
    fn number(&mut self) -> Result<Json, String> {
        let start = self.i;
        if self.peek() == Some(b'-') { self.i += 1; }
        while let Some(c) = self.peek() {
            if c.is_ascii_digit() || matches!(c, b'.' | b'e' | b'E' | b'+' | b'-') { self.i += 1; } else { break; }
        }
        let t = std::str::from_utf8(&self.s[start..self.i]).map_err(|e| e.to_string())?;
        t.parse::<f64>().map(Json::Num).map_err(|_| format!("angka tidak valid '{}'", t))
    }
    fn hex4(&mut self) -> Result<u32, String> {
        if self.i + 4 > self.s.len() { return Err("escape \\u terpotong".into()); }
        let t = std::str::from_utf8(&self.s[self.i..self.i + 4]).map_err(|e| e.to_string())?;
        let v = u32::from_str_radix(t, 16).map_err(|_| "escape \\u tidak valid".to_string())?;
        self.i += 4;
        Ok(v)
    }
    fn string(&mut self) -> Result<String, String> {
        self.expect(b'"')?;
        let mut buf: Vec<u8> = Vec::new();
        loop {
            let c = self.peek().ok_or("string tidak ditutup")?;
            self.i += 1;
            match c {
                b'"' => break,
                b'\\' => {
                    let e = self.peek().ok_or("escape terpotong")?;
                    self.i += 1;
                    match e {
                        b'"' => buf.push(b'"'),
                        b'\\' => buf.push(b'\\'),
                        b'/' => buf.push(b'/'),
                        b'b' => buf.push(8),
                        b'f' => buf.push(12),
                        b'n' => buf.push(b'\n'),
                        b'r' => buf.push(b'\r'),
                        b't' => buf.push(b'\t'),
                        b'u' => {
                            let mut cp = self.hex4()?;
                            if (0xD800..0xDC00).contains(&cp) {
                                if self.s[self.i..].starts_with(b"\\u") {
                                    self.i += 2;
                                    let lo = self.hex4()?;
                                    if (0xDC00..0xE000).contains(&lo) {
                                        cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                                    } else {
                                        cp = 0xFFFD;
                                    }
                                } else {
                                    cp = 0xFFFD;
                                }
                            }
                            let ch = char::from_u32(cp).unwrap_or('\u{FFFD}');
                            let mut tmp = [0u8; 4];
                            buf.extend_from_slice(ch.encode_utf8(&mut tmp).as_bytes());
                        }
                        _ => return Err("escape tidak dikenal".into()),
                    }
                }
                c => buf.push(c),
            }
        }
        String::from_utf8(buf).map_err(|_| "UTF-8 tidak valid".to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_dan_stringify() {
        let j = Json::parse(r#"{"a":1,"b":[true,null,"x\ny"],"c":{"d":-2.5}}"#).unwrap();
        assert_eq!(j.num_of("a"), Some(1.0));
        assert_eq!(j.get("c").unwrap().num_of("d"), Some(-2.5));
        let s = j.stringify();
        assert_eq!(Json::parse(&s).unwrap(), j);
    }

    #[test]
    fn unicode_dan_surrogate() {
        let j = Json::parse(r#""é😀""#).unwrap();
        assert_eq!(j.as_str(), Some("é😀"));
    }

    #[test]
    fn menolak_json_rusak() {
        assert!(Json::parse("{").is_err());
        assert!(Json::parse("[1,]").is_err());
        assert!(Json::parse("{\"a\":1} x").is_err());
    }
}

//! Protokol JSON satu baris per perintah. Dipakai desktop (proses anak) dan Android (JNI/proses).
//! Permintaan: {"id":1,"cmd":"classify","input":"kopi"}  Jawaban: {"id":1,"ok":true,"data":{...}}

use crate::json::Json;
use crate::omnibox::{classify, resolve, Intent};
use crate::phishing::{assess, Risk};
use crate::privacy::{report, upgrade_https, Blocker, Decision};
use crate::search::{Doc, Index};
use crate::summarize::{extract_readable, summarize};
use crate::url::Url;

pub struct Engine {
    blocker: Blocker,
    index: Index,
}

impl Engine {
    pub fn new() -> Engine {
        Engine { blocker: Blocker::builtin(), index: Index::new() }
    }

    /// Satu baris masuk, satu baris keluar. Tidak pernah panik karena masukan buruk.
    pub fn handle_line(&mut self, line: &str) -> String {
        let req = match Json::parse(line) {
            Ok(j) => j,
            Err(e) => return err_json(Json::Null, &format!("JSON tidak valid: {}", e)),
        };
        let id = req.get("id").cloned().unwrap_or(Json::Null);
        match self.dispatch(&req) {
            Ok(data) => Json::obj(vec![("id", id), ("ok", Json::Bool(true)), ("data", data)]).stringify(),
            Err(e) => err_json(id, &e),
        }
    }

    fn dispatch(&mut self, r: &Json) -> Result<Json, String> {
        let cmd = r.str_of("cmd").ok_or("perintah 'cmd' tidak ada")?;
        let s = |k: &str| r.str_of(k).ok_or(format!("kolom '{}' wajib diisi", k));
        match cmd {
            "ping" => Ok(Json::obj(vec![("engine", Json::str("ceet")), ("version", Json::str(crate::VERSION)), ("blocklist", Json::num(self.blocker.len() as f64))])),
            "classify" => {
                let input = s("input")?;
                let tpl = r.str_of("search").unwrap_or("https://duckduckgo.com/?q={q}");
                let intent = classify(input);
                let (kind, value, arg) = match &intent {
                    Intent::Url { url } => ("url", url.clone(), String::new()),
                    Intent::Search { query } => ("search", query.clone(), String::new()),
                    Intent::Question { query } => ("question", query.clone(), String::new()),
                    Intent::Command { name, arg } => ("command", name.clone(), arg.clone()),
                    Intent::Empty => ("empty", String::new(), String::new()),
                };
                Ok(Json::obj(vec![
                    ("kind", Json::str(kind)),
                    ("value", Json::str(value)),
                    ("arg", Json::str(arg)),
                    ("target", Json::opt_str(resolve(input, tpl))),
                ]))
            }
            "clean_url" => {
                let url = s("url")?;
                let (up, upgraded) = upgrade_https(url);
                match Url::parse(&up) {
                    Some(u) => {
                        let (c, removed) = u.strip_tracking();
                        Ok(Json::obj(vec![
                            ("url", Json::str(c.to_string())),
                            ("upgraded", Json::Bool(upgraded)),
                            ("removed", Json::str_arr(&removed)),
                        ]))
                    }
                    None => Ok(Json::obj(vec![("url", Json::str(url)), ("upgraded", Json::Bool(false)), ("removed", Json::Arr(vec![]))])),
                }
            }
            "block_check" => {
                let d = self.blocker.check(s("host")?, s("page")?);
                Ok(match d {
                    Decision::Allow => Json::obj(vec![("block", Json::Bool(false))]),
                    Decision::Block { rule } => Json::obj(vec![("block", Json::Bool(true)), ("rule", Json::str(rule))]),
                })
            }
            "block_batch" => {
                let page = s("page")?;
                let hosts = r.get("hosts").and_then(Json::as_arr).ok_or("kolom 'hosts' wajib berupa array")?;
                let out: Vec<Json> = hosts
                    .iter()
                    .map(|h| Json::Bool(matches!(h.as_str().map(|h| self.blocker.check(h, page)), Some(Decision::Block { .. }))))
                    .collect();
                Ok(Json::Arr(out))
            }
            "privacy_report" => {
                let hosts: Vec<String> = r.get("hosts").and_then(Json::as_arr).map(|a| a.iter().filter_map(|h| h.as_str().map(String::from)).collect()).unwrap_or_default();
                let rep = report(&self.blocker, s("url")?, &hosts);
                Ok(Json::obj(vec![
                    ("blocked", Json::num(rep.blocked as f64)),
                    ("trackers", Json::str_arr(&rep.trackers)),
                    ("params_stripped", Json::num(rep.params_stripped as f64)),
                    ("upgraded", Json::Bool(rep.upgraded)),
                    ("secure", Json::Bool(rep.secure)),
                    ("score", Json::num(rep.score as f64)),
                    ("grade", Json::str(rep.grade)),
                ]))
            }
            "phishing" => {
                let a = assess(s("url")?);
                let risk = match a.risk {
                    Risk::Aman => "aman",
                    Risk::Waspada => "waspada",
                    Risk::Berbahaya => "berbahaya",
                };
                Ok(Json::obj(vec![("risk", Json::str(risk)), ("score", Json::num(a.score as f64)), ("reasons", Json::str_arr(&a.reasons))]))
            }
            "index_add" => {
                let doc = Doc {
                    id: s("docid")?.to_string(),
                    url: s("url")?.to_string(),
                    title: r.str_of("title").unwrap_or("").to_string(),
                    body: r.str_of("body").unwrap_or("").to_string(),
                    visits: r.num_of("visits").unwrap_or(1.0).max(0.0) as u32,
                    last_visit: r.num_of("last_visit").unwrap_or(0.0).max(0.0) as u64,
                };
                self.index.add(doc);
                Ok(Json::obj(vec![("size", Json::num(self.index.len() as f64))]))
            }
            "index_remove" => Ok(Json::obj(vec![("removed", Json::Bool(self.index.remove(s("docid")?)))])),
            "search" | "suggest" => {
                let q = s("q")?;
                let now = r.num_of("now").unwrap_or(0.0) as u64;
                let limit = r.num_of("limit").unwrap_or(8.0).clamp(1.0, 50.0) as usize;
                let hits = if cmd == "search" { self.index.search(q, now, limit) } else { self.index.suggest(q, now, limit) };
                Ok(Json::Arr(
                    hits.into_iter()
                        .map(|h| Json::obj(vec![("id", Json::str(h.id)), ("score", Json::num(h.score as f64)), ("why", Json::str_arr(&h.why))]))
                        .collect(),
                ))
            }
            "summarize" => {
                let n = r.num_of("sentences").unwrap_or(3.0).clamp(1.0, 12.0) as usize;
                let sm = summarize(s("text")?, n);
                Ok(Json::obj(vec![
                    ("sentences", Json::str_arr(&sm.sentences)),
                    ("keywords", Json::str_arr(&sm.keywords)),
                    ("reading_minutes", Json::num(sm.reading_minutes as f64)),
                ]))
            }
            "readable" => {
                let (title, body) = extract_readable(s("html")?);
                Ok(Json::obj(vec![("title", Json::str(title)), ("body", Json::str(body))]))
            }
            other => Err(format!("perintah tidak dikenal: {}", other)),
        }
    }
}

impl Default for Engine {
    fn default() -> Self {
        Engine::new()
    }
}

fn err_json(id: Json, msg: &str) -> String {
    Json::obj(vec![("id", id), ("ok", Json::Bool(false)), ("error", Json::str(msg))]).stringify()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn call(e: &mut Engine, line: &str) -> Json {
        Json::parse(&e.handle_line(line)).unwrap()
    }

    #[test]
    fn ping_dan_classify() {
        let mut e = Engine::new();
        let p = call(&mut e, r#"{"id":1,"cmd":"ping"}"#);
        assert_eq!(p.get("ok").unwrap().as_bool(), Some(true));
        let c = call(&mut e, r#"{"id":2,"cmd":"classify","input":"kompas.com"}"#);
        assert_eq!(c.get("data").unwrap().str_of("kind"), Some("url"));
    }

    #[test]
    fn alur_indeks_dan_cari() {
        let mut e = Engine::new();
        call(&mut e, r#"{"id":1,"cmd":"index_add","docid":"a","url":"https://masak.id/soto","title":"Resep soto ayam","body":"kuah kuning","visits":2,"last_visit":100}"#);
        let r = call(&mut e, r#"{"id":2,"cmd":"search","q":"soto","now":200}"#);
        let first = &r.get("data").unwrap().as_arr().unwrap()[0];
        assert_eq!(first.str_of("id"), Some("a"));
    }

    #[test]
    fn galat_tidak_panik() {
        let mut e = Engine::new();
        assert_eq!(call(&mut e, "bukan json").get("ok").unwrap().as_bool(), Some(false));
        assert_eq!(call(&mut e, r#"{"id":3,"cmd":"xyz"}"#).get("ok").unwrap().as_bool(), Some(false));
        assert_eq!(call(&mut e, r#"{"id":4,"cmd":"classify"}"#).get("ok").unwrap().as_bool(), Some(false));
    }

    #[test]
    fn phishing_lewat_protokol() {
        let mut e = Engine::new();
        let r = call(&mut e, r#"{"id":5,"cmd":"phishing","url":"https://paypa1.com/login"}"#);
        assert_eq!(r.get("data").unwrap().str_of("risk"), Some("berbahaya"));
    }
}

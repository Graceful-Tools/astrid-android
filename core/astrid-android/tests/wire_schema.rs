//! The pinned core's wire schema, committed where the app's JVM tests read it (AATD-94).
//!
//! The Kotlin side cannot call `astrid_core::app::schema` itself, so `WireSchemaContractTest` reads
//! a copy from `app/src/test/resources/core-schema/`. This test is what makes that copy honest: it
//! fails when the copy is not what the pinned crate produces, so a pin bump that changes the wire
//! changes the copy in the same commit, and the Kotlin gate then judges the app against it.
//!
//! `UPDATE_SCHEMA=1 cargo test -p astrid-android --test wire_schema` rewrites the copy.

use std::path::PathBuf;

fn dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../app/src/test/resources/core-schema")
}

#[test]
fn the_committed_schema_is_the_pinned_cores_aatd_94() {
    let update = std::env::var_os("UPDATE_SCHEMA").is_some();
    let mut stale = Vec::new();
    for (name, document) in astrid_core::app::schema::documents() {
        let path = dir().join(name);
        let want = serde_json::to_string_pretty(&document).expect("a schema serialises") + "\n";
        if update {
            std::fs::create_dir_all(dir()).expect("the schema directory");
            std::fs::write(&path, &want).expect("write the schema");
        } else if std::fs::read_to_string(&path).ok().as_deref() != Some(want.as_str()) {
            stale.push(name);
        }
    }
    assert!(
        stale.is_empty(),
        "app/src/test/resources/core-schema is not the pinned core's schema ({}): run \
         `UPDATE_SCHEMA=1 cargo test -p astrid-android --test wire_schema` in core/ and commit it",
        stale.join(", ")
    );
}

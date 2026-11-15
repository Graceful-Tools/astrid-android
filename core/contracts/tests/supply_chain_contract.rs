//! `core/deny.toml` is the copy the pinned core generated for this repository (AATD-112).
//!
//! astrid-core renders every consumer's `deny.toml` from its own (`cargo xtask gen-deny`, task
//! `32d7c04d`) and carries the results in `astrid_core::supply_chain`, so this checks the copy
//! against the revision this workspace pins rather than against the core's `main`. A licence or
//! advisory decision reaches this repository when the pin is bumped, and this test says so.
//!
//! To take a new copy after a bump: `git show <rev>:supply-chain/deny.astrid-android.toml` from
//! astrid-core, written over `core/deny.toml`.

use std::path::PathBuf;

#[test]
fn deny_toml_is_the_core_it_pins_aatd_112() {
    // Not redundant with `matches`: an unknown repository key would make that compare against
    // nothing, and this test would pass forever.
    assert!(
        astrid_core::supply_chain::deny_toml("astrid-android").is_some(),
        "the pinned core generates no deny.toml for astrid-android"
    );
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../deny.toml");
    let committed = std::fs::read_to_string(&path).expect("core/deny.toml");
    assert!(
        astrid_core::supply_chain::matches("astrid-android", &committed),
        "core/deny.toml is not the pinned core's supply-chain/deny.astrid-android.toml: copy it \
         from that revision of astrid-core"
    );
}

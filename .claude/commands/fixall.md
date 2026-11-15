Check the **Ready** list on the Astrid Android To-do and autonomously work every task on it to
completion. Safe to re-run.

Follow [`../astrid-web/docs/FIXALL_WORKFLOW.md`](../../../astrid-web/docs/FIXALL_WORKFLOW.md) exactly —
the queue, board etiquette (Doing / Waiting / handing back), the per-task loop, and re-checking after
each task. Everything in [fixstuff.md](./fixstuff.md) → *What is different here* applies.

- Queue: `get_agent_queue { agent: "claude", listId: "85eef4ce-3d4d-4072-9ca7-bd631ca815b1",
  includeUnassigned: true }`. Two lists share the name "Astrid Android To-do", so use the id.
  `get_lists` should still list it. Claim with `cd ../astrid-web && npx tsx scripts/claim-fixall-task.ts
  <taskId> ready --agent claude --list <that id>`. The server has allowed this board since AATD-22.
- **The inbox is part of the run** (AATD-81). Answer `attention.tasks` and `attention.messages`
  even when `queue` is empty. The scheduled loop now starts a run for a reply alone. A reply that
  resumes a handback (`attention.tasks[].resume`) is claimed with `claim-fixall-task.ts <taskId>
  resume <commentWatermark> --agent claude --list <id> --resume-context '<resume JSON>'`
  (FIXALL_WORKFLOW.md → *A human reply resumes an explicit handback*).
- **Done = merged to `main` with `npm run predeploy` green on the merged tree.** Push once, at the
  end of the run, and say "on main", never "shipped".
- **Always ask before:** a Play upload, signing with the real key, deleting files.
- If every Ready task is blocked and the inbox is empty, say so and stop.

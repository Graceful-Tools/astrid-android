Check the **Ready** list on the Astrid Android To-do and autonomously work every task on it to
completion. Safe to re-run.

Follow [`../astrid-web/docs/FIXALL_WORKFLOW.md`](../../../astrid-web/docs/FIXALL_WORKFLOW.md) exactly —
the queue, board etiquette (Doing / Waiting / handing back), the per-task loop, and re-checking after
each task. Everything in [fixstuff.md](./fixstuff.md) → *What is different here* applies.

- Queue: `get_agent_queue { agent: "claude", listId: <"Astrid Android To-do" id> }` (resolve the id
  with `get_lists` each run). Claim with `cd ../astrid-web && npx tsx scripts/claim-fixall-task.ts`
  — the server allows this board since AATD-22.
- **Done = merged to `main` with `npm run predeploy` green on the merged tree.** Push once, at the
  end of the run, and say "on main", never "shipped".
- **Always ask before:** a Play upload, signing with the real key, deleting files.
- If every Ready task is blocked, say so and stop.

# Article source

The Medium article about this project, kept in the repository so it has a history
like everything else here.

- [`ticketflow-en.md`](ticketflow-en.md) — the article
- [`n8n-workflow.json`](n8n-workflow.json) — importable n8n flow

Deliberately outside `docs/`: that folder is documentation *of* the system, and this
is writing *about* it.

## What the automation can and cannot do

The flow watches `content/medium/*.md` on `main` and, when the article changes,
creates a **new draft** on Medium.

It cannot update the published post, and that is not a limitation of the flow:

> **The Medium API is no longer supported. We do not recommend using it.**
> — Medium's own API documentation

The Posts section offers exactly one operation, `POST /v1/users/{authorId}/posts`.
There is no `PUT`, no `PATCH`, no `DELETE`. Updating a published Medium post through
an API has never been possible.

So the honest shape is: the flow prepares a draft, and a human replaces and publishes.
Anything promising true sync to Medium is either driving a browser session or lying.

**If real sync matters more than the platform**, dev.to (Forem) and Hashnode both
expose an update endpoint, and the same flow retargets in one node.

## Setup

1. **Medium token** — `medium.com/me/settings/security` → Integration tokens.
   Medium stopped issuing new ones for some accounts; if the section is gone, the
   API path is closed for you and dev.to/Hashnode is the way.
2. **n8n credential** — Header Auth: `Authorization` = `Bearer <token>`.
3. **GitHub credential** — a token with `repo` scope for the trigger.
4. Import `n8n-workflow.json`, attach both credentials, activate.

## Untested

This flow has not been executed. The n8n MCP connector was not authorised when it was
written, so it was built from the API contracts rather than from a run. Expect to fix
node versions on import, and check the first execution before trusting it.

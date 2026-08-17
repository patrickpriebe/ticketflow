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

## The flow cannot run, and that is settled

`medium.com/me/settings/security` was checked on this account: there is **no Integration
tokens section**. Mastodon, Facebook, X and Google are listed; token issuing is gone. No
token means the two Medium nodes can never authenticate.

So the workflow is imported into n8n and saved, but **inactive and without credentials**
— kept as a record of the attempt, not as something that works. Anything promising
automated publishing to Medium today is either driving a browser session or lying.

## How the article actually got published

By hand, and the method matters because the obvious ones fail:

1. Medium's **import-from-URL** loses the section headings, the images and some code
   blocks. Tried twice; not usable for an article this long.
2. Copying from GitHub's **Code** tab pastes raw markdown — `##`, `**`, `![]()` appear
   literally.
3. Copying from GitHub's **Preview** tab works. Headings, images and code blocks survive,
   and Medium re-hosts the images on its own CDN instead of hotlinking the repository.

Published: https://medium.com/@patrickpriebepp/the-bugs-that-designed-my-architecture-258cf1261f85

Updating it later means editing in Medium's editor. There is no API path, so the
markdown here is the source of truth for the text and the published post drifts from it
unless someone syncs it deliberately.

## If real sync ever matters

dev.to (Forem) and Hashnode both expose an update endpoint. The same flow retargets in
one node, and the "change the project, the article follows" idea becomes possible — on a
platform other than Medium.

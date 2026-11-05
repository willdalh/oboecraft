# ADR Format

ADRs live in `docs/decisions/` and are named by date: `YYYYMMDD-title.md`, like `20260310-event-sourced-orders.md`.

Create the `docs/decisions/` directory lazily: only when the first ADR is needed.

## Template

```md
# ADR-YYYYMMDD: Title

## Status
Accepted | Superseded by ADR-YYYYMMDD

## Context
What situation prompted this decision.

## Decision
What we decided to do.

## Consequences
What becomes easier, harder, or different as a result.
```

Once accepted, an ADR is never edited, except for its status when a newer ADR replaces it.

## Naming

Use today's date and a short kebab-case title. The heading uses the same date: `# ADR-YYYYMMDD: Title`.

## When to offer an ADR

All three of these must be true:

1. **Hard to reverse**: the cost of changing your mind later is meaningful
2. **Surprising without context**: a future reader will look at the code and wonder "why on earth did they do it this way?"
3. **The result of a real trade-off**: there were genuine alternatives and you picked one for specific reasons

If a decision is easy to reverse, skip it: you'll just reverse it. If it's not surprising, nobody will wonder why. If there was no real alternative, there's nothing to record beyond "we did the obvious thing."

### What qualifies

- **Architectural shape.** "We're using a monorepo." "The write model is event-sourced, the read model is projected into Postgres."
- **Integration patterns between contexts.** "Ordering and Billing communicate via domain events, not synchronous HTTP."
- **Technology choices that carry lock-in.** Database, message bus, auth provider, deployment target. Not every library: just the ones that would take a quarter to swap out.
- **Boundary and scope decisions.** "Customer data is owned by the Customer context; other contexts reference it by ID only." The explicit no-s are as valuable as the yes-s.
- **Deliberate deviations from the obvious path.** "We're using manual SQL instead of an ORM because X." Anything where a reasonable reader would assume the opposite. These stop the next engineer from "fixing" something that was deliberate.
- **Constraints not visible in the code.** "We can't use AWS because of compliance requirements." "Response times must be under 200ms because of the partner API contract."
- **Rejected alternatives when the rejection is non-obvious.** If you considered GraphQL and picked REST for subtle reasons, record it; otherwise someone will suggest GraphQL again in six months.

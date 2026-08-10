# Conflux Integration (PhoenixCore)

- **PhoenixCore currently loaded:** {{conflux_available}}

Conflux is PhoenixCore's research/discipline system: players unlock research
nodes at a Research Terminal, which can set "flags" (arbitrary string ids used
to gate content elsewhere) and/or join a "discipline" tree. It is its own
separate research-tree UI, distinct from this quest tree - Chronicles' Conflux
support is a data hook between the two systems, not a merged visual tree.

---

## As a quest requirement

Add a "conflux_research" task to make a quest wait on a Conflux unlock/flag:

```
{
  type: "conflux_research", task_id: "phoenix_chronicles:task_…",
  flag_mode: false, node_id: "phoenixcore:some_research_node"
}
```

Or check a flag instead of a specific node (flag_mode: true, flag: "…").
Always reads live from PhoenixCore's WorldResearchData - no local progress to
desync, and it's server-authoritative (always incomplete on the client alone).
Always incomplete (never satisfied) if PhoenixCore isn't loaded at all.

---

## As a quest reward

Add a "conflux_unlock" reward to grant a Conflux unlock/flag for free on claim,
bypassing the Research Terminal's normal cost entirely:

```
{ type: "conflux_unlock", flag_mode: false, node_id: "phoenixcore:some_research_node" }
```

This is a flat unlock only - it does not join a discipline tree or mark a
commitment node, even if the target node normally would at the terminal.
No-ops silently if PhoenixCore isn't loaded.

---

## Setup

PhoenixCore is a genuinely optional dependency - Chronicles compiles against it
but does not bundle or force-load it. Whether it's actually present in your
modpack is your call; if it's absent, conflux_research tasks simply never
complete and conflux_unlock rewards silently do nothing - nothing crashes.

:::warning
PhoenixCore currently hard-requires a newer GregTech CEu build than this
project's own dev environment ships, which is exactly why it's compile-only
and not bundled as a runtime dependency.
:::

- **Java bridge** — net.phoenixvine.chronicles.integration.conflux.ConfluxCompat

```java
ConfluxCompat.isAvailable()
ConfluxCompat.isUnlocked(player, nodeId)   // server-side only
ConfluxCompat.hasFlag(player, flag)        // server-side only
ConfluxCompat.grantUnlock(serverPlayer, nodeId)
ConfluxCompat.grantFlag(serverPlayer, flag)
```

# 0008. Local only

**Status:** accepted, 2026-11-14

## Context

The app holds a model API key and the analyst's conversations. It has no login. It also serves MCP, whose spec warns
local servers against requests from other sites' pages (DNS rebinding).

## Options

| Option | Against |
|---|---|
| Listen on every address | Anyone on the network can spend the key and read chats |
| Add a login now | Users, passwords and sessions for an app one analyst runs |
| **Listen on 127.0.0.1 and check the host** | No sharing between analysts |

## Decision

Both ports (8080 for the chat, 8081 for Actuator) listen on 127.0.0.1. `HostFilter` answers 403 to a request for
another host name, or one sent by another site's page. Docker publishes the ports on the host's 127.0.0.1 only.

## Consequences

- Safe on a laptop on any network, with nothing to configure.
- A shared deployment needs a login and an allowed-host setting first ([0009](0009-docker-on-the-analysts-machine.md)).
- Chat memory is per conversation, not per user, which is fine for one analyst.

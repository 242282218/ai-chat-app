# Gateway Contracts

This directory stores JSON schemas shared by the Android gateway client and the Go gateway.

Current golden fixtures live in `fixtures/`:

- `tool-manifest.json`
- `search-success.json`
- `search-error-unavailable.json`
- `sandbox-success.json`
- `sandbox-timeout.json`
- `sandbox-error-unavailable.json`
- `gateway-error.json`

Go gateway tests and Android `GatewayClientTest` should read these fixtures directly. When the gateway contract changes, update the schema, fixture, Go response test, and Android parser test in the same change.

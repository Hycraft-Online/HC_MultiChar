# HC_MultiChar

Multi-character system that allows players to have multiple independent characters on a single account. Each character has its own inventory, progression, faction membership, and play time. Character switching is implemented via disconnect/reconnect to ensure clean data serialization through the engine's built-in save pipeline.

## Features

- Multiple character slots per account with automatic default character creation on first login
- Character switching via `/character` command with 30-second cooldown
- Character selection GUI
- Per-character play time tracking with automatic flush on disconnect and shutdown
- Safe switch ordering: disconnect triggers auto-save to current slot, then active slot is updated for next connect
- Switch-in-progress flag so other plugins (HC_StarterArea, HC_Factions, etc.) skip cleanup during character transitions
- Static API (`HC_MultiCharAPI`) for other plugins to query active character UUID, check switching state, and resolve character IDs
- PostgreSQL-backed character repository

## Dependencies

- **EntityModule** (required) -- Hytale entity system

## Building

```bash
./gradlew build
```

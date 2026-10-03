# ChaosManhunt

Professionelle Manhunt-Event-Steuerung für **Paper 1.21.11** (Java 21) – gebaut für Live-Streams:
Admin-Panel, Rollen, Tracker-Kompass mit Dimensionsprüfung, Respawn-Sperre und dynamischer
Actionbar-Timer mit echtem Hex-Farbverlauf.

---

## Funktionen

### 1. Admin-Panel & Befehle
* `/manhunt` (Alias `/mh`) – nur für `manhunt.admin` (Standard: OP).
* Ohne Argument öffnet sich ein Admin-Panel (Inventar) mit:
  * **Statusanzeige** (grün = läuft, gelb = Countdown, rot = bereit) inkl. Runner, Hunter-Anzahl und Laufzeit,
  * **Start** (grüne Wolle) und **Stop** (rote Wolle),
  * **Runner-Auswahl** über Spielerköpfe (man selbst oder jeder andere Spieler),
  * **Hunter-Verwaltung** mit Live-Übersicht, wer Hunter ist,
  * **Tracker-Ausgabe** an alle Hunter,
  * **Einstellungs-Übersicht** und Schließen-Knopf.
* Das Panel aktualisiert sich automatisch, solange es geöffnet ist.
* Sämtliche Klicks im Inventar werden abgebrochen (`event.setCancelled(true)`) – Duplizieren oder
  Verschieben von Items ist ausgeschlossen, ebenso Drag-Operationen und Shift-Klicks.

### 2. Welten- & Dimensions-Compass
* Hunter erhalten beim Start automatisch den **„Gorgii-Tracker"** (markierter Kompass, PDC-Tag).
* Die Kompass-Nadel wird 4× pro Sekunde auf den Runner ausgerichtet (`Player#setCompassTarget`).
* **Dimensionsprüfung:** Nur wenn Hunter und Runner in derselben Welt sind, wird die Nadel
  aktualisiert. In einer anderen Dimension bleibt sie stabil (kein Herumspinnen) und ein
  Rechtsklick liefert eine deutliche Chat-Nachricht mit der Dimension des Runners.
  Optional (`tracker.cross-dimension-mode: PORTAL_SCALED`) werden Oberwelt/Nether-Koordinaten
  mit dem Portal-Faktor 8 umgerechnet.
* Rechtsklick in derselben Welt: Entfernung, Himmelsrichtung und Koordinaten.
* Der Tracker kann weder weggeworfen noch in Behälter gelegt werden.

### 3. Hunter-Respawn & Todeslogik
* Stirbt ein Hunter, verliert er sein **komplettes Inventar** (und optional Erfahrung) –
  `keepInventory` wird hart auf `false` gesetzt.
* Der Tracker geht **nicht** verloren: Er wird eingesammelt und nach der Sperre neu ausgegeben.
* Der Hunter landet **sofort im Zuschauer-Modus**.
* **Respawn-Sperre: exakt 120 Sekunden** (konfigurierbar, zeitbasiert – übersteht auch einen Reconnect).
  Die Actionbar zeigt den Countdown, danach wird der Hunter automatisch in den Survival-Modus
  teleportiert (Weltspawn, Todesstelle oder Bett – konfigurierbar) und bekommt seinen Tracker zurück.
* Stirbt der **Runner**, endet das Event sofort mit der globalen Siegesmeldung der Hunter.

### 4. Dynamischer Actionbar-Timer (BastiGHG-Style)
* Es werden nur die Einheiten angezeigt, die gebraucht werden:

  | Laufzeit | Anzeige |
  |---|---|
  | 0:15 | `15s` |
  | 4:12 | `4m 12s` |
  | 1:00:00 | `1h` |
  | 1:03:42 | `1h 03m 42s` (mit `pad-zero-units: false`: `1h 3m 42s`) |

* Der Timer läuft durch einen **echten, animierten Hex-Farbverlauf** (Standard: Rot → Orange → Gelb),
  Zeichen für Zeichen per RGB interpoliert – inklusive fließender Animation.

### 5. Inventar- & Schutzlogik
* Alle GUI-Klicks sind gesperrt (siehe oben).
* Alle Tasks (Actionbar, Tracker, Respawn-Sperren, Menü-Refresh) werden in einer Liste geführt und bei
  `Stop`, Server-Stop oder Plugin-Deaktivierung **garantiert gecancelt** – es laufen keine Timer weiter.
* Beim Deaktivieren werden Spielmodi wiederhergestellt, Tracker entfernt und die Actionbar geleert.

---

## Installation

1. **Server:** Paper **1.21.11** (getestet mit Build 132), **Java 21**.
2. **Bauen:**
   ```bash
   mvn -B clean package
   ```
   Ergebnis: `target/ChaosManhunt-1.0.0.jar`
3. Die JAR nach `plugins/` kopieren und den Server starten.
4. `plugins/ChaosManhunt/config.yml` und `messages.yml` nach Wunsch anpassen, danach `/mh reload`.

> Der Build lädt `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` aus dem offiziellen
> PaperMC-Repository. Ohne Internetzugang kann nicht gebaut werden.

## Befehle & Rechte

| Befehl | Beschreibung |
|---|---|
| `/mh` | Admin-Panel öffnen |
| `/mh start [Spieler]` | Event starten (optional Runner direkt setzen) |
| `/mh stop` | Event beenden |
| `/mh runner <Spieler>` | Runner festlegen |
| `/mh hunter <add\|remove\|list> [Spieler]` | Hunter verwalten |
| `/mh status` | Status, Runner, Hunter, Laufzeit |
| `/mh reload` | `config.yml` + `messages.yml` neu laden |
| `/mh diagnose` | Selbsttest (Konfiguration, Farbverlauf, Tracker-Item, Nachrichten) |
| `/mh help` | Hilfe |

| Permission | Standard | Bedeutung |
|---|---|---|
| `manhunt.admin` | OP | Vollzugriff (Panel, Start/Stop, Rollen) |
| `manhunt.manage` | OP | Verwaltungs-Marker |
| `manhunt.play` | true | Teilnahme-Marker |

## Wichtige Konfigurationswerte (`config.yml`)

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `game.countdown-seconds` | `5` | Countdown vor dem Start (0 = sofort) |
| `game.respawn-lock-seconds` | `120` | Respawn-Sperre der Hunter |
| `game.auto-hunters-on-join` | `true` | Joiner werden automatisch Hunter |
| `game.end-game-when-runner-quits` | `false` | Event beenden, wenn der Runner das Spiel verlässt |
| `game.spawn-world` | `''` | Welt für Hunter-Respawns (leer = Hauptwelt) |
| `hunter.respawn-strategy` | `WORLD_SPAWN` | `WORLD_SPAWN`, `DEATH_LOCATION` oder `BED` |
| `hunter.drop-inventory` | `true` | Inventar beim Tod verlieren |
| `tracker.name` / `tracker.lore` | – | Name und Lore des Kompass-Items |
| `tracker.cross-dimension-mode` | `FROZEN` | `FROZEN` oder `PORTAL_SCALED` |
| `actionbar.format` | – | Timer-Vorlage mit `%time%`, `%runner%`, `%hunters%`, `%state%` |
| `actionbar.gradient.colors` | Rot→Gelb | beliebig viele Hex-Stützfarben |
| `actionbar.gradient.animate` / `speed` | `true` / `0.015` | Animation des Verlaufs |
| `timer.pad-zero-units` | `true` | `1h 03m 42s` statt `1h 3m 42s` |
| `sounds.enabled` | `true` | Sounds komplett abschaltbar |

Alle Texte liegen in der `messages.yml` (Hex-Farben als `&#RRGGBB`). Fehlt nach einem Update ein
Schlüssel, greift automatisch der mitgelieferte Standardtext – eigene Anpassungen bleiben erhalten.

## Technische Hinweise

* **Farbverlauf:** Die Hex-Farben entstehen über `ChatColor.of()` (Bungee-Chat-API, die Paper
  mitliefert) und werden anschließend in Adventure-`TextColor` übernommen, weil Paper 1.21.11 alle
  Texte über Components färbt – die alte `§x§…`-Schreibweise ist dafür nicht mehr nötig.
  Interpoliert wird pro Zeichen (codepoint-sicher, also auch für Sonderzeichen), Hex-Farben
  funktionieren dadurch in Nachrichten, Item-Namen und Lore gleichermaßen. Sollte die Bungee-API in
  einer künftigen Serverversion entfallen, greift automatisch ein direkter Hex-Parser.
* **Tod der Hunter:** Der `PlayerDeathEvent` wird abgebrochen – das ist der von Paper offiziell
  empfohlene Weg (Paper stellt die Lebenspunkte über `EntityDeathEvent#getReviveHealth` wieder her).
  Dadurch entsteht kein Death-Screen und kein Client-Desync, der bei `spigot().respawn()` ab
  Minecraft 1.21.11 auftritt. Inventar-Drops, Erfahrung, Sound und Titel werden vom Plugin selbst
  übernommen.
* **Emoji-Symbolik:** Das Vanilla-Font enthält keine farbigen Emojis. Die Standardtexte nutzen daher
  Symbole wie `✦`, `▪`, `☠`, die garantiert dargestellt werden. Wer ein Emoji-Resourcepack
  einsetzt, findet in der `messages.yml` die kommentierte Emoji-Variante der Siegesmeldung.
* **CI:** `ci/build.yml` baut das Plugin, führt die Unit-Tests aus und startet anschließend einen
  echten Paper-1.21.11-Server, auf dem `/mh diagnose`, `/mh status`, `/mh start`, `/mh reload` und Co.
  automatisiert geprüft werden. Damit GitHub die Datei ausführt, wird sie einmalig verschoben:
  `git mv ci/build.yml .github/workflows/build.yml` – danach läuft der komplette Test bei jedem Push.

## Projektstruktur

```
src/main/java/de/chaostobimc/chaosmanhunt/
├── ChaosManhunt.java            # Plugin-Einstieg, Service-Verdrahtung
├── ManhuntConfig.java           # typisierter Config-Zugriff
├── Messages.java                # messages.yml inkl. Fallback & Platzhalter
├── command/ManhuntCommand.java  # /manhunt inkl. Tab-Completion & Diagnose
├── game/                        # ManhuntGame, TrackerService, GameState, RespawnLock
├── listener/                    # GameListener, TrackerListener
├── task/ActionBarTask.java      # Actionbar-Timer mit Farbverlauf
├── ui/                          # AdminMenu, PlayerListMenu, MenuListener, MenuRegistry
└── util/                        # Text, Gradient, TimeFormat, Nav, ItemBuilder

src/main/resources/              # plugin.yml, config.yml, messages.yml
src/test/java/…                  # JUnit-5-Tests für Timer, Gradient, Text, Navigation
ci/build.yml                     # GitHub-Actions-Build (siehe Technische Hinweise)
```

## Lizenz

Frei verwendbar für eigene Streams und Server. Viel Erfolg – und möge der Runner weit kommen. 🎯

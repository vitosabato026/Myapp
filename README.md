# Promozione B

App Android dedicata al campionato di **Promozione Puglia – Girone B** (stagione 2026/27): classifica, partite, gol, statistiche, notizie e pagine delle squadre con i loro profili social ufficiali.

## Schermate

- **Home**: big match della giornata, primi 5 in classifica, capocannoniere, gol totali, ultima notizia.
- **Classifica**: totale, casa e trasferta, con penalizzazioni e zone (Eccellenza, play-off, play-out, retrocessione).
- **Partite**: tutte le giornate, con risultati, orari e marcatori.
- **Gol**: gol totali, media, miglior attacco e difesa, classifica marcatori.
- **Notizie**: articoli di SalentoSport, più un collegamento al girone su Tuttocampo.
- **Squadra**: posizione, ultime 5, statistiche, prossima partita, partite giocate, marcatori, pulsanti Instagram e Facebook.

Tema chiaro, scuro o come il telefono (icona in alto a destra). Font Google Sans. I dati restano salvati per l'uso offline; trascina verso il basso per aggiornare.

## Fonti dei dati

| Dato | Fonte |
|---|---|
| Classifica, calendario, risultati, tabellini | [livescore.calciowebpuglia.it](https://livescore.calciowebpuglia.it/stagioni/26-27/promozione/gironi/girone-b/classifica), la stessa fonte delle dirette di SalentoSport |
| Classifica marcatori | Calcolata dall'app dai tabellini (esclusi autogol e gare a tavolino) |
| Notizie | API pubblica di [salentosport.net](https://www.salentosport.net/) (categoria Promozione) |
| Profili social | Ricerca web; Sporting Oria indicati dal committente. Sono in `data/Teams.kt` |

Tuttocampo.it è protetto da un controllo anti-bot (AWS WAF): l'app non ne legge i dati, ma apre il girone sul sito.

## Compilare

Serve l'Android SDK (piattaforma 36) e JDK 17 o superiore.

```bash
./gradlew testDebugUnitTest   # test di parser, marcatori e screenshot
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

I test generano anche gli screenshot di tutte le schermate, in tema chiaro e scuro, in `app/build/screenshots/` (con Robolectric, senza emulatore).

## Da sistemare a ogni stagione

- URL della stagione in `LeagueRepository` (`26-27`).
- Squadre e social in `Teams.kt`.
- Zone di classifica in `StandingsScreen.kt` (valori indicativi, da verificare sul comunicato del Comitato Regionale Puglia).

## Licenze

Google Sans è distribuito con licenza SIL Open Font License 1.1 (`app/src/main/assets/OFL-GoogleSans.txt`).

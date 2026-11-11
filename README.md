# Promozione B

App Android dedicata al campionato di **Promozione Puglia – Girone B** (stagione 2026/27): classifica, partite, gol, statistiche, notizie e pagine delle squadre con i loro profili social ufficiali.

## Schermate

Ispirate a Sofascore:

- **Home**: "La mia squadra" in cima (o la scelta rapida toccandone il logo), partite in diretta, big match, classifica (in tempo reale durante le partite), capocannoniere, gol totali, ultima notizia.
- **Classifica**: totale, casa e trasferta, con zone e penalizzazioni. Durante le partite è **in tempo reale**: risultati parziali inclusi, frecce di chi sale o scende rispetto all'inizio della giornata, punteggio in corso accanto alla squadra. La squadra seguita è evidenziata.
- **Partite**: liste compatte per giorno (orario o minuto a sinistra, squadre una sotto l'altra con il punteggio, perdente in grigio, barra rossa in diretta).
- **Gol**: gol totali, media, miglior attacco e difesa, classifica marcatori (tocca un nome per il giocatore).
- **Notizie**: articoli di SalentoSport, più un collegamento al girone su Tuttocampo.
- **Partita**: a schede Dettagli / Classifica / Notizie. Gol minuto per minuto divisi tra casa e trasferta con separatori "Intervallo" e "Finale" (risultato all'intervallo ricavato dai minuti), confronto tra le squadre, andata/ritorno, collegamenti a Tuttocampo; classifica con le due squadre evidenziate.
- **Squadra**: stella per seguirla; schede Panoramica (partita in corso o prossima, statistiche, grafico della posizione giornata per giornata, gol fatti e subiti per fascia di 15 minuti, rendimento in casa e fuori, partite senza subire e senza segnare, social), Partite, Marcatori.
- **Giocatore**: gol, rigori, posizione tra i marcatori e gol partita per partita con i minuti.

Tema chiaro, scuro o come il telefono (icona in alto a destra). Font Google Sans. I dati restano salvati per l'uso offline; trascina verso il basso per aggiornare.

## Aggiornamenti e diretta

- I dati si aggiornano **a ogni apertura** dell'app (anche quando torna dal background).
- Con l'app aperta, mentre ci sono **partite in corso** risultati e marcatori si riscaricano da soli **ogni 30 secondi** (solo classifica e calendario, circa 25 KB compressi). Le partite in diretta compaiono in cima alla Home e sono evidenziate con LIVE in Partite e nella pagina squadra.
- Attorno al calcio d'inizio (da 20 minuti prima) si controlla ogni minuto, per accorgersi subito che una partita è partita; se ci sono partite entro 12 ore si ricontrolla almeno ogni 30 minuti; altrimenti gli aggiornamenti automatici si fermano. Con l'app chiusa o in background non si scarica nulla.
- La fonte segna le partite in corso evidenziandone il risultato, ma **non pubblica il minuto**: l'app lo stima dall'orario d'inizio (due tempi da 45' e 15' di intervallo) e lo dice sotto le partite in diretta.
- Regole in `data/Live.kt`.

## Notifiche dei gol

- Campanella in alto: gol di **tutto il girone** (di serie; seguendo una squadra diventano solo i suoi, se non li hai scelti a mano), di **una sola squadra** o disattivate. Al primo avvio l'app chiede il permesso per le notifiche (Android 13+).
- Una notifica per partita, aggiornata a ogni gol: "GOL! Virtus Matino 1-0 Real V. Grottaglie · 12' Puzzovio". Se la fonte non ha ancora scritto il marcatore, la notifica lo dice. Toccandola si apre la partita.
- Con l'app chiusa i controlli li fa WorkManager (`notifications/LiveUpdates.kt`): ogni minuto durante le partite, poi nulla fino a 20 minuti prima della giornata successiva; in più un controllo ogni 12 ore per eventuali cambi di orario.
- Si notificano solo partite in diretta (o appena finite) nelle 4 ore dal calcio d'inizio: aprire l'app giorni dopo non produce una raffica di notifiche, e lo stesso gol non viene mai notificato due volte.
- Limite di Android: con il telefono fermo e lo schermo spento a lungo (Doze) il sistema raggruppa i controlli in background, quindi una notifica può arrivare con qualche minuto di ritardo.

## Fonti dei dati

| Dato | Fonte |
|---|---|
| Classifica, calendario, risultati, tabellini | [livescore.calciowebpuglia.it](https://livescore.calciowebpuglia.it/stagioni/26-27/promozione/gironi/girone-b/classifica), la stessa fonte delle dirette di SalentoSport |
| Classifica marcatori | Calcolata dall'app dai tabellini (esclusi autogol e gare a tavolino) |
| Notizie | API pubblica di [salentosport.net](https://www.salentosport.net/) (categoria Promozione) |
| Loghi delle squadre | Le immagini di [livescore.calciowebpuglia.it](https://livescore.calciowebpuglia.it/) (`/img/loghi/`), ritagliate e ridotte a 192 px in `res/drawable-nodpi/logo_*.png`; inclusi nell'app, funzionano offline. Per squadre nuove l'app mostra un cerchio con la sigla finché non si aggiunge il logo |
| Profili social | Ricerca web; Sporting Oria indicati dal committente. Sono in `data/Teams.kt` |

Tuttocampo.it è protetto da un controllo anti-bot (AWS WAF) che blocca la lettura automatica: l'app non ne legge i dati, ma apre nel browser la pagina della partita (`data/Tuttocampo.kt`). Il sito numera le partite di ogni giornata d'andata in ordine alfabetico della squadra di casa e al ritorno ripete il numero dell'andata: regola ricavata da tre indirizzi reali e verificata nei test. Se una squadra cambia nome su Tuttocampo va aggiornato `tuttocampoName` in `Teams.kt`. Formazioni, ammonizioni e cronaca non sono pubblicate dalle fonti usate.

## Scaricare l'app

L'APK di test più recente è in [`dist/PromozioneB-0.1.0-debug.apk`](dist/PromozioneB-0.1.0-debug.apk) (Android 8 o successivo, firmato con la chiave di debug): scaricalo sul telefono, aprilo e consenti l'installazione da quella fonte se Android lo chiede.

## Compilare

Serve l'Android SDK (piattaforma 36) e JDK 17 o superiore.

```bash
./gradlew testDebugUnitTest   # test di parser, marcatori e screenshot
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

I test generano anche gli screenshot di tutte le schermate, in tema chiaro e scuro, in `app/build/screenshots/` (con Robolectric, senza emulatore).

## Da sistemare a ogni stagione

- URL della stagione in `LeagueRepository` (`26-27`).
- Squadre, social e nomi Tuttocampo in `Teams.kt`; loghi in `res/drawable-nodpi/` e in `teamLogo()` (`ui/components/Components.kt`).
- Zone di classifica in `StandingsScreen.kt` (valori indicativi, da verificare sul comunicato del Comitato Regionale Puglia).

## Licenze

Google Sans è distribuito con licenza SIL Open Font License 1.1 (`app/src/main/assets/OFL-GoogleSans.txt`).

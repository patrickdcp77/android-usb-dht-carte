# Journal de développement — Android USB DHT + GPS + Carte OSM

Objectif de ce journal : garder une trace simple (type “carnet de bord”) de ce qui a été fait, pourquoi, et comment le reproduire plus tard.

> Projet : `MyApplication2` (rootProject.name = "My Application")
> Date : 2026-03-01 (mise à jour)

---

## 0) Procédure de reproduction pas à pas (10 minutes)

Pour une procédure **courte, imprimable et actionnable**, voir :
- `docs/checklist-reproduction.md`

Ce journal reste volontairement plus "explicatif" (décisions, pièges, raisons).

---

## 1) Vue d’ensemble (ce qu’on a construit)

- Projet Android Studio **multi-modules** :
  - `:app` = **App capteur** (USB-Serial + localisation + enregistrement)
  - `:appmap` = **App carte** (OpenStreetMap via osmdroid + lecture des mesures)
  - `:core` = **module partagé** (Room DB + Entity/DAO/Repository)

- Fonctionnel :
  - l’app capteur affiche **Temp/Hum + Lat/Lon**
  - l’app capteur enregistre une mesure **toutes les 10 secondes max** (rate limiter)
  - conservation **3000 dernières** mesures
  - l’app carte lit les mesures via un **ContentProvider** exposé par `:app`
  - carte osmdroid : points **verts**, le **dernier point rouge**
  - bouton Rafraîchir + `ContentObserver` (si notif reçue)

---

## 2) Étapes clés (chronologie simplifiée)

### 2.1 USB permission receiver (Android 13+)
Problème rencontré : Lint / build demandait `RECEIVER_EXPORTED` ou `RECEIVER_NOT_EXPORTED` pour `registerReceiver`.

Solution appliquée dans `MainActivity.kt` (`:app`) :
- sur API 33+ : utiliser la surcharge avec flags `Context.RECEIVER_NOT_EXPORTED`
- définir une **permission interne** pour protéger le broadcast USB (bonne pratique)

Fichier :
- `app/src/main/java/com/example/myapplication/MainActivity.kt`

### 2.2 Localisation (GPS)
Objectif : afficher Lat/Lon et associer chaque mesure à une position.

Approche :
- permissions `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- récupération via `FusedLocationProviderClient` :
  - `getCurrentLocation(...)`
  - fallback : `requestLocationUpdates(... maxUpdates=1)` si nécessaire

Fichier :
- `app/src/main/java/com/example/myapplication/MainActivity.kt`

### 2.3 Stockage (Room) + trimming
Objectif : conserver un historique local consultable par l’app carte.

Choix : Room (module `:core`).

- Entité : `MeasurementEntity`
- DAO : insert + requêtes
- trimming : garder les `keepLast` dernières (ex: 3000)

Fichiers :
- `core/src/main/java/com/example/core/db/AppDatabase.kt`
- `core/src/main/java/com/example/core/db/MeasurementEntity.kt`
- `core/src/main/java/com/example/core/db/MeasurementDao.kt`
- `core/src/main/java/com/example/core/db/MeasurementRepository.kt`

### 2.4 Rate limiter (10 secondes)
Demande : ne pas sauvegarder à chaque trame, mais **au max toutes les 10 secondes**.

Implémentation :
- `saveIntervalMs = 10_000L`
- `lastSavedAtMs` (volatile)
- condition : `now - lastSavedAtMs >= saveIntervalMs`

Fichier :
- `app/src/main/java/com/example/myapplication/MainActivity.kt`

### 2.5 Partage inter-app via ContentProvider (lecture seule)
Problème initial : `Mesures:0` dans l’app carte.

Cause classique : si le provider est déclaré dans `:core`, il est fusionné aussi dans `:appmap`, et l’app carte lit alors **sa propre DB** (vide).

Solution :
- déclarer le provider **uniquement** dans le manifest de `:app`
- laisser un commentaire explicite dans `core/src/main/AndroidManifest.xml`

Fichiers :
- Provider : `app/src/main/java/com/example/myapplication/provider/MeasurementsProvider.kt`
- Manifest app : `app/src/main/AndroidManifest.xml`
- Manifest core : `core/src/main/AndroidManifest.xml`

URI :
- `content://com.example.myapplication.measurements/latest?limit=3000`

### 2.6 Synchronisation app carte (refresh)
Objectif : l’app carte doit voir les nouvelles mesures.

Implémentation :
- Dans `:app`, après insertion en base : `contentResolver.notifyChange(MeasurementsProvider.CONTENT_URI, null)`
- Dans `:appmap`, un `ContentObserver` déclenche `loadLatest()`
- En complément : bouton "Rafraîchir"

Fichiers :
- App capteur : `app/src/main/java/com/example/myapplication/MainActivity.kt`
- App carte : `appmap/src/main/java/com/example/myapplication/map/MapActivity.kt`

### 2.7 Affichage carte + points (dernier rouge)
Objectif : matérialiser les mesures sur OpenStreetMap.

Implémentation (osmdroid) :
- `MapView` (MAPNIK)
- overlay `Marker` par mesure
- icône : cercle (ShapeDrawable Oval)
- couleur :
  - dernier point (plus récent) = rouge
  - autres = vert

Fichier :
- `appmap/src/main/java/com/example/myapplication/map/MapActivity.kt`

---

## 3) Commandes & environnement (Windows)

### 3.1 JAVA_HOME (quand Gradle se plaint)
Erreur typique : `JAVA_HOME is not set`.

Solution (session PowerShell) :
```powershell
$Env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$Env:JAVA_HOME\bin\java.exe" -version
```

Permanent (optionnel) :
```powershell
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
```

### 3.2 Build rapide
```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :appmap:compileDebugKotlin
```

### 3.3 ADB (si `adb` non reconnu)
Utiliser le chemin complet :
```powershell
$adb = "C:\Users\patri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```

---

## 4) Git / GitHub

### 4.1 Dépôts
- dépôt principal : `android-usb-dht-carte`

### 4.2 Règle importante : ne pas committer les builds
- `.gitignore` ignore `**/build/` + `local.properties`.
- si un `build/` a été committé par erreur :
```bash
git rm -r --cached <module>/build
```

---

## 5) Documentation

- README principal : `README.md`
- Article WordPress/BetterDocs : `docs/betterdocs-article.md`

---

## 6) Prochaines évolutions possibles (idées)

- Logging en arrière-plan (Foreground Service) pour que l’app capteur continue sans écran.
- Export CSV / partage.
- Mode “données simulées” pour tester `:appmap` sur émulateur sans USB.
- Filtrage des points (par date, par zone, clustering si beaucoup de points).

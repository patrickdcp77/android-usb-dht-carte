# Android USB DHT Capteur + Géolocalisation + Carte OSM (projet multi-modules)

Ce dépôt contient **2 applications Android** qui partagent un même module `:core` :

- **App capteur** (`:app`) : lit en **USB-Serial (OTG)** des trames du capteur (ex: DHT via ESP8266/ESP32), récupère la **localisation**, affiche en temps réel et **enregistre** les mesures.
- **App carte** (`:appmap`) : affiche une **carte OpenStreetMap (osmdroid)** centrée sur la position du smartphone et matérialise les mesures enregistrées (dernier point **rouge**, historiques **verts**).

Les données sont partagées via un **ContentProvider** exposé par l’app capteur.

---

## Mini schéma ASCII “architecture”

```
                       (USB OTG)                     (GPS / Wi‑Fi / Cell)
[ESP / Capteur]  ------------------>  :app MainActivity  <-----------------
        |                                  |
        |                                  |  (rate limiter 10s)
        |                                  v
        |                            Room DB (:core)
        |                                  |
        |                                  |  notifyChange(content://…)
        |                                  v
        |                   ContentProvider (déclaré dans :app)
        |                 content://com.example.myapplication.measurements/latest
        |                                  |
        |                                  v
        +--------------------------->  :appmap MapActivity (Refresh / Observer)
                                           |
                                           v
                                      Carte OSM (osmdroid)
                              points = mesures (dernier rouge, autres verts)
```

---

## Modules Gradle (ce qu’il faut lancer)

Déclarés dans `settings.gradle.kts` :
- `:app` (application) → **Capteur DHT + USB + Location + stockage + provider**
- `:appmap` (application) → **Carte OSM + lecture provider + affichage points**
- `:core` (library) → **Room (DB/DAO/Entity/Repository) + code partagé**

Dans Android Studio, tu choisis le module à exécuter via la configuration **Run/Debug** (ou le menu déroulant). Sinon en Gradle :

```powershell
# APK debug app capteur
.\gradlew.bat :app:assembleDebug

# APK debug app carte
.\gradlew.bat :appmap:assembleDebug
```

---

## Flux de données (important)

### 1) App capteur (`:app`)
Fichiers clés :
- `app/src/main/java/com/example/myapplication/MainActivity.kt`

Flux :
1. Détection driver USB-Serial (`UsbSerialProber`) + permission USB.
2. Lecture bytes via `SerialInputOutputManager`.
3. Reconstruction de lignes `\n` puis parsing `T=…;H=…`.
4. Récupération localisation via `FusedLocationProviderClient`.
5. **Enregistrement en base (Room)** d’une mesure horodatée *si* :
   - on a une localisation, et
   - au moins une valeur (T ou H), et
   - **rate limiter** : `now - lastSavedAtMs >= 10_000` (10 secondes)
6. Après insertion : `contentResolver.notifyChange(MeasurementsProvider.CONTENT_URI, null)`
   → permet à l’app carte de se rafraîchir.

### 2) Stockage (module `:core`)
Fichiers clés :
- `core/src/main/java/com/example/core/db/MeasurementEntity.kt`
- `core/src/main/java/com/example/core/db/MeasurementDao.kt`
- `core/src/main/java/com/example/core/db/AppDatabase.kt`
- `core/src/main/java/com/example/core/db/MeasurementRepository.kt`

Rôle :
- Stocker les mesures (timestamp + lat/lon + température + humidité + raw).
- Garder un historique limité (ex: **3000 dernières** mesures) via `trimToLatest(keepLast)`.

### 3) Partage inter-app (ContentProvider)
Fichier clé :
- `app/src/main/java/com/example/myapplication/provider/MeasurementsProvider.kt`

Contrat :
- Authority : `com.example.myapplication.measurements`
- URI :
  - `content://com.example.myapplication.measurements/latest?limit=3000`

Important :
- Le provider est **déclaré dans le Manifest de `:app` uniquement** :
  - `app/src/main/AndroidManifest.xml`
- `core/src/main/AndroidManifest.xml` contient volontairement un commentaire indiquant de **NE PAS** déclarer le provider dans `:core`.
  Sinon il serait fusionné dans `:appmap` et la carte lirait une DB “vide” → `Mesures:0`.

### 4) App carte (`:appmap`)
Fichier clé :
- `appmap/src/main/java/com/example/myapplication/map/MapActivity.kt`

Flux :
1. Récupère la localisation (pour centrer la carte).
2. Lit les mesures via `contentResolver.query()` sur le provider.
3. Affiche les points sur la carte osmdroid :
   - **dernier point** (le plus récent) en **rouge**
   - les autres en **vert**
4. Rafraîchissement :
   - bouton **Rafraîchir**
   - et/ou `ContentObserver` (si `notifyChange` est reçu)

---

## Rôle des principaux fichiers / dossiers

### Racine
- `settings.gradle.kts` : liste les modules (`:app`, `:core`, `:appmap`) et les repositories (Google/MavenCentral/Jitpack).
- `build.gradle.kts` : configuration Gradle “racine” (plugins/versions).
- `gradle/libs.versions.toml` : Version Catalog (versions dépendances/plugins).
- `gradlew` / `gradlew.bat` + `gradle/wrapper/*` : Gradle Wrapper (indispensable pour cloner/build ailleurs).

### `app/`
- `app/src/main/AndroidManifest.xml` : permissions + déclaration du provider + launcher activity.
- `MainActivity.kt` : USB + parsing + location + stockage Room + notifyChange.

### `appmap/`
- `appmap/src/main/AndroidManifest.xml` : permissions + launcher activity (MapActivity).
- `MapActivity.kt` : carte OSM + lecture provider + overlay markers.

### `core/`
- `core/src/main/java/com/example/core/db/*` : DB Room et repository.
- `core/src/main/AndroidManifest.xml` : volontairement “vide” (commentaire important sur le provider).

---

## Additif : clonage sur une autre machine (Android Studio)

### Objectif
Pouvoir cloner le dépôt GitHub et l’ouvrir dans Android Studio **sans copier de fichiers locaux**.

### Ce qui est normal après clonage
- `local.properties` **n’est pas dans Git** (et ne doit pas y être). Android Studio le recrée avec `sdk.dir=...`.
- Les dossiers `**/build/**` ne sont **pas** versionnés (générés par Gradle).

### Prérequis côté PC
- Android Studio (recommandé)
- Android SDK installé (Android Studio le gère)
- JDK : idéalement celui intégré à Android Studio (`jbr`)

### 1) Cloner
```powershell
git clone https://github.com/patrickdcp77/android-usb-dht-carte.git
cd android-usb-dht-carte
```

### 2) Ouvrir dans Android Studio
- `File > Open…` puis sélectionner le dossier du projet.
- Attendre la synchro Gradle.

### 3) Si Gradle affiche “JAVA_HOME is not set” (Windows)
Sur certaines machines/terminaux, Gradle en ligne de commande a besoin de `JAVA_HOME`.

Exemple (session courante PowerShell) :
```powershell
$Env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$Env:JAVA_HOME\bin\java.exe" -version
```

Pour le rendre permanent (optionnel) :
```powershell
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
```

### 4) Build rapide (sans Android Studio)
```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :appmap:assembleDebug
```

### 5) Lancer sur smartphone
- Branche le téléphone (mode développeur + débogage USB)
- Dans Android Studio, sélectionne **quelle app lancer** (`:app` ou `:appmap`) puis Run.

---

## .gitignore (important)
Ton `.gitignore` ignore déjà les essentiels :
- `local.properties`
- `**/build/`

Si tu vois des fichiers `build/**` dans GitHub, c’est qu’ils ont été commit avant d’être ignorés.
La correction est alors : `git rm -r --cached <module>/build` + commit.

---

## Dépannage rapide

- **Carte OK mais “Mesures: 0”** :
  - vérifier que l’app capteur a bien enregistré des mesures (ouvrir `:app` et attendre)
  - vérifier que le provider est déclaré **uniquement** dans `:app`.

- **Points invisibles sur la carte** :
  - vérifier qu’il y a des mesures
  - vérifier que les points ont une taille d’icône suffisante (dans `MapActivity` on fixe la taille du cercle).

- **L’app capteur ne “log” pas en arrière-plan** :
  - actuellement l’app enregistre quand le flux USB/l’app est active.
  - un vrai logging arrière-plan nécessitera un Service/Foreground Service/WorkManager (à faire plus tard).

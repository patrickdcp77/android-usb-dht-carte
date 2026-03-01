# Checklist de reproduction (imprimable) — Android USB DHT + GPS + Carte OSM

Objectif : reproduire le projet rapidement sur une autre machine et éviter les pièges classiques.

> Durée cible : ~10 minutes (hors téléchargements Android Studio/SDK).

---

## 0) Pré-requis (à cocher)

- [ ] Android Studio installé
- [ ] Connexion Internet OK (pour dépendances Gradle)
- [ ] Smartphone Android + câble USB
- [ ] (Pour `:app`) Câble **USB OTG** + ESP32/ESP8266 + capteur DHT

---

## 1) Récupérer le projet

- [ ] Cloner :

```bash
git clone https://github.com/patrickdcp77/android-usb-dht-carte.git
cd android-usb-dht-carte
```

ou télécharger le ZIP depuis GitHub.

---

## 2) Ouvrir dans Android Studio

- [ ] `File > Open…` → sélectionner le dossier du projet
- [ ] Attendre la synchro Gradle (1ère fois = plus long)

> Normal : `local.properties` est recréé automatiquement par Android Studio.

---

## 3) (Windows) Si Gradle se plaint de JAVA_HOME

- [ ] Tester Java (session courante) :

```powershell
$Env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$Env:JAVA_HOME\bin\java.exe" -version
```

- [ ] Rebuild :

```powershell
.\gradlew.bat :app:compileDebugKotlin :appmap:compileDebugKotlin
```

---

## 4) Installer et exécuter les 2 apps

### A) Choisir quelle app lancer

- [ ] Pour l’app capteur : exécuter le module **`:app`**
- [ ] Pour l’app carte : exécuter le module **`:appmap`**

> Dans Android Studio : choisir la configuration Run/Debug (`app` ou `appmap`) puis Run.

### B) À quoi s’attendre

- [ ] `:appmap` fonctionne très bien sur émulateur (carte + GPS simulé)
- [ ] `:app` (USB OTG) nécessite idéalement un **vrai smartphone**

---

## 5) Préparer le smartphone (recommandé)

- [ ] Activer la localisation (GPS)
- [ ] Autoriser la localisation pour les apps
- [ ] (Si install depuis Android Studio) activer **Options développeur** + **Débogage USB**

---

## 6) Tester l’app capteur (`:app`)

- [ ] Brancher ESP + capteur via **USB OTG**
- [ ] Ouvrir l’app capteur
- [ ] Accepter la permission USB
- [ ] Vérifier Temp/Hum + Lat/Lon
- [ ] Attendre **au moins 10 secondes** (rate limiter) pour qu’une mesure soit persistée

Piège courant : si pas de location (Lat/Lon `--`), aucune mesure n’est enregistrée.

---

## 7) Tester l’app carte (`:appmap`)

- [ ] Ouvrir l’app carte
- [ ] Vérifier `Location: OK`
- [ ] Appuyer sur **Rafraîchir**
- [ ] Vérifier `Mesures: > 0`
- [ ] Vérifier : points visibles (dernier rouge, autres verts)

---

## 8) Pièges à éviter (les 3 classiques)

### 8.1 `Mesures: 0` dans l’app carte
- [ ] Vérifier que le **ContentProvider** est déclaré **uniquement** dans `app/src/main/AndroidManifest.xml`.
- [ ] Ne pas déclarer le provider dans `:core` (sinon DB vide côté `:appmap`).

### 8.2 ADB ne voit pas le téléphone
- [ ] Utiliser le chemin complet :

```powershell
$adb = "C:\Users\patri\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```

### 8.3 Build/ artefacts dans Git
- [ ] Vérifier que `**/build/` est ignoré
- [ ] Si déjà committé : `git rm -r --cached <module>/build`

---

## 9) Validation rapide (build)

- [ ] Exécuter :

```powershell
.\gradlew.bat :app:assembleDebug :appmap:assembleDebug
```

---

## 10) Point “prochaines améliorations” (si tu veux industrialiser)

- [ ] Logging en arrière-plan (Foreground Service)
- [ ] Export CSV / partage
- [ ] Mode “données simulées” pour tests émulateur
- [ ] Clustering des points sur carte


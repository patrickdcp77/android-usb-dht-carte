# Projet Android : capteur DHT en USB + géolocalisation + carte OpenStreetMap

> **Objectif** : reproduire une solution qui enregistre des mesures de température/humidité toutes les 10 secondes (max 3000 dernières), avec **coordonnées GPS**, et qui affiche ces mesures sur une carte **OpenStreetMap**.

---

## Sommaire (liens rapides)

- [1. Ce que fait le projet](#1-ce-que-fait-le-projet)
- [2. Ce qu’il te faut (matériel)](#2-ce-quil-te-faut-matériel)
- [3. Ce qu’il te faut (logiciels)](#3-ce-quil-te-faut-logiciels)
- [4. Architecture (simple)](#4-architecture-simple)
- [5. Installation sur ton PC](#5-installation-sur-ton-pc)
- [6. Installation sur le smartphone](#6-installation-sur-le-smartphone)
- [7. Utilisation – App Capteur](#7-utilisation--app-capteur)
- [8. Utilisation – App Carte](#8-utilisation--app-carte)
- [9. FAQ / Dépannage](#9-faq--dépannage)
- [10. Sécurité & Permissions Android](#10-sécurité--permissions-android)

---

## 1. Ce que fait le projet

Le dépôt contient **2 applications Android** (dans un seul projet Android Studio) :

- **App capteur** (`:app`) :
  - lit un périphérique **USB-Serial** via un câble **OTG**
  - récupère la **position GPS** du smartphone
  - affiche Temp/Hum + Lat/Lon
  - enregistre les mesures en base locale (Room)
  - notifie l’app carte qu’il y a de nouvelles données

- **App carte** (`:appmap`) :
  - affiche une **carte OpenStreetMap (osmdroid)**
  - lit les mesures via un mécanisme Android standard (**ContentProvider**)
  - place des points sur la carte : **dernier point rouge**, anciens en **vert**
  - clic sur un point = détail (date/heure + valeurs)

Le stockage et les modèles de données sont dans un module partagé :
- **Module partagé** (`:core`) : base de données Room + modèles (entité/DAO/repository)

---

## 2. Ce qu’il te faut (matériel)

### Checklist « panier d’achat » (recommandée)

> Objectif : avoir un montage simple, fiable, facilement reproductible.

#### A) Microcontrôleur
Choisis **un seul** des deux :

1) **ESP32 DevKit** (recommandé)
- Exemple : *ESP32 DevKit V1 (WROOM-32)*
- Avantages : robuste, très courant, USB intégré sur beaucoup de cartes

2) **ESP8266 NodeMCU** (ok aussi)
- Exemple : *NodeMCU v2/v3 (ESP8266)*

#### B) Capteur température/humidité
Choisis **un seul** :

1) **DHT22 / AM2302** (recommandé)
- Plus précis que DHT11

2) **DHT11**
- Moins précis mais suffisant pour test

#### C) Câbles et adaptation smartphone
- **Câble USB OTG** adapté à ton téléphone :
  - Téléphone USB‑C → **USB‑C OTG → USB‑A femelle** (le plus courant)
  - Téléphone micro‑USB → **micro‑USB OTG → USB‑A femelle**
- **Câble USB (data)** entre l’OTG et la carte ESP (souvent USB‑A → micro‑USB / USB‑C)

> Important : certains câbles « charge only » ne transmettent pas les données. Prends un câble data.

#### D) Alimentation (si nécessaire)
- Sur certains téléphones, l’OTG n’alimente pas correctement la carte + capteur.
- Solution possible : **hub OTG alimenté** (si tu as des déconnexions).

#### E) (Optionnel mais conseillé) Boîtier / protection
- petite boîte + passe-câbles
- éviter l’oxydation si usage extérieur

---

## 3. Ce qu’il te faut (logiciels)

- **Android Studio** (sur PC)
- Connexion Internet (premier build)
- Un smartphone Android (pour tester l’USB)

---

## 4. Architecture (simple)

### Schéma ASCII

```
[Capteur DHT] -> [ESP32/ESP8266] -> (USB OTG) -> App Capteur (:app)
                                             |
                                             | (GPS)
                                             v
                                      Base locale (Room)
                                             |
                                             v
                          ContentProvider (exposé par l’app capteur)
                                             |
                                             v
                                  App Carte (:appmap) -> OSM
```

### Format des trames attendues
Le microcontrôleur envoie des lignes texte, par exemple :

- `T=23.4;H=45.6\n`

---

## 5. Installation sur ton PC

### A) Télécharger / cloner le projet

Si tu as Git :

```bash
git clone https://github.com/patrickdcp77/android-usb-dht-carte.git
cd android-usb-dht-carte
```

Sinon :
- bouton GitHub **Code → Download ZIP**
- dézipper dans un dossier

### B) Ouvrir dans Android Studio
- Android Studio → **File → Open…**
- sélectionner le dossier du projet
- attendre la synchronisation Gradle

---

## 6. Installation sur le smartphone

### A) Activer les options développeur
(Si tu veux installer via Android Studio)
- Paramètres → À propos du téléphone → tapoter 7 fois « Numéro de build »
- Options développeur → activer **Débogage USB**

### B) Autoriser la localisation
- Autoriser la localisation « pendant l’utilisation »
- Activer GPS/Localisation dans les réglages

---

## 7. Utilisation – App Capteur

1) Brancher le montage via **USB OTG**
2) Ouvrir **App capteur**
3) Accepter la permission USB
4) Vérifier que l’écran affiche :
   - Température / humidité
   - Latitude / longitude

### Enregistrement automatique
- La mesure est enregistrée au maximum **toutes les 10 secondes** (rate limiter)
- L’historique est limité à **3000** mesures

---

## 8. Utilisation – App Carte

1) Ouvrir **App carte**
2) Autoriser la localisation si demandé
3) Appuyer sur **Rafraîchir**
4) Vérifier :
   - la carte est centrée sur ta position
   - des points apparaissent :
     - dernier point **rouge**
     - anciens **verts**

---

## 9. FAQ / Dépannage

### Q1. Je vois la carte mais « Mesures: 0 »
- Ouvre l’app capteur, attend au moins 10 secondes.
- Reviens sur l’app carte et appuie sur **Rafraîchir**.

### Q2. Pas de latitude/longitude
- Active la localisation du téléphone.
- Autorise la permission.
- Attends un « fix » GPS (intérieur = parfois long).

### Q3. L’app capteur ne log pas en arrière-plan
- Android peut suspendre l’app.
- Pour un logging permanent, il faudra une évolution (Foreground Service / WorkManager).

---

## 10. Sécurité & Permissions Android

### Pourquoi l’app demande la localisation ?
Parce que chaque mesure est enregistrée avec ses coordonnées.

Permissions :
- `ACCESS_COARSE_LOCATION` : localisation approximative
- `ACCESS_FINE_LOCATION` : localisation précise (GPS)
- `ACCESS_BACKGROUND_LOCATION` : **optionnelle** (utile seulement si tu veux enregistrer en arrière-plan)

### Pourquoi l’app demande l’USB ?
Parce qu’un périphérique USB (OTG) est branché.

Bonnes pratiques appliquées :
- permission USB demandée explicitement
- receiver `registerReceiver` avec flags sur Android 13+

### Internet (app carte)
- l’app carte charge les tuiles OpenStreetMap → besoin de :
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`

### Partage des données entre les 2 apps
- l’app capteur expose un **ContentProvider** (lecture seule) via :
  - `content://com.example.myapplication.measurements/latest?limit=3000`

Recommandations si tu industrialises :
- limiter l’exposition (app signature, permission, ou provider non-exported + mécanisme alternatif)
- documenter le contrat d’URI

---

# FAQ (format BetterDocs)

## Je peux utiliser Google Maps au lieu d’OpenStreetMap ?
Oui, mais Google Maps nécessite une clé API et une intégration différente.

## Je peux exporter les mesures en CSV ?
Oui (évolution facile) : export Room → fichier CSV.

## Je peux ajouter d’autres capteurs ?
Oui : étendre la trame (`T=…;H=…;P=…`) + adapter le parsing.


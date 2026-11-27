# Prototype S.O.S (jalon J3)

Ce prototype jetable prouve, sur de vrais téléphones, les points durs du S.O.S décrits dans le
cahier des charges (section 6, exigences EF-SOS-03 à 08, risques R1, R2 et R12) avant d'écrire
le vrai module. Il vit dans `prototype/sos`, comme une application à part
(`fr.cocooning.prototype.sos`, nom affiché « Proto S.O.S »). Après le J3, on supprime le dossier
et la ligne `include(":prototype:sos")` de `settings.gradle.kts`, rien d'autre ne dépend de lui.

## Ce que fait le prototype

| Côté | Ce qui est testé | Fichier |
|---|---|---|
| Récepteur | Écoute permanente d'un sujet ntfy par un service au premier plan (voie sans Google, plan B /e/OS) | `EcouteNtfyService.kt` |
| Récepteur | Message FCM de données en priorité haute (voie Google), si Firebase est configuré | `ProtoFirebaseService.kt` |
| Récepteur | Volume alarme au maximum, sortie du silencieux ou du vibreur, son en boucle sur le flux alarme, vibration, notification plein écran sur l'écran verrouillé, accusés « reçue » et « vue » | `SonnerieService.kt`, `AlerteActivity.kt` |
| Récepteur | Photographie de tous les réglages qui décident si l'alerte sonne, prise à chaque réception | `Diagnostic.kt` |
| Les deux | Assistant de réglages : chaque autorisation lisible avec un bouton « Régler » | `MainActivity.kt` |
| Émetteur | Envoi immédiat avec la dernière position connue, puis position fraîche, attente des accusés 60 s, SMS de secours ou 112 | `EnvoiAlerte.kt` |

Le simulateur remplace le serveur : `prototype/sos/simulateur/simuler_alerte.ps1` publie une
alerte sur le sujet ntfy et attend les accusés comme le ferait l'émetteur ;
`envoyer_fcm.py` envoie un message FCM de données en priorité haute (ou normale, pour comparer).

Le journal de l'appli (section 5 de l'écran) garde chaque événement avec l'heure ; il est aussi
écrit dans logcat sous l'étiquette `SOSPROTO`. Sur un APK de débogage, on le relit depuis le PC :

```powershell
adb shell run-as fr.cocooning.prototype.sos cat shared_prefs/prototype_sos.xml
```

### Confidentialité pendant les essais

Par défaut, le prototype passe par le serveur public `ntfy.sh`. Le sujet (« code commun ») est
tiré au hasard sur 16 caractères et sert de secret partagé ; ntfy.sh garde les messages environ
12 heures. Les messages ne contiennent qu'un identifiant, le nom du téléphone et le niveau de
batterie. **La position n'est pas envoyée à ntfy.sh** sauf si l'on coche « Joindre la position »
(désactivé par défaut) ; elle part seulement dans le SMS de secours, de téléphone à téléphone.
Le serveur ntfy se change dans l'écran (par exemple un ntfy auto hébergé sur le Proxmox).

## Installer et tester

Compiler et installer (le JBR complet est dans `Android Studio1`) :

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat :prototype:sos:assembleDebug
```

```powershell
adb install -r prototype\sos\build\outputs\apk\debug\sos-debug.apk
```

Sur le téléphone récepteur :

1. Ouvrir Proto S.O.S, section 3 : toucher « Régler » sur chaque ligne marquée ✗.
2. Après l'accès Ne pas déranger, toucher « Recréer le canal S.O.S » (voir plus bas pourquoi).
3. Réglages constructeur à la main (voir le tableau par constructeur).
4. Section 2 : activer « Écoute ntfy permanente ».
5. Sur l'autre téléphone, saisir le même code commun (bouton « Partager le code »).

Lancer une alerte depuis le PC, téléphone verrouillé :

```powershell
powershell -ExecutionPolicy Bypass -File prototype\sos\simulateur\simuler_alerte.ps1 -Sujet <code commun>
```

Le bouton « Essai local » fait sonner le téléphone lui-même 15 s plus tard, sans réseau : le
temps de le verrouiller. Il ne teste que la sonnerie, pas la réception.

### Matrice d'essais

À rejouer sur chaque téléphone ; chaque ligne se lit dans le journal (« ALERTE ... reçue par ...
en x s » suivi du diagnostic, puis « Son lancé », « Écran d'alerte affiché »).

| # | Situation | Attendu |
|---|---|---|
| 1 | Déverrouillé, mode sonnerie | son, écran d'alerte |
| 2 | Verrouillé, silencieux | son fort, plein écran |
| 3 | Verrouillé, vibreur | son fort, plein écran |
| 4 | Ne pas déranger par défaut | son (exception Alarmes) |
| 5 | Ne pas déranger, exception Alarmes retirée | dépend de l'accès Ne pas déranger (voir résultats) |
| 6 | Ne pas déranger, aucune exception (silence total) | dépend de l'accès Ne pas déranger |
| 7 | Mode économie d'énergie, verrouillé 1 h | reçue en moins de 10 s |
| 8 | Verrouillé et inutilisé une nuit (plus de 8 h) | reçue en moins de 10 s, nombre de coupures d'écoute |
| 9 | Émetteur : alerte sans « J'ai vu » | SMS de secours proposé à 60 s, lien de carte valide |
| 10 | Émetteur : position | précision et délai du premier point, en extérieur et en intérieur |

## Résultats

### Émulateur Pixel 7, Android 17 (API 37), 27/11/2026

Essais faits depuis le PC, écran éteint, application en arrière-plan, sans exemption batterie.

| Situation | Accès Ne pas déranger | Résultat |
|---|---|---|
| Ne pas déranger prioritaire, silencieux | non | **Sonne** (flux alarme, non coupé), plein écran affiché, volume passé au maximum |
| Veille profonde forcée (`deviceidle force-idle`) | non | **Reçue** sans délai par la connexion ntfy déjà ouverte : un service au premier plan garde le réseau en Doze ; la sonnerie démarre |
| Silence total | non | Reçue mais **muette** (flux coupé par le système, volume bloqué à 0) et **pas de plein écran** |
| Silence total | oui, canal recréé | **Sonne fort** et plein écran : `setRingerMode(NORMAL)` fait sortir le téléphone du Ne pas déranger (voir plus bas) |
| Accusés | n'importe | « reçue » renvoyée aussitôt ; « vue » arrive à l'émetteur après le toucher de « J'ai vu » |

### Samsung Galaxy S25+, One UI 9, Android 17 (API 37)

| Situation | Résultat |
|---|---|
| Essai local, vibreur, accès Ne pas déranger et exemption batterie accordés | **Sonne** : mode vibreur repassé en sonnerie, volume alarme passé de 3 à 15/15, « J'ai vu » coupe le son |
| Canal créé sans l'accès Ne pas déranger | `mBypassDnd=false` (relevé par `dumpsys notification`) : hypothèse du cahier confirmée |
| Par ntfy, silencieux, Ne pas déranger prioritaire (alarmes permises), 27/11 18:23 | **Sonne** : reçue en 0,5 s, mode sonnerie rétabli, volume 15/15. **Le Ne pas déranger reste actif** : contrairement à l'émulateur, One UI ne le lève pas avec `setRingerMode` |
| Par ntfy, silencieux, Ne pas déranger désactivé, 27/11 18:25 | **Sonne** : reçue en 0,8 s |
| Émetteur, position | point frais en 1,2 s, ±14 m (fournisseur `fused`) ; accusés « reçue » 0,5 s et « vue » 4,9 s |
| Silence total, nuit complète, économie d'énergie | à faire |

### OPPO Reno 8 Lite, ColorOS 12

À faire : installer l'APK (câble USB ou copie du fichier), même matrice. C'est le téléphone le
plus exposé au risque R1.

## Ce qu'on a appris

**Confirmé**

- Le flux alarme (`USAGE_ALARM`) n'est coupé ni par le silencieux ni par le vibreur, ni par un Ne
  pas déranger qui garde l'exception Alarmes (réglage par défaut). C'est le levier principal,
  comme les réveils et les antivols.
- `setBypassDnd(true)` n'est retenu que si l'appli a déjà l'accès Ne pas déranger quand le canal
  est créé. Un canal existant ne se modifie pas : il faut le supprimer et en créer un sous un
  nouvel identifiant après l'octroi de l'accès. Le vrai assistant devra le faire tout seul.
- Avec l'accès Ne pas déranger, `setRingerMode(RINGER_MODE_NORMAL)` reste permis sur Android 17
  et sort le téléphone du silencieux ou du vibreur.
- Un service au premier plan avec une connexion ouverte reçoit l'alerte même en veille profonde
  forcée, et peut démarrer le service de sonnerie depuis l'arrière-plan.
- Sans l'accès plein écran (`canUseFullScreenIntent`), l'alerte n'est qu'une bannière ; sur
  Android 11 à 13, l'accès est accordé d'office.

**Découverte à trancher (ADR)** : sur l'émulateur Android 17, `setRingerMode(NORMAL)` avec l'accès
Ne pas déranger **désactive aussi le Ne pas déranger de l'utilisateur, même en silence total**.
C'est plus que ce que le cahier croyait possible depuis Android 15 (6.2 « Ce qui ne marche
plus »). C'est efficace pour un S.O.S, mais c'est un effet de bord fort, et le rétablissement
n'est pas exact : `setInterruptionFilter` crée une règle propre à l'appli, et le silence total
d'origine revient en « prioritaire ». Sur le S25+ (One UI 9), le même appel **ne lève pas** le
Ne pas déranger prioritaire : le comportement dépend donc du constructeur. Silence total sur
One UI encore à essayer.

**Ne marche pas, ou pas sans geste de l'utilisateur**

- Silence total sans accès Ne pas déranger : ni son, ni plein écran. Cocooning ne peut rien y faire.
- `setStreamVolume` ne peut pas remonter le flux alarme tant que le silence total est actif ;
  l'ordre compte : mode sonnerie d'abord, volume ensuite.
- L'appli ne peut pas lire les exceptions Ne pas déranger par appli de One UI ni la liste
  « Applications jamais en veille » : l'assistant ne peut que guider et proposer un essai.

**Pas encore mesuré** : voie FCM (projet Firebase à créer), priorité haute déclassée en normale,
nuit complète sur One UI et ColorOS, délai et précision du premier point GPS.

## Réglages à demander à l'installation

| Réglage | Côté | Lisible par l'appli | Écran ouvert par l'assistant |
|---|---|---|---|
| Notifications (`POST_NOTIFICATIONS`) | les deux | oui | demande système |
| Plein écran (`USE_FULL_SCREEN_INTENT`, Android 14+) | récepteur | oui | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` |
| Accès Ne pas déranger | récepteur | oui | `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`, puis recréation du canal |
| Canal S.O.S « Ignorer Ne pas déranger » | récepteur | oui | réglages du canal |
| Exemption d'optimisation batterie | les deux | oui | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Arrière-plan non restreint | les deux | oui | infos de l'appli |
| Position exacte | émetteur | oui | demande système, choisir « Position exacte » |
| SMS direct (`SEND_SMS`, facultatif) | émetteur | oui | demande système ; sinon appli SMS préremplie |
| Exception Cocooning dans chaque mode Ne pas déranger | récepteur | **non** | réglages Ne pas déranger |
| Exception Alarmes du Ne pas déranger | récepteur | **non** (sauf en mode prioritaire) | réglages Ne pas déranger |
| Réglages constructeur (tableau suivant) | les deux | **non** | infos de l'appli, avec texte guidé |

Ce qui n'est pas lisible se vérifie par l'essai hebdomadaire en mode TEST (cahier 6.5).

## Ce qui change par constructeur

| Constructeur | Réglages en plus | Remarques |
|---|---|---|
| Samsung One UI | Batterie > Limites d'utilisation en arrière-plan > « Applications jamais en veille » ; désactiver « Mettre en veille les applis inutilisées » ; exception de l'appli dans Ne pas déranger > Applications | Mode économie d'énergie et veille des applis inutilisées à mesurer (essais 7 et 8) |
| OPPO ColorOS 12 | Gestion des applis > Proto S.O.S : « Démarrage automatique » et « Autoriser l'activité en arrière-plan » ; Batterie : ne pas optimiser ; verrouiller l'appli dans les applis récentes ; notifications sur l'écran verrouillé et en bannière | Réputé le plus agressif (dontkillmyapp.com) ; Android 11 ou 12, donc plein écran accordé d'office, pas de types de service au premier plan |
| Google Pixel, Android d'origine | rien de plus que la liste générale | Comportement de l'émulateur ci-dessus |
| /e/OS (Fairphone, R12) | pas de FCM fiable (microG) : voie ntfy seule | La voie ntfy du prototype est le « dernier recours » d'ENF-EVO-03 ; un vrai distributeur UnifiedPush reste à essayer |

Les chemins ColorOS sont tirés de la documentation publique et seront corrigés après l'essai sur
l'OPPO.

## Activer la voie FCM (facultatif)

1. Créer un projet sur console.firebase.google.com et y ajouter une appli Android
   `fr.cocooning.prototype.sos`.
2. Dans le `google-services.json` téléchargé, relever `current_key`, `mobilesdk_app_id`,
   `project_id` et `project_number`, et les écrire dans `local.properties` (jamais versionné) :

   ```properties
   prototypeSos.firebase.apiKey=...
   prototypeSos.firebase.appId=...
   prototypeSos.firebase.projectId=...
   prototypeSos.firebase.senderId=...
   ```

3. Recompiler et réinstaller ; le jeton FCM s'affiche dans la section 2 (bouton « Copier »).
4. Générer une clé de compte de service (Paramètres du projet > Comptes de service), la garder
   hors du dépôt, puis :

   ```powershell
   pip install google-auth requests
   ```

   ```powershell
   python prototype\sos\simulateur\envoyer_fcm.py --cle C:\chemin\cle.json --jeton <jeton>
   ```

Le journal note la priorité reçue et la priorité demandée : un écart signifie que FCM a déclassé
le message, ce qui supprimerait l'exemption de démarrage en arrière-plan.

## Limites du prototype

Pas de vrai serveur, pas de chiffrement, pas de suivi de position toutes les 10 s ni de service
de localisation au premier plan (EF-SOS-04), pas de code ni de biométrie pour arrêter l'alerte
(EF-SOS-09), sonnerie limitée à 5 minutes, interface sans le design système. Les alertes de
plus de 15 minutes rattrapées après une coupure sont journalisées sans sonner.

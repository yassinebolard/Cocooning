# ADR 0003 : architecture de la messagerie chiffrée de bout en bout

- Statut : **acceptée** par la MOA (Yassine) le 27/11/2026 : libsignal avec la licence AGPL-3.0
  (question 1), sauvegarde de l'historique sans les clés de messagerie (question 2)
- Date : 27/11/2026
- Exigences : EF-MSG-01 à 11, ENF-SEC-01 à 08, EF-CPT-04, EF-CPT-08, EF-CPT-09, ENF-RGPD-04,
  ENF-EVO-01 à 03, section 10 (limites des médias)
- Code : module `:core:messagerie` (premier jalon : modèle et domaine des messages texte)

## Contexte

La messagerie est la fonction la plus exposée de Cocooning : tout ce que le couple s'écrit passe
par le serveur personnel (Ktor sur un LXC Proxmox, derrière Nginx Proxy Manager et Cloudflare).
Le cahier des charges impose que ni le serveur ni Cloudflare ne voient le contenu (ENF-SEC-01,
ENF-SEC-05), que les notifications n'en portent aucun (ENF-SEC-07), que l'appli marche hors ligne
(EF-MSG-10) et que rien ne suppose un partenaire unique, pour la V2 famille (ENF-EVO-02).

Déjà en place : bases Room chiffrées par SQLCipher avec phrase enveloppée par le Keystore
(`BaseChiffree`), préférences chiffrées, sauvegarde par phrase de récupération (ADR 0002),
composants `MessageBubble` et `MessageComposer` dans le design système.

## Décision

### 1. Protocole de chiffrement : protocole Signal avec libsignal

- **Recommandé : libsignal** (`org.signal:libsignal-android`, bibliothèque officielle de Signal).
  Échange de clés PQXDH (résiste aux futurs ordinateurs quantiques), puis Double Ratchet : chaque
  message a sa propre clé, une clé volée ne déchiffre ni le passé ni l'avenir (confidentialité
  persistante et auto-réparation).
- Écrire soi-même un Double Ratchet avec Tink est écarté : c'est exactement le genre de code où
  une erreur subtile ruine la sécurité sans que rien ne casse à l'écran. Tink seul, sans ratchet
  (une paire de clés fixe par téléphone), perd la confidentialité persistante : une clé volée
  ouvre tout l'historique intercepté.
- MLS (RFC 9420) est écarté : pensé pour les grands groupes, sans bibliothèque Android mûre.
- **Contrepartie : licence AGPL-3.0.** Le dépôt est déjà public et l'appli n'est pas diffusée
  (pas de Play Store) : il suffit de placer le code de Cocooning sous AGPL-3.0, ce qui ne change
  rien à l'usage du couple. Si un jour Cocooning était vendu en code fermé, il faudrait changer
  de bibliothèque. **Question 1 pour la MOA.**
- Poids : environ 5 à 10 Mo de bibliothèque native par architecture (à mesurer) ; on ne livre
  que `arm64-v8a` (S25+ et OPPO Reno 8 Lite) et `x86_64` (émulateur).
- Le serveur ne dépend pas de libsignal : il range des paquets de clés publiques et des blobs.

### 2. Clés par appareil

- Chaque téléphone a sa propre identité Signal (paire de clés Curve25519), créée à l'installation.
  Le serveur connaît des **appareils** rattachés à des **membres** d'un **espace** : un membre a
  un appareil actif en V1, le modèle en accepte plusieurs (ENF-EVO-01).
- Le téléphone publie sur le serveur sa clé d'identité publique, une pré-clé signée (renouvelée
  chaque semaine), une pré-clé post-quantique (Kyber) et 100 pré-clés à usage unique, rechargées
  sous 20.
- Code de sécurité (EF-CPT-04) : les 60 chiffres de Signal (« safety number ») calculés depuis
  les deux clés d'identité, affichés en chiffres et en QR code à scanner l'un chez l'autre.
- **Écart assumé avec ENF-SEC-02** : l'Android Keystore ne sait pas faire les calculs Curve25519
  de libsignal, la clé privée doit donc passer en mémoire. Elle est stockée dans une base SQLCipher
  dédiée, `cocooning_signal.db`, dont la phrase est elle-même enveloppée par le Keystore (même
  mécanisme que les autres bases). La clé ne sort pas du téléphone en clair.

### 3. Lien avec la sauvegarde de clé (ADR 0002)

- **Recommandé : la sauvegarde ne contient pas les clés Signal.** Elle contient l'historique
  (`cocooning_messagerie.db`) mais jamais `cocooning_signal.db`. Sur un nouveau téléphone,
  restauré par la phrase ou par QR code, une nouvelle identité est créée, l'ancien appareil est
  déconnecté, et le partenaire voit « Le code de sécurité de Yassine a changé » : c'est
  exactement ce que demande EF-CPT-09.
- Raison : remettre un état de ratchet ancien (la sauvegarde a jusqu'à 24 heures) désynchronise
  les sessions, et une clé d'identité qui vit dans un fichier exporté peut être volée avec lui.
  Signal fait le même choix.
- Conséquence visible : un message envoyé à l'ancien téléphone après sa dernière sauvegarde et
  jamais ouvert dessus est perdu (il était chiffré pour l'ancienne identité). L'appli de l'autre
  côté le renverra automatiquement s'il n'a pas été accusé reçu. **Question 2 pour la MOA.**

### 4. Format des messages

- Identifiant de message : UUID créé sur le téléphone de l'auteur, avant tout envoi. Il sert de
  clé d'idempotence : un renvoi après coupure ne crée jamais de doublon, ni sur le serveur ni chez
  le destinataire.
- Contenu en clair (avant chiffrement) : un objet versionné sérialisé en Protocol Buffers
  (`kotlinx-serialization-protobuf`), compact et extensible sans casser les anciennes versions.
  Champs : `version`, `id`, `conversation`, `auteur`, `creeLe` (horloge de l'auteur), et un seul
  contenu parmi : texte, pièce jointe (vocal, image, GIF, vidéo), réaction, suppression pour
  tous, accusé (reçu, lu), en train d'écrire. `reponseA` (identifiant du message cité) est commun.
- Texte limité à 10 000 caractères (paramètre de `config/cocooning.properties`, ENF-CONF-01).
- L'enveloppe vue par le serveur ne contient que : appareil destinataire, type Signal (pré-clé ou
  message), blob chiffré, identifiant opaque. Le serveur ne voit ni le type de contenu, ni la
  conversation, ni la citation. Le contenu chiffré est complété à une taille multiple de 160
  octets, comme Signal, pour ne pas trahir la longueur exacte d'un texte.
- Diffusion : l'auteur chiffre une copie par appareil destinataire (sessions deux à deux). Cela
  suffit pour un couple et pour une famille de quelques personnes ; les « Sender Keys » de
  libsignal resteront possibles en V2 si les groupes grandissent.

### 5. Médias (vocal, images, GIF, vidéo)

- Compression sur le téléphone selon la section 10 du cahier, puis chiffrement avec une clé
  aléatoire de 32 octets par fichier (ENF-SEC-03).
- Chiffrement par segments de 64 Kio en AES-256-GCM (construction STREAM : nonce = préfixe
  aléatoire, compteur, drapeau « dernier segment »). On chiffre et déchiffre au fil de l'eau une
  vidéo de 100 Mo sans la charger en mémoire, et un segment tronqué ou déplacé est refusé.
- Envoi par morceaux de 8 Mo avec reprise (limite de 100 Mo par requête de Cloudflare) ; le
  serveur range le blob sous un identifiant aléatoire sur son disque de 60 Go.
- Le message chiffré porte : identifiant du blob, clé, empreinte SHA-256 du blob chiffré, type
  MIME, taille, durée ou dimensions, et une vignette chiffrée de moins de 8 Ko pour afficher
  l'aperçu tout de suite.
- Suppression du blob sur le serveur 30 jours après sa réception par tous les appareils
  destinataires (ENF-RGPD-04), quotas de la section 10 (80 % et 95 %).
- Recherche de GIF par une API tierce : plus tard, en option, avec l'avertissement d'EF-MSG-04.

### 6. Accusés et indicateur d'écriture

- **Envoyé** : le serveur a accepté le message (réponse HTTP avec l'identifiant). **Reçu** : le
  téléphone destinataire l'a déchiffré et renvoie un accusé chiffré. **Lu** : la conversation a été
  affichée à l'écran, accusé chiffré aussi. Le serveur ne sait donc pas qui a lu quoi.
- Statut d'un message pour N destinataires : le plus faible des statuts (« lu » quand tous ont lu).
  Un statut ne recule jamais : un « reçu » arrivé après un « lu » est ignoré.
- Accusés de lecture et indicateur d'écriture actifs par défaut, désactivables dans les réglages,
  de façon réciproque comme dans les messageries courantes : qui ne les envoie pas ne les voit pas.
- « En train d'écrire » : chiffré, envoyé seulement par WebSocket quand les deux applis sont
  ouvertes, jamais stocké ni poussé par notification, expire après 6 secondes.

### 7. Hors ligne et synchronisation

- Tout message écrit va d'abord dans la base locale avec l'état « en attente » : l'écran l'affiche
  aussitôt. Une tâche WorkManager contrainte au réseau envoie la file dans l'ordre, avec reprise
  exponentielle. Le chiffrement Signal a lieu au moment de l'envoi, pas de la saisie, car l'état
  de la session change entre-temps.
- Réactions, suppressions et accusés passent par la même file d'envoi (table de sortie) pour
  partir eux aussi au retour du réseau.
- Côté serveur, une boîte aux lettres par appareil avec un numéro d'ordre croissant. Le téléphone
  relève tout ce qui suit son dernier numéro (à l'ouverture de la WebSocket, au réveil par
  notification, et toutes les 15 minutes par WorkManager), puis acquitte : le serveur efface alors
  le message. Non relevé, il est effacé au bout de 30 jours.
- Affichage par heure de l'auteur (`creeLe`), avec l'heure d'arrivée locale en cas d'horloge
  déréglée (un message ne s'affiche jamais dans le futur). Le Double Ratchet accepte les messages
  dans le désordre.
- Recherche (EF-MSG-11) : index plein texte FTS dans la base SQLCipher, donc chiffré aussi.
- « Supprimer pour les deux » : seulement ses propres messages ; l'autre téléphone efface le
  contenu et garde la mention « Message supprimé ». C'est une demande : un téléphone trafiqué
  pourrait l'ignorer, l'écran le dira.

### 8. Notifications

- Couche abstraite à deux voies (ENF-EVO-03) : FCM en voie principale, UnifiedPush avec le ntfy
  du Proxmox en repli, comme le prototype S.O.S (ADR 0001).
- La notification ne porte que « réveille-toi » et un identifiant opaque (ENF-SEC-07) : FCM en
  priorité haute pour un message, rien pour un accusé ou l'indicateur d'écriture. Réveillée,
  l'appli relève sa boîte, déchiffre, puis affiche elle-même une notification locale, masquée sur
  l'écran verrouillé par défaut (« Nouveau message », EF-NTF-06).
- Canal Android « Messages » distinct du S.O.S, soumis à la plage « ne pas déranger Cocooning »
  (EF-NTF-05).

### 9. Stockage local et modules

- `:core:messagerie` : domaine pur (règles testées sans Android) et stockage Room dans
  `cocooning_messagerie.db`, chiffrée par `BaseChiffree`, incluse dans la sauvegarde EF-CPT-08.
- `cocooning_signal.db` (identité, pré-clés, sessions) dans un module à venir
  `:core:chiffrement`, exclue de la sauvegarde.
- `:feature:messages` : l'onglet Messages, avec `MessageBubble` et `MessageComposer`.
- Écrans de messagerie protégés par `FLAG_SECURE` (ENF-SEC-08).

## Conséquences

- Le serveur et Cloudflare voient seulement qui écrit à quel appareil, quand, et une taille
  arrondie. Masquer l'expéditeur (« sealed sender ») n'apporte rien tant que le serveur est celui
  du couple ; à revoir en V2 si d'autres familles l'utilisaient.
- La perte d'un téléphone sans sauvegarde récente perd les messages reçus depuis : c'est le prix
  de clés qui ne quittent pas le téléphone.
- libsignal ajoute du poids à l'APK et une licence AGPL (question 1).

## Premier jalon (fait)

Modèle Room et domaine des messages texte dans `:core:messagerie`, sans réseau ni chiffrement
Signal : écriture en file d'attente, états d'envoi, accusés par destinataire, réponse citée,
réactions, suppression pour soi ou pour les deux, réception idempotente. Les destinataires sont
une liste dès la V1 (ENF-EVO-02).

## Reste

1. Fichier `LICENSE` AGPL-3.0 à la racine du dépôt, avec l'arrivée de libsignal.
2. `:core:chiffrement` : libsignal, stockage des clés, code de sécurité, tests d'aller-retour.
3. Serveur Ktor : comptes et appareils, paquets de pré-clés, boîtes aux lettres, WebSocket,
   blobs par morceaux, purge à 30 jours.
4. Transport côté appli : file de sortie (réactions, suppressions, accusés), WorkManager,
   relève, notifications FCM et ntfy.
5. `:feature:messages` : écran de conversation, puis vocal, images, GIF, vidéo, recherche.

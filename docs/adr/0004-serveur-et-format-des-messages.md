# ADR 0004 : serveur Cocooning et format des messages

- Statut : **proposée** le 27/11/2026. Une question attend la MOA : comptes sans e-mail ni mot
  de passe (écart avec EF-CPT-01), recommandé et déjà codé.
- Exigences : EF-CPT-02, 03, 09 ; EF-MSG-01, 07, 10 ; ENF-SEC-05 à 07 et 11 ; ENF-RGPD-04 ;
  ENF-CONF-01 à 03 ; ENF-EVO-01 à 03.
- Code : `:core:protocole` (Kotlin pur, partagé), `:backend` (Ktor), `FormatMessages` dans
  `:core:messagerie`.

## Décision

- **Format des messages** : `ContenuClair` en Protocol Buffers (`:core:protocole`), un seul type
  par contenu (texte, réaction, suppression, accusé groupé, écriture). Un type inconnu d'une
  version future est ignoré. L'auteur n'est pas dans le contenu : c'est l'appareil dont la session
  Signal a déchiffré le message, impossible à falsifier.
- **Serveur** : Ktor 3.3.3 (dernière version compilée avec Kotlin 2.2, celui du projet), moteur
  CIO, SQLite en WAL par JDBC, sans ORM. Il ne voit que des métadonnées et des octets chiffrés.
- **Authentification par appareil** : paire EC P-256 dont la clé privée reste dans l'Android
  Keystore ; défi signé à usage unique (60 s) contre un jeton de 15 min (ENF-SEC-06), haché en
  mémoire. La clé de l'appareil joue le rôle du jeton de rafraîchissement.
- **Comptes** : code d'invitation de 8 caractères, à usage unique, 24 h ; 2 membres en V1.
  Nouveau téléphone : secret de récupération (32 octets, dans la sauvegarde EF-CPT-08, le serveur
  n'en garde que le SHA-256) ; l'ancien appareil est coupé et le nouveau reprend son numéro Signal.
- **Messages** : une boîte par appareil, numérotée ; dépôt idempotent par identifiant ; chaque
  membre visé doit être servi sur tous ses appareils actifs, sinon 409 avec la liste à corriger ;
  effacement à l'acquittement, ou après 30 jours.
- **Médias** : morceaux de 8 Mio avec reprise, purge 30 jours après réception par tous (90 jours
  au plus), blocage des envois à 95 % du quota, alerte à 80 %.
- **Temps réel et réveil** : WebSocket (annonce « nouveaux », éphémères chiffrés relayés sans
  stockage) ; hors ligne, réveil ntfy sans contenu. FCM attend le projet Firebase.
- **Configuration** : `config/server.conf` (ignoré par git), modèle commenté
  `config/server.conf.example` vérifié par un test ; secrets par variables d'environnement.

## Reste

Réponse de la MOA sur les comptes ; FCM côté serveur ; transport côté appli (relève, envoi,
WorkManager) ; déploiement systemd sur le LXC ; montée de Kotlin puis de Ktor.

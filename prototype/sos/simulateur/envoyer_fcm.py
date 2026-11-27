"""Simulateur FCM : envoie un message de DONNÉES en priorité haute au prototype.

C'est ce que fera le serveur Cocooning (cahier, section 6.1). Le message ne porte
qu'un identifiant et des champs de test, jamais de contenu sensible.

Prérequis : pip install google-auth requests
Usage :
  python envoyer_fcm.py --cle compte-de-service.json --jeton <jeton FCM copié dans l'appli>
La clé du compte de service ne doit JAMAIS être versionnée (elle reste hors du dépôt).
"""
import argparse
import json
import time
import uuid

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

PORTEE = "https://www.googleapis.com/auth/firebase.messaging"


def main() -> None:
    parseur = argparse.ArgumentParser()
    parseur.add_argument("--cle", required=True, help="fichier JSON du compte de service Firebase")
    parseur.add_argument("--jeton", required=True, help="jeton FCM du téléphone récepteur")
    parseur.add_argument("--de", default="simulateur FCM")
    parseur.add_argument("--normale", action="store_true", help="priorité normale, pour comparer")
    args = parseur.parse_args()

    identifiants = service_account.Credentials.from_service_account_file(args.cle, scopes=[PORTEE])
    identifiants.refresh(Request())
    projet = json.load(open(args.cle, encoding="utf-8"))["project_id"]

    id_alerte = "fcm-" + uuid.uuid4().hex[:6]
    message = {
        "message": {
            "token": args.jeton,
            # Les valeurs FCM de données sont toujours des chaînes.
            "data": {
                "type": "sos",
                "id": id_alerte,
                "emis": str(int(time.time() * 1000)),
                "de": args.de,
                "test": "true",
                "batterie": "100",
            },
            "android": {"priority": "NORMAL" if args.normale else "HIGH", "ttl": "300s"},
        }
    }
    reponse = requests.post(
        f"https://fcm.googleapis.com/v1/projects/{projet}/messages:send",
        headers={"Authorization": f"Bearer {identifiants.token}"},
        json=message,
        timeout=15,
    )
    print(reponse.status_code, reponse.text)
    print(f"Alerte {id_alerte} envoyée par FCM")


if __name__ == "__main__":
    main()

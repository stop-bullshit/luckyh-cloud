"""Create the Redis and RabbitMQ credentials once, without writing them to disk."""

import base64
import json
import secrets
import subprocess


def kubectl(*args, input_text=None):
    return subprocess.run(
        ["kubectl", *args], input=input_text, text=True, capture_output=True, check=True
    ).stdout


for namespace in ("redis", "rabbitmq"):
    manifest = kubectl("create", "namespace", namespace, "--dry-run=client", "-o", "yaml")
    kubectl("apply", "-f", "-", input_text=manifest)


redis_secret_json = kubectl(
    "-n", "redis", "get", "secret", "redis-auth", "--ignore-not-found", "-o", "json"
)
if redis_secret_json.strip():
    redis_data = json.loads(redis_secret_json)["data"]
    basic_password = base64.b64decode(redis_data["insight-basic-password"]).decode()
    print("redis/redis-auth already exists")
else:
    basic_password = secrets.token_urlsafe(24)
    secret = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {"name": "redis-auth", "namespace": "redis"},
        "stringData": {
            "password": secrets.token_urlsafe(32),
            "insight-encryption-key": secrets.token_hex(32),
            "insight-basic-password": basic_password,
        },
    }
    kubectl("create", "-f", "-", input_text=json.dumps(secret))
    print("redis/redis-auth created")


basic_secret = kubectl(
    "-n", "redis", "get", "secret", "redis-insight-basic-auth",
    "--ignore-not-found", "-o", "name",
)
if basic_secret.strip():
    print("redis/redis-insight-basic-auth already exists")
else:
    password_hash = subprocess.run(
        ["openssl", "passwd", "-apr1", "-stdin"],
        input=basic_password + "\n", text=True, capture_output=True, check=True,
    ).stdout.strip()
    secret = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {"name": "redis-insight-basic-auth", "namespace": "redis"},
        "stringData": {"auth": "admin:" + password_hash},
    }
    kubectl("create", "-f", "-", input_text=json.dumps(secret))
    print("redis/redis-insight-basic-auth created")


rabbit_secret = kubectl(
    "-n", "rabbitmq", "get", "secret", "rabbitmq-auth",
    "--ignore-not-found", "-o", "name",
)
if rabbit_secret.strip():
    print("rabbitmq/rabbitmq-auth already exists")
else:
    secret = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {"name": "rabbitmq-auth", "namespace": "rabbitmq"},
        "stringData": {
            "erlang-cookie": secrets.token_hex(32),
            "username": "admin",
            "password": secrets.token_urlsafe(32),
        },
    }
    kubectl("create", "-f", "-", input_text=json.dumps(secret))
    print("rabbitmq/rabbitmq-auth created")

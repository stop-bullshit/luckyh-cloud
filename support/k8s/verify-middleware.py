"""Check cluster membership and both management ingresses without printing secrets."""

import base64
import json
import subprocess
import urllib.error
import urllib.request


def kubectl(*args):
    return subprocess.run(
        ["kubectl", *args], text=True, capture_output=True, check=True
    ).stdout.strip()


def secret_value(namespace, name, key):
    secret = json.loads(kubectl("-n", namespace, "get", "secret", name, "-o", "json"))
    return base64.b64decode(secret["data"][key]).decode()


def request(host, path, username=None, password=None):
    headers = {"Host": host}
    if username is not None:
        token = base64.b64encode(f"{username}:{password}".encode()).decode()
        headers["Authorization"] = "Basic " + token
    req = urllib.request.Request("http://192.168.10.200" + path, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


redis_info = kubectl("-n", "redis", "exec", "redis-cluster-0", "--", "redis-cli", "cluster", "info")
for expected in ("cluster_state:ok", "cluster_slots_assigned:16384", "cluster_known_nodes:6", "cluster_size:3"):
    assert expected in redis_info, f"Redis missing {expected}"
print("Redis Cluster: 3 masters, 3 replicas, 16,384 slots, healthy")

code, _ = request("redis.home", "/api/health/")
assert code == 401, f"Redis Insight without login: HTTP {code}"
basic_password = secret_value("redis", "redis-auth", "insight-basic-password")
code, body = request("redis.home", "/api/health/", "admin", basic_password)
assert code == 200, f"Redis Insight with login: HTTP {code}, {body[:200]!r}"
print("redis.home: HTTP 401 without login, HTTP 200 with login")

code, body = request("redis.home", "/api/databases", "admin", basic_password)
assert code == 200, f"Redis Insight database list: HTTP {code}, {body[:200]!r}"
databases = json.loads(body)
if isinstance(databases, dict):
    databases = databases.get("data", [])
database = next((db for db in databases if db.get("name") == "Kubernetes Redis Cluster"), None)
assert database, "Redis Insight connection missing"
code, body = request("redis.home", f'/api/databases/{database["id"]}/connect', "admin", basic_password)
assert code == 200, f"Redis Insight connection: HTTP {code}, {body[:200]!r}"
print("Redis Insight: Kubernetes Redis Cluster connection works")

rabbit_user = secret_value("rabbitmq", "rabbitmq-auth", "username")
rabbit_password = secret_value("rabbitmq", "rabbitmq-auth", "password")
code, body = request("rabbit.home", "/api/nodes", rabbit_user, rabbit_password)
assert code == 200, f"RabbitMQ API: HTTP {code}, {body[:200]!r}"
nodes = json.loads(body)
assert len(nodes) == 3 and all(node["running"] for node in nodes), "RabbitMQ is not a 3-node cluster"
print("rabbit.home: HTTP 200, three RabbitMQ nodes running")

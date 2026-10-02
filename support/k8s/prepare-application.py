"""Prepare application credentials and an isolated Nacos namespace from JSON stdin."""

import base64
import json
import re
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

import yaml


def kubectl(*args, manifest=None):
    result = subprocess.run(
        ["kubectl", *args],
        input=json.dumps(manifest) if manifest is not None else None,
        text=True, capture_output=True,
    )
    if result.returncode and manifest is not None and manifest.get("kind") == "Secret":
        raise RuntimeError(f"kubectl Secret operation failed with exit {result.returncode}")
    if result.returncode:
        raise RuntimeError("kubectl failed: " + result.stderr.strip())
    return result.stdout


def nacos_request(base, path, parameters, method="GET"):
    encoded = urllib.parse.urlencode(parameters)
    url = base + path
    data = None
    if method == "GET":
        url += "?" + encoded
    else:
        data = encoded.encode()
    request = urllib.request.Request(url, data=data, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Nacos {path} returned HTTP {error.code}") from None
    if "code" in result and result["code"] != 0:
        raise RuntimeError(f"Nacos {path} returned code {result['code']}")
    return result


def resolve_credential(value, name, supplied):
    if supplied.get(name):
        return supplied[name]
    match = re.fullmatch(r"\$\{" + name + r":([^}]+)\}", str(value))
    if match:
        return match.group(1)
    if not value or str(value).startswith("${"):
        raise RuntimeError(f"Supply {name} through stdin credentials")
    return str(value)


def prepare():
    supplied = json.load(sys.stdin)
    for name in ("NACOS_PASSWORD", "GHCR_PULL_TOKEN"):
        if not supplied.get(name):
            raise RuntimeError(f"Missing {name} in stdin credentials")

    namespace = "luckyh-cloud"
    nacos_namespace = "luckyh-cloud-k8s"
    nacos_user = supplied.get("NACOS_USERNAME") or "nacos"
    admin_base = "http://192.168.10.201:8848"
    console_base = "http://192.168.10.201:8080"
    token = nacos_request(admin_base, "/nacos/v3/auth/user/login", {
        "username": nacos_user, "password": supplied["NACOS_PASSWORD"],
    }, "POST")["accessToken"]
    print("Nacos authentication verified")

    exists = nacos_request(console_base, "/v3/console/core/namespace/exist", {
        "customNamespaceId": nacos_namespace, "accessToken": token,
    })["data"]
    if not exists:
        created = nacos_request(console_base, "/v3/console/core/namespace", {
            "customNamespaceId": nacos_namespace,
            "namespaceName": "luckyh-cloud-k8s",
            "namespaceDesc": "Kubernetes application environment",
            "accessToken": token,
        }, "POST")["data"]
        if created is not True:
            raise RuntimeError("Nacos namespace creation failed")

    configs = {}
    for data_id in (
        "db-common.yml", "common.yml", "gateway-service.yml", "auth-service.yml",
        "user-service.yml", "order-service.yml", "inventory-service.yml", "account-service.yml",
    ):
        coordinates = {"namespaceId": "luckyh-cloud", "groupName": "DEFAULT_GROUP",
                       "dataId": data_id, "accessToken": token}
        source = nacos_request(admin_base, "/nacos/v3/admin/cs/config", coordinates)["data"]
        content = source["content"]
        configs[data_id] = yaml.safe_load(content)
        coordinates["namespaceId"] = nacos_namespace
        published = nacos_request(admin_base, "/nacos/v3/admin/cs/config", {
            **coordinates, "content": content, "type": "yaml",
        }, "POST")["data"]
        if published is not True:
            raise RuntimeError(f"Nacos publish failed for {data_id}")
        if nacos_request(admin_base, "/nacos/v3/admin/cs/config", coordinates)["data"]["content"] != content:
            raise RuntimeError(f"Nacos readback mismatch for {data_id}")
        print(f"Nacos {nacos_namespace}/DEFAULT_GROUP/{data_id} verified")

    kubectl("apply", "-f", "-", manifest={
        "apiVersion": "v1", "kind": "Namespace", "metadata": {"name": namespace},
    })
    existing = kubectl("-n", namespace, "get", "secret", "app-runtime", "--ignore-not-found", "-o", "json")
    existing_data = json.loads(existing).get("data", {}) if existing.strip() else {}
    redis = json.loads(kubectl("-n", "redis", "get", "secret", "redis-auth", "-o", "json"))["data"]
    rabbit = json.loads(kubectl("-n", "rabbitmq", "get", "secret", "rabbitmq-auth", "-o", "json"))["data"]
    datasource = configs["db-common.yml"]["luckyh"]["datasource"]
    jwt_secret = supplied.get("JWT_SECRET")
    if not jwt_secret:
        jwt_secret = base64.b64decode(existing_data["JWT_SECRET"]).decode() if "JWT_SECRET" in existing_data else secrets.token_hex(64)
    if len(jwt_secret.encode()) < 64:
        raise RuntimeError("JWT_SECRET must contain at least 64 UTF-8 bytes")
    runtime = {
        "NACOS_USERNAME": nacos_user,
        "NACOS_PASSWORD": supplied["NACOS_PASSWORD"],
        "DB_USERNAME": resolve_credential(datasource["username"], "DB_USERNAME", supplied),
        "DB_PASSWORD": resolve_credential(datasource["password"], "DB_PASSWORD", supplied),
        "REDIS_PASSWORD": base64.b64decode(redis["password"]).decode(),
        "SPRING_RABBITMQ_USERNAME": base64.b64decode(rabbit["username"]).decode(),
        "RABBITMQ_PASSWORD": base64.b64decode(rabbit["password"]).decode(),
        "JWT_SECRET": jwt_secret,
    }
    kubectl("apply", "--server-side", "--field-manager=luckyh-application", "--force-conflicts", "-f", "-", manifest={
        "apiVersion": "v1", "kind": "Secret", "type": "Opaque",
        "metadata": {"name": "app-runtime", "namespace": namespace},
        "data": {key: base64.b64encode(value.encode()).decode() for key, value in runtime.items()},
    })
    registry_auth = base64.b64encode(("stop-bullshit:" + supplied["GHCR_PULL_TOKEN"]).encode()).decode()
    registry_config = {"auths": {"ghcr.io": {"auth": registry_auth}}}
    kubectl("apply", "--server-side", "--field-manager=luckyh-application", "--force-conflicts", "-f", "-", manifest={
        "apiVersion": "v1", "kind": "Secret", "type": "kubernetes.io/dockerconfigjson",
        "metadata": {"name": "ghcr-pull", "namespace": namespace},
        "data": {".dockerconfigjson": base64.b64encode(json.dumps(registry_config).encode()).decode()},
    })
    kubectl("-n", namespace, "annotate", "secret", "app-runtime", "ghcr-pull",
            "kubectl.kubernetes.io/last-applied-configuration-", "--overwrite")
    print("Application runtime and GHCR pull Secrets prepared; credential values were not printed")


if __name__ == "__main__":
    try:
        prepare()
    except (RuntimeError, KeyError, ValueError, urllib.error.URLError) as error:
        print(f"Preparation failed: {error}", file=sys.stderr)
        sys.exit(1)

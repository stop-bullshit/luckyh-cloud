"""Form the six Redis pods into a three-master, three-replica cluster."""

import json
import subprocess
import time


def kubectl(*args):
    result = subprocess.run(
        ["kubectl", *args], text=True, capture_output=True, check=True
    )
    return result.stdout.strip()


def redis_cli(pod, *args):
    return kubectl("-n", "redis", "exec", pod, "--", "redis-cli", *args)


pods = json.loads(kubectl("-n", "redis", "get", "pods", "-l", "app=redis-cluster", "-o", "json"))
by_node = {}
for item in pods["items"]:
    if not all(status["ready"] for status in item["status"].get("containerStatuses", [])):
        raise SystemExit(f'{item["metadata"]["name"]} is not Ready')
    by_node.setdefault(item["spec"]["nodeName"], []).append(item["metadata"]["name"])

if len(by_node) != 3 or sorted(map(len, by_node.values())) != [2, 2, 2]:
    raise SystemExit("Expected two Redis pods on each of three nodes")

nodes = sorted(by_node)
masters = [sorted(by_node[node])[0] for node in nodes]
replicas = [sorted(by_node[node])[1] for node in nodes]


def endpoint(pod):
    ordinal = pod.rsplit("-", 1)[1]
    service = json.loads(kubectl("-n", "redis", "get", "svc", f"redis-node-{ordinal}", "-o", "json"))
    return service["spec"]["clusterIP"] + ":6379"


seed_info = redis_cli(masters[0], "cluster", "info")
if "cluster_known_nodes:1" in seed_info:
    print("Creating three Redis masters")
    print(
        kubectl(
            "-n", "redis", "exec", masters[0], "--", "redis-cli", "--cluster", "create",
            *(endpoint(pod) for pod in masters), "--cluster-yes",
        )
    )
elif "cluster_known_nodes:3" not in seed_info and "cluster_known_nodes:6" not in seed_info:
    raise SystemExit("Redis is partly formed; inspect CLUSTER NODES before retrying")

for index, replica in enumerate(replicas):
    replica_info = redis_cli(replica, "cluster", "info")
    if "cluster_known_nodes:1" not in replica_info:
        print(f"{replica} is already in a cluster")
        continue
    master = masters[(index + 1) % 3]
    master_id = redis_cli(master, "cluster", "myid")
    print(f"Joining {replica} to {master}")
    print(
        kubectl(
            "-n", "redis", "exec", master, "--", "redis-cli", "--cluster", "add-node",
            endpoint(replica), endpoint(master), "--cluster-slave", "--cluster-master-id", master_id,
        )
    )
    time.sleep(2)

for _ in range(20):
    info = redis_cli(masters[0], "cluster", "info")
    if all(value in info for value in ("cluster_state:ok", "cluster_slots_assigned:16384", "cluster_known_nodes:6")):
        print("Redis Cluster is healthy: 16,384 slots and 6 nodes")
        break
    time.sleep(3)
else:
    raise SystemExit("Redis Cluster did not reach healthy state")

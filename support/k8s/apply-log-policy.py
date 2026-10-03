#!/usr/bin/env python3
"""更新现有集群的日志配置，保留副本数、资源、凭据及其他运行配置。"""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

import yaml


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("component", choices=["seata", "nacos", "rabbitmq"])
component = parser.parse_args().component
base = Path(__file__).resolve().parent
namespace = component
kind = "deployment" if component == "seata" else "statefulset"
name = "seata-server" if component == "seata" else component
# 局部清单仅更新已有服务，避免在错误集群中创建不完整的工作负载。
subprocess.run(["kubectl", "-n", namespace, "get", kind, name, "-o", "name"], check=True)

if component == "nacos":
    resources = list(yaml.safe_load_all((base / "nacos-logging.yml").read_text(encoding="utf-8")))
    content = resources[0]["data"]["nacos-logback.xml"]
    template = resources[1]["spec"]["template"]
else:
    resources = list(yaml.safe_load_all((base / f"{component}.yml").read_text(encoding="utf-8")))
    desired = next(r for r in resources if r["kind"] == "ConfigMap")
    if component == "seata":
        content = desired["data"]["file-appender.xml"]
        resources = [{
            "apiVersion": "v1", "kind": "ConfigMap",
            "metadata": {"name": "seata-server-config", "namespace": namespace},
            "data": {"file-appender.xml": content},
        }, {
            "apiVersion": "apps/v1", "kind": "Deployment",
            "metadata": {"name": name, "namespace": namespace},
            "spec": {"template": {"spec": {"containers": [{
                "name": name, "volumeMounts": [{
                    "name": "config", "mountPath": "/seata-server/resources/logback/file-appender.xml",
                    "subPath": "file-appender.xml", "readOnly": True,
                }],
            }]}}},
        }]
        template = resources[1]["spec"]["template"]
    else:
        current = json.loads(subprocess.check_output([
            "kubectl", "-n", namespace, "get", "configmap", "rabbitmq-config", "-o", "json",
        ], text=True))
        logging_lines = [line.strip() for line in desired["data"]["rabbitmq.conf"].splitlines()
                         if line.strip().startswith("log.")]
        logging_keys = {line.split("=", 1)[0].strip() for line in logging_lines}
        lines = [line for line in current["data"]["rabbitmq.conf"].splitlines()
                 if line.split("=", 1)[0].strip() not in logging_keys]
        content = "\n".join(lines + logging_lines) + "\n"
        subprocess.run([
            "kubectl", "-n", namespace, "patch", "configmap", "rabbitmq-config",
            "--type=merge", "--patch-file=/dev/stdin",
        ], input=json.dumps({
            "metadata": {"resourceVersion": current["metadata"]["resourceVersion"]},
            "data": {"rabbitmq.conf": content},
        }), text=True, check=True)
        template = {}
        resources = []

checksum = hashlib.sha256(content.encode("utf-8")).hexdigest()
template.setdefault("metadata", {}).setdefault("annotations", {})[
    "luckyh.cloud/logging-config-sha256"
] = checksum
if component == "rabbitmq":
    subprocess.run([
        "kubectl", "-n", namespace, "patch", kind, name,
        "--type=strategic", "--patch-file=/dev/stdin",
    ], input=json.dumps({"spec": {"template": template}}), text=True, check=True)
else:
    for resource in resources:
        if resource["kind"] == "ConfigMap":
            subprocess.run([
                "kubectl", "apply", "--server-side", "--field-manager=luckyh-log-policy", "-f", "-",
            ], input=json.dumps(resource), text=True, check=True)
            continue
        # 按容器名、卷名和挂载路径合并，避免完整清单覆盖运行中的其他配置。
        subprocess.run([
            "kubectl", "-n", namespace, "patch", kind, name,
            "--type=strategic", "--patch-file=/dev/stdin",
        ], input=json.dumps({"spec": resource["spec"]}), text=True, check=True)

print(f"{component} 日志配置已更新，请检查滚动更新及集群健康。")

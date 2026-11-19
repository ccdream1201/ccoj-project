#!/usr/bin/env python3
"""Verify CCOJ against a disposable ccoj-acceptance Compose project."""

import argparse
import base64
import concurrent.futures
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request


PROJECT = "ccoj-acceptance"
COMPOSE = ["docker", "compose", "-p", PROJECT,
           "-f", "compose.yaml", "-f", "deploy/compose.acceptance.yaml"]
ROOT = Path(__file__).resolve().parents[1]
SOURCE = """
import java.util.Scanner;
class Main {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.println(input.nextInt() + input.nextInt());
    }
}
"""


def compose(*args):
    return subprocess.check_output([*COMPOSE, *args], cwd=ROOT, text=True).strip()


def query(statement):
    return compose("exec", "-T", "mysql", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" ccoj -BN -e "$1"',
                   "sh", statement)


def wait_for(label, predicate, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = predicate()
        if result:
            return result
        time.sleep(0.4)
    raise AssertionError(f"Timed out waiting for {label}")


def call(base, client, path, payload=None, method=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(base + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        with client.open(request, timeout=8) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"{}")


def require(result):
    status, body = result
    if status != 200 or body.get("code") != 200:
        raise AssertionError(f"API failed: HTTP {status} {body}")
    return body.get("data")


def sandbox_ready(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.3):
            return True
    except OSError:
        return False


def run(args, sandbox_log):
    if not compose("ps", "-q", "gateway"):
        raise RuntimeError(f"Start the disposable Compose project {PROJECT} before running")
    base = "http://" + compose("port", "gateway", "8080")
    sandbox = None

    def start_sandbox():
        nonlocal sandbox
        if sandbox_ready(args.sandbox_port):
            raise RuntimeError("Sandbox acceptance port is already in use")
        sandbox = subprocess.Popen(
            [args.java, "-jar", str(args.sandbox_jar), f"--server.port={args.sandbox_port}"],
            stdout=sandbox_log, stderr=subprocess.STDOUT,
            env={**os.environ, "CCOJ_SANDBOX_BIND": args.sandbox_bind, "CCOJ_SANDBOX_MODE": "docker"})
        wait_for("sandbox startup", lambda: sandbox_ready(args.sandbox_port), seconds=20)

    def stop_sandbox():
        if sandbox is not None and sandbox.poll() is None:
            sandbox.terminate()
            try:
                sandbox.wait(8)
            except subprocess.TimeoutExpired:
                sandbox.kill()
                sandbox.wait()

    cookies = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))

    def api(path, payload=None, method=None):
        return require(call(base, client, path, payload, method))

    def submit(source=SOURCE):
        return int(api("/submission/judge", {"problemId": problem, "language": "JAVA", "sourceCode": source}))

    def row(submission):
        values = query(f"SELECT status,judge_attempt FROM submission WHERE id={int(submission)}").split("\t")
        return values[0], int(values[1])

    def terminal(submission, expected, seconds=60):
        def read():
            detail = api(f"/submission/{submission}")
            if detail["status"] in {"PENDING", "RUNNING"}:
                return None
            if detail["status"] != expected:
                raise AssertionError(f"Submission {submission} expected {expected}: {detail['status']}")
            return detail
        return wait_for(f"{submission} {expected}", read, seconds)

    broker_stopped = False
    try:
        start_sandbox()
        def gateway_ready():
            try:
                status, body = call(base, client, "/problem")
                if status != 200 or body.get("code") != 200:
                    return False
                status, body = call(base, client, "/user/current")
                return status == 200 and body.get("code") == 1111
            except (OSError, ValueError):
                return False
        wait_for("gateway and problem discovery", gateway_ready)
        account = "t" + secrets.token_hex(3)
        password = "SmokePass123"
        api("/user/register", {"account": account, "password": password, "confirmPassword": password})
        query(f"UPDATE oj_user SET user_role='ADMIN' WHERE account='{account}'")
        api("/user/login", {"account": account, "password": password})
        case = {"input": "1 2\n", "output": "3\n"}
        problem_request = {
            "title": f"Compose acceptance {account}", "description": "输出两数之和。",
            "difficulty": 1, "samples": [case], "judgeCases": [case],
            "judgeConfig": {"timeLimit": 2000, "memoryLimit": 256},
        }
        anonymous = urllib.request.build_opener()
        assert call(base, anonymous, "/problem/add", problem_request)[1]["code"] == 1111

        reader_cookies = http.cookiejar.CookieJar()
        reader = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(reader_cookies))
        reader_account = "t" + secrets.token_hex(3)
        reader_id = int(require(call(base, reader, "/user/register", {
            "account": reader_account, "password": password, "confirmPassword": password,
        })))
        require(call(base, reader, "/user/login", {"account": reader_account, "password": password}))
        assert call(base, reader, "/problem/add", problem_request)[1]["code"] == 40300

        problem = int(api("/problem/add", problem_request))
        _, draft = call(base, anonymous, f"/problem/{problem}")
        assert draft["code"] == 40401
        api(f"/problem/{problem}/publish", method="PATCH")
        assert api(f"/problem/{problem}")["problem"]["id"] == problem

        ok = submit()
        terminal(ok, "ACCEPTED")
        assert row(ok) == ("ACCEPTED", 0)
        assert call(base, reader, f"/submission/{ok}")[1]["code"] == 40300
        page = require(call(base, reader, "/submission"))
        assert page["total"] == 0
        api(f"/user/admin/{reader_id}", {"role": "USER", "status": 1}, method="PATCH")
        assert call(base, reader, "/submission")[1]["code"] == 1111
        print("PASS cross-service session, admin guard, draft visibility and revoked cookie", flush=True)

        failed = submit("class Main { ! }")
        terminal(failed, "COMPILE_ERROR")
        assert row(failed) == ("COMPILE_ERROR", 0)
        print("PASS real Docker sandbox: ACCEPTED and COMPILE_ERROR without retry", flush=True)

        mismatches = [
            ("class Main { public static void main(String[] args) { System.out.println(4); } }",
             "WRONG_ANSWER"),
            ("class Main { public static void main(String[] args) { throw new IllegalStateException(); } }",
             "RUNTIME_ERROR"),
            ("class Main { public static void main(String[] args) throws Exception { Thread.sleep(10000); } }",
             "TIME_LIMIT"),
        ]
        for source, verdict in mismatches:
            submission = submit(source)
            terminal(submission, verdict)
            assert row(submission) == (verdict, 0)
        print("PASS real Docker sandbox: WRONG_ANSWER, RUNTIME_ERROR and TIME_LIMIT without retry", flush=True)

        time.sleep(1.1)
        gate = threading.Barrier(8)
        cookie = "; ".join(f"{item.name}={item.value}" for item in cookies)
        def burst(_):
            request = urllib.request.Request(
                base + "/submission/judge",
                data=json.dumps({"problemId": problem, "language": "JAVA", "sourceCode": SOURCE}).encode(),
                headers={"Content-Type": "application/json", "Cookie": cookie})
            gate.wait()
            try:
                with urllib.request.urlopen(request, timeout=10) as response:
                    return response.status, json.load(response)
            except urllib.error.HTTPError as error:
                return error.code, json.load(error)

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(burst, range(8)))
        blocked = sum(status == 429 for status, _ in results)
        admitted = [int(require(item)) for item in results if item[0] != 429]
        assert blocked and admitted, results
        for submission in admitted:
            terminal(submission, "ACCEPTED")
        assert int(query(f"SELECT COUNT(*) FROM submission WHERE problem_id={problem}")) == 5 + len(admitted)
        print(f"PASS rate limit: {blocked} HTTP 429, {len(admitted)} admitted", flush=True)

        stop_sandbox()
        recovered = submit()
        wait_for("durable sandbox retry", lambda: row(recovered) == ("PENDING", 1))
        start_sandbox()
        terminal(recovered, "ACCEPTED")
        assert row(recovered)[1] >= 1
        print(f"PASS sandbox recovery: same submission {recovered}, attempt {row(recovered)[1]}", flush=True)

        broker_stopped = True
        compose("stop", "rabbitmq")
        delayed = submit()
        assert row(delayed) == ("PENDING", 0)
        assert query(f"SELECT retry_at IS NOT NULL FROM submission WHERE id={delayed}") == "1"
        compose("start", "rabbitmq")
        broker_stopped = False
        terminal(delayed, "ACCEPTED", 90)
        assert row(delayed) == ("ACCEPTED", 0)
        print("PASS broker outage: first dispatch recovered from MySQL", flush=True)

        expected = 3 + len(admitted)
        totals = query(f"SELECT submission_count,accepted_count FROM problem WHERE id={problem}")
        assert totals == f"{expected + 4}\t{expected}", totals
        print(f"PASS counters: {expected + 4} submissions, {expected} accepted", flush=True)

        if args.extended:
            stop_sandbox()
            exhausted = submit()
            terminal(exhausted, "SYSTEM_FAILED", 235)
            assert row(exhausted) == ("SYSTEM_FAILED", 3)
            credentials = base64.b64encode(b"ccoj:ccoj-local-mq").decode()

            def failed_notice():
                request = urllib.request.Request(
                    "http://" + compose("port", "rabbitmq", "15672")
                    + "/api/queues/%2F/ccoj.judge.failed/get",
                    data=json.dumps({"count": 100, "ackmode": "ack_requeue_true",
                                     "encoding": "auto", "truncate": 50000}).encode(),
                    headers={"Authorization": "Basic " + credentials, "Content-Type": "application/json"})
                with urllib.request.urlopen(request, timeout=5) as response:
                    notices = json.load(response)
                return next((notice for notice in notices
                             if notice["properties"].get("message_id") == f"judge-failed:{exhausted}:3"), None)

            notice = wait_for("failure notice in RabbitMQ", failed_notice)
            assert json.loads(notice["payload"]) == {
                "submissionId": exhausted, "attempt": 3,
                "status": "SYSTEM_FAILED", "reason": "RETRIES_EXHAUSTED"}
            print("PASS persistent sandbox outage: exhausted 3 retries, failure notice confirmed", flush=True)
            start_sandbox()

            # Test a crashed worker with a fixture already past the RUNNING deadline.
            lease = int(query(
                "INSERT INTO submission (problem_id,user_id,language,source_code,status,judge_attempt,updated_at) "
                f"SELECT problem_id,user_id,language,source_code,'RUNNING',1,NOW(3)-INTERVAL 6 MINUTE "
                f"FROM submission WHERE id={ok}; SELECT LAST_INSERT_ID()"))
            key = os.environ.get("CCOJ_INTERNAL_KEY", "ccoj-compose-local-only")
            endpoint = "http://" + compose("port", "problem", "8001") + f"/inner/submissions/{lease}/outcome?attempt=0"
            request = urllib.request.Request(endpoint, method="PUT",
                                             data=b'{"status":"COMPILE_ERROR","passedCaseCount":0,"totalCaseCount":1}',
                                             headers={"X-CCOJ-Internal-Key": key, "Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=5) as response:
                assert response.status == 204
            assert row(lease)[0] != "COMPILE_ERROR"
            terminal(lease, "ACCEPTED", 120)
            assert row(lease) == ("ACCEPTED", 2)
            print("PASS old result ignored and expired RUNNING recovered at attempt 2", flush=True)
    finally:
        stop_sandbox()
        if broker_stopped:
            compose("start", "rabbitmq")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sandbox-jar", required=True, type=Path)
    parser.add_argument("--sandbox-port", type=int, default=18181)
    parser.add_argument("--sandbox-bind", default="127.0.0.1")
    parser.add_argument("--java", default="java")
    parser.add_argument("--extended", action="store_true", help="include retry exhaustion and stale round checks")
    arguments = parser.parse_args()
    with tempfile.NamedTemporaryFile(prefix="ccoj-sandbox-acceptance-", suffix=".log", delete=False) as log:
        print(f"Sandbox log: {log.name}", flush=True)
        run(arguments, log)

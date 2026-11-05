# 재구축·복구 절차 (초안)

S15P31D106-347. 2026-11-06 기준 infra 브랜치 구성으로 작성했다. 비밀값은 적지 않고 **어디에 있는지**만 적는다.

- 실행 위치 표기: **[맥]** **[윈도우]** (윈도우 Git Bash) **[노드 1]** (ssh 접속 후 셸)
- 노드 1의 kubectl은 항상 `sudo k3s kubectl`
- 클러스터 변경은 원칙적으로 infra 브랜치 커밋으로 한다(ArgoCD selfHeal이 되돌림). 이 문서의 직접 변경 명령은 장애 복구용 예외이며, 실행 전에 담당자 확인을 받는다
- 확인되지 않은 항목은 `(확인 필요)`로 표시했다

---

## 0. Sealed Secrets 마스터 키

**현재 상태:** 2026-11-06에 백업했다.
- 1차: 맥 `~/.s15p31d106/sealed-secrets-master.yaml`(600)
- 2차: 비밀번호 관리자

**재백업 시기:** 차트 기본값으로 30일마다 새 키가 추가되고 옛 키도 남는다. 새 키로 봉인된 Secret은 옛 백업으로 풀 수 없으므로, **월 1회와 큰 작업(노드 재구축, k3s·차트 업그레이드) 전**에 다시 백업한다.

**영향(백업이 없거나 낡았을 때):** 노드 1을 잃으면 `infra/platform/secrets/*.yaml`의 SealedSecret 9개를 복호화할 수 없다. 모든 비밀값을 다시 만들어 다시 봉인해야 한다(0.3).

### 0.1 백업하기
- 언제: 월 1회, 큰 작업 전
- 필요한 것: 맥에서 노드 1 SSH 접속
- 내용을 화면에 출력하지 않는다(`cat`, `less` 금지). 이 파일만 있으면 저장소의 모든 SealedSecret을 풀 수 있으므로 **절대 커밋하지 않는다**
- [노드 1] 키 Secret을 라벨로 골라 파일로 내보낸다
  ```bash
  umask 077
  sudo k3s kubectl -n kube-system get secret -l sealedsecrets.bitnami.com/sealed-secrets-key -o yaml > ~/sealed-secrets-master.yaml
  grep -c 'kind: Secret' ~/sealed-secrets-master.yaml   # 키 개수만 확인
  ```
- [맥] 가져와서 기존 백업을 덮어쓴다. 내보낸 파일에 옛 키와 새 키가 모두 들어 있다
  ```bash
  scp <노드1>:~/sealed-secrets-master.yaml ~/.s15p31d106/sealed-secrets-master.yaml
  chmod 600 ~/.s15p31d106/sealed-secrets-master.yaml
  ```
- [노드 1] 원본 삭제: `shred -u ~/sealed-secrets-master.yaml`
- 비밀번호 관리자의 2차 사본도 새 파일로 바꾼다
- 확인: 키 개수가 클러스터 키 개수(`sudo k3s kubectl -n kube-system get secret -l sealedsecrets.bitnami.com/sealed-secrets-key --no-headers | wc -l`)와 같다. 활성 키의 지문이 저장소 `pub-cert.pem`과 같은지는 0.2의 확인 방법으로 본다

### 0.2 마스터 키 복원 (노드 1 재구축 후)
- 언제: 클러스터를 새로 만든 뒤, SealedSecret이 `Synced=False`(복호화 실패)일 때
- 필요한 것: 맥 `~/.s15p31d106/sealed-secrets-master.yaml`(없으면 비밀번호 관리자의 2차 사본), 노드 1 SSH 접속
- [맥] `umask 077`을 건 상태로 노드 1에 복사한다: `scp ~/.s15p31d106/sealed-secrets-master.yaml <노드1>:~/`
- [노드 1] 컨트롤러보다 먼저 넣거나, 넣은 뒤 컨트롤러를 재시작한다
  ```bash
  sudo k3s kubectl apply -f ~/sealed-secrets-master.yaml
  sudo k3s kubectl -n kube-system delete pod -l app.kubernetes.io/name=sealed-secrets
  shred -u ~/sealed-secrets-master.yaml
  ```
- 확인
  ```bash
  # [노드 1] 모든 SealedSecret이 Synced=True
  sudo k3s kubectl get sealedsecrets -A
  # [노드 1] 활성 키 인증서 지문 → [맥] openssl x509 -in infra/platform/sealed-secrets/pub-cert.pem -noout -fingerprint -sha256 와 비교
  sudo k3s kubectl -n kube-system get secret -l sealedsecrets.bitnami.com/sealed-secrets-key -o jsonpath='{range .items[*]}{.data.tls\.crt}{"\n"}{end}' \
    | while read -r c; do echo "$c" | base64 -d | openssl x509 -noout -fingerprint -sha256; done
  ```

### 0.3 키를 잃었을 때 (대체 절차)
새 컨트롤러가 만든 새 키로 모든 Secret을 다시 봉인한다.
1. [노드 1→맥] 새 공개 인증서를 받아 `infra/platform/sealed-secrets/pub-cert.pem`을 교체한다(`kubeseal --fetch-cert` 또는 0.2의 tls.crt)
2. [맥] 비밀값마다 생성 스크립트를 다시 돌린다

   | Secret | 스크립트 | 값의 원천 |
   |---|---|---|
   | postgres-backup-s3 | `gen-backup-secrets.sh` | IAM 새 액세스 키(자동 생성) |
   | core-api-s3 | `gen-core-api-s3-secrets.sh` | IAM 새 액세스 키 |
   | core-api-secrets | `gen-core-api-secrets.sh` | JWT 키 `~/.s15p31d106/jwt/` 등 |
   | registry-htpasswd·registry-pull | `gen-registry-secrets.sh` | `~/.s15p31d106/registry.env` |
   | operator-oauth (`tailscale-operator-oauth.yaml`) | `gen-tailscale-oauth-secret.sh` | Tailscale 관리 화면에서 새 OAuth 클라이언트 |
   | postgres-credentials | **전체 생성 스크립트 없음**. `gen-dev-readonly-secret.sh`는 기존 SealedSecret에 `DEV_READONLY_PASSWORD`만 추가하므로, 키를 잃으면 앱 롤 비밀번호까지 새로 만들어 직접 봉인해야 한다 | 새 값 |
   | rabbitmq-credentials, redis-credentials | **생성 스크립트 없음** (확인 필요) | 새 값으로 직접 봉인 |

   dev_readonly 비밀번호는 파일로 보관하지 않는다. 필요하면 클러스터 Secret `data/postgres-credentials`의 `DEV_READONLY_PASSWORD`에서 꺼낸다. `create-dev-readonly-role.sh`도 이 방식을 쓴다

3. 커밋 → push → ArgoCD 반영
4. DB 롤 비밀번호가 새 Secret과 다르면 맞춘다. 기존 DB를 살렸을 때와 백업에서 복원했을 때 모두 해당된다. 방법은 `create-dev-readonly-role.sh` 방식(Secret → 파이프 → `psql \getenv`)을 앱 롤 4개와 postgres에 똑같이 적용하는 것이다. 이 일을 하는 스크립트는 아직 없다

---

## 1. DB 복구 (S3 백업 → 운영 PostgreSQL)

- 언제: 운영 데이터 손상·삭제, PVC 유실, 노드 1 재구축 뒤
- 실행 위치: [맥] 사전서명 URL 생성, [노드 1] 복원
- 필요한 것
  - 맥 AWS 프로필 `s15p31d106`(`~/.aws/credentials`). 백업 계정 `backup-writer`는 업로드만 가능해서 쓸 수 없다
  - 백업: `s3://s15p31d106-backup-887324111755/postgres/` 아래 `animal-<TS>.dump` + `globals-<TS>.sql`. 6시간마다 생성, 30일 보관
  - 손실 범위(RPO): 최대 6시간. 마지막 백업 이후의 데이터는 잃는다

### 1.1 먼저 복원 테스트
[노드 1] `bash infra/scripts/restore-test.sh <TS>`를 실행한다. 고른 백업이 복원되는지, 운영과 무엇이 다른지 먼저 확인한다. 2026-11-06에 `20261106-0600`으로 성공했다.

### 1.2 restore-test.sh와 실제 복구의 차이
| 항목 | restore-test.sh | 실제 복구 |
|---|---|---|
| 대상 | 임시 Pod `restore-test/pg-restore-test` (emptyDir) | 운영 `data/postgres-0` (PVC) |
| 운영 쪽 권한 | 읽기만 (`dev_readonly`) | 쓰기 (`postgres` 슈퍼유저로 DB 삭제·생성) |
| 앱 | 그대로 둠 | **먼저 정지**: infra 커밋으로 core-api 등 `replicas: 0` (ArgoCD가 수동 scale을 되돌림) |
| 기존 DB | 없음(빈 Pod) | `animal` DB가 있다. 노드를 새로 만든 경우 init 스크립트가 만든 빈 롤·스키마가 있다 |
| globals | 그대로 적용 (`role "postgres" already exists`만 허용) | 같음. 다만 **백업의 비밀번호 해시가 현재 Secret 값과 다르면** 앱 접속이 실패하므로 0.3-4로 맞춘다 |
| 복원 명령 | `pg_restore -C -d postgres` | `dropdb --force animal` 후 `pg_restore -C -d postgres --exit-on-error` |
| 끝난 뒤 | 네임스페이스 삭제 | 비교 쿼리로 확인 → 앱 `replicas` 원복 커밋 |

### 1.3 절차 (초안, 스크립트화는 별도 이슈)
1. [맥] 1.1을 먼저 성공시킨다
2. [맥] infra 커밋: 앱 Deployment의 `replicas: 0` → push → ArgoCD 반영 확인
3. [노드 1] 복원 직전에 수동 백업을 한 번 받아 둔다. 복구가 실패하면 되돌릴 지점이 된다
   ```bash
   sudo k3s kubectl -n data create job --from=cronjob/postgres-backup postgres-backup-before-restore
   ```
4. [맥] 사전서명 URL 2개를 만든다(restore-test.sh 머리말의 `aws s3 presign` 명령). [노드 1] `postgres-0`의 `/tmp`로 스트리밍한다(restore-test.sh의 `fetch()`와 같은 방식, 대상만 `-n data exec -i postgres-0`)
5. [노드 1] 복원
   ```bash
   sudo k3s kubectl -n data exec postgres-0 -- psql -X -q -U postgres -d postgres -v VERBOSITY=terse -f /tmp/globals.sql
   sudo k3s kubectl -n data exec postgres-0 -- dropdb -U postgres --force animal
   sudo k3s kubectl -n data exec postgres-0 -- pg_restore -U postgres -d postgres -C --exit-on-error /tmp/animal.dump
   sudo k3s kubectl -n data exec postgres-0 -- rm -f /tmp/globals.sql /tmp/animal.dump
   ```
6. 확인: restore-test.sh의 비교 쿼리(`COMPARE_SQL`)를 운영에 돌려 테이블·행 수가 백업 시점과 맞는지 본다. 앱 롤로 접속되는지도 확인한다
7. [맥] `replicas`를 원복하는 커밋 → push → 헬스 체크(`/actuator/health`)

Flyway로 바꾼 뒤에는 `flyway_schema_history`도 덤프에 같이 들어 있으므로 앱이 기동할 때 검증을 통과한다. 백업이 Flyway 전환 이전 것이면 그렇지 않으니 주의한다.

---

## 2. 노드 재생성

### 2.1 노드 2 (EC2, Terraform)
- 언제: 노드 2 인스턴스 장애·손상, OS 재설치가 필요할 때
- 실행 위치: [맥] Terraform(`~/bin/terraform`), [노드 1] 토큰 발급·노드 정리, [브라우저] Tailscale 관리 화면
- 필요한 것
  - 맥 AWS 프로필 `s15p31d106`. 상태 파일은 S3 `s15p31d106-tfstate-887324111755-ap-northeast-2-an`
  - `infra/terraform/terraform.tfvars`(맥, Git 제외). 새 `tailscale_auth_key`, `k3s_token`, `ssh_allowed_cidrs`를 넣는다
  - SSH 키 `~/.ssh/k15-node2`(맥). 윈도우 키는 `keys/k15-node2-win.pub`를 user_data가 넣는다
- 절차
  1. [노드 1] 노드를 빼낸다: `sudo k3s kubectl drain k15d106-node2 --ignore-daemonsets --delete-emptydir-data`, 이어서 `sudo k3s kubectl delete node k15d106-node2`
  2. [브라우저] Tailscale에서 기존 `k15d106-node2` 기기를 삭제한다(4장). 남겨 두면 새 노드 이름이 `-1`로 붙는다
  3. [노드 1] 토큰 발급 `sudo k3s token create --ttl 24h --description node2` → [맥] `terraform.tfvars`에 넣는다
  4. [브라우저] Tailscale 인증 키 발급(재사용 불가, 7일, `tag:k3s`) → `terraform.tfvars`
  5. [맥] `cd infra/terraform && ~/bin/terraform plan -replace=aws_instance.node2`. **apply는 승인 후**에 한다. 교체할 때는 현재 user_data가 쓰이므로 3·4번의 새 값이 반영된다
  6. [노드 2] Docker와 gitlab-runner를 수동으로 설치하고 등록한다. 러너 등록 토큰의 위치는 (확인 필요)
- 확인: [노드 2] `/var/log/cloud-init-output.log`에 `완료` / [노드 1] `sudo k3s kubectl get nodes`에서 `k15d106-node2 Ready`, 라벨 `role=worker` / GitLab CI 러너 온라인

### 2.2 노드 1 (Lightsail, Terraform 밖) — 초안
- 언제: 노드 1 인스턴스 유실. 클러스터 전체를 다시 만드는 경우다
- 노드 1은 Terraform이 관리하지 않는다(backup.tf 주석: "노드 1이 Lightsail"). 인스턴스 사양, 스냅샷 정책, SSH 키 위치는 (확인 필요)
- 같이 사라지는 것: k3s 데이터, PostgreSQL·Redis·RabbitMQ 볼륨(local-path), 레지스트리 이미지, **Sealed Secrets 키(0장)**
- 순서
  1. [Lightsail 콘솔] 인스턴스 생성(Ubuntu), 방화벽 설정. ufw 규칙은 node2-init과 같다
  2. [노드 1] Tailscale 접속. k3s 설정과 Terraform `k3s_url`이 Tailscale IP `100.121.219.58`에 고정돼 있으므로, Tailscale 관리 화면에서 새 기기의 IP를 이 값으로 바꾸거나 두 곳을 고친다
  3. [노드 1] `/etc/rancher/k3s/config.yaml` ← `infra/bootstrap/k3s-config-node1.yaml`, k3s `v1.36.5+k3s1` 서버 설치
  4. [노드 1] **Sealed Secrets 키 복원(0.2)을 ArgoCD 동기화보다 먼저** 한다. 키 Secret을 먼저 넣어 두면 컨트롤러가 기동할 때 그 키를 쓴다
  5. [노드 1] ArgoCD v3.5.6 설치(`install.yaml`, 저장소 밖) → admin 비밀번호 설정(3장) → GitLab 저장소 접근 설정(자격 증명 위치는 확인 필요) → `infra/argocd/root.yaml` 적용
  6. ArgoCD가 `infra/apps`의 모든 앱을 동기화한다. PostgreSQL은 init 스크립트로 빈 DB를 만든다
  7. DB 복구(1장). 레지스트리가 비어 있으면 CI를 다시 돌려 이미지를 다시 올린다
  8. 노드 2 다시 조인(2.1. 새 서버의 토큰이 필요하다)
- 확인: `sudo k3s kubectl get nodes`, ArgoCD 모든 앱 `Synced/Healthy`, `get sealedsecrets -A` 모두 Synced, API 헬스 체크

---

## 3. ArgoCD admin 비밀번호 재설정

- 언제: admin 비밀번호 분실, 유출 의심, 재설치 직후
- 실행 위치: [노드 1], 브라우저(ArgoCD UI)
- 필요한 것: 노드 1 SSH. ArgoCD 본체는 저장소 밖에서 설치했고, `argocd-secret`은 ArgoCD 앱이 관리하지 않으므로 selfHeal과 충돌하지 않는다
- 절차 (ArgoCD 공식 방식. 직접 만든 해시를 셸에 입력하지 않는다)
  1. [노드 1] 기존 비밀번호를 지우고 서버를 재시작한다. 서버가 새 초기 비밀번호를 만든다
     ```bash
     sudo k3s kubectl -n argocd patch secret argocd-secret -p '{"data":{"admin.password":null,"admin.passwordMtime":null}}'
     sudo k3s kubectl -n argocd rollout restart deployment argocd-server
     sudo k3s kubectl -n argocd rollout status deployment argocd-server
     ```
  2. [노드 1] 초기 비밀번호를 한 번 읽는다(화면에 나온다. 바로 바꾸고 터미널 기록을 지운다)
     ```bash
     sudo k3s kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
     ```
  3. [브라우저] admin으로 로그인 → User Info → Update Password에서 새 비밀번호로 바꾼다. 보관 위치는 (확인 필요: 비밀번호 관리자)
  4. [노드 1] `sudo k3s kubectl -n argocd delete secret argocd-initial-admin-secret`
- 확인: 새 비밀번호로 로그인 성공, 옛 비밀번호로는 실패. `backend` 읽기 전용 계정(argocd-cm `accounts.backend`)은 영향을 받지 않는다. 그 비밀번호도 바꾸려면 admin으로 `argocd account update-password --account backend`

---

## 4. Tailscale 기기 정리

- 언제: 노드·operator를 다시 만든 뒤, 같은 이름의 기기가 `-1` 접미사로 중복될 때, 오래 오프라인인 기기가 쌓였을 때
- 실행 위치: [브라우저] Tailscale 관리 화면(Machines), [노드 1] 확인
- 관련 기기와 태그

  | 기기 | 만든 주체 | 태그 |
  |---|---|---|
  | 노드 1 (Tailscale IP `100.121.219.58`) | 수동 설치 | (확인 필요) |
  | `k15d106-node2` | node2 user_data | `tag:k3s` |
  | `k15-operator` | tailscale-operator | `tag:k8s-operator` |
  | `animal-db` | operator(Service `data/postgres-tailnet`) | `tag:svc-db` |
  | 맥·윈도우 | 개인 | — |

- 절차
  1. Machines에서 이름이 같은 기기를 찾아 **오프라인인 옛 기기**를 Remove한다
  2. 새 기기에 `-1`이 붙었다면 Edit machine name으로 원래 이름으로 바꾼다. `animal-db`는 팀원 DB 접속 주소(`animal-db.<tailnet>.ts.net`)라서 이름이 바뀌면 접속이 끊긴다
  3. 노드 1을 새로 만들었다면 IP를 `100.121.219.58`로 바꾼다(2.2-2)
  4. operator를 다시 만들었다면 OAuth 클라이언트가 유효한지 확인하고, 아니면 `gen-tailscale-oauth-secret.sh`로 다시 만든다
  5. 다 쓴 인증 키(노드 2 조인용)는 Settings → Keys에서 폐기한다
- 확인: Machines에 각 이름이 하나씩만 있다 / [노드 1] `tailscale status` / 팀원 `animal-db` 접속 확인 / `sudo k3s kubectl get nodes -o wide`에서 노드 IP가 Tailscale IP

---

## 확인 필요 목록
- 후속 작업: 생성 스크립트가 없는 Secret 3개(`postgres-credentials` 전체, `rabbitmq-credentials`, `redis-credentials`)의 생성·봉인 스크립트 작성(0.3)
- 마스터 키 재백업 일정(월 1회) 담당·알림 방법
- 노드 1 Lightsail 사양·스냅샷·SSH 키 위치, 노드 1 Tailscale 태그
- ArgoCD `install.yaml` 보관 위치와 GitLab 저장소 자격 증명
- gitlab-runner 등록 토큰 위치
- 실제 DB 복구 절차(1.3)의 스크립트화

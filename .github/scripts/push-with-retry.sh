#!/usr/bin/env bash
#
# 把当前 HEAD 推到目标分支，带「拉取 → rebase → 重推」的自动重试。
#
# 为什么必须有它（这不是优化，是发布能不能收尾的前提）：
# 整条发布流程要跑好几分钟，期间仓库随时可能前进 —— 比如有人在网页上改了文件、
# 或者别的 workflow 推了提交。等流程走到最后那一下 push，本地 HEAD 已经落后，
# 会被非快进（non-fast-forward）直接拒掉，前面几分钟全部白跑。
#
# v1.0.1 就是这么卡住的：Release 建好了、APK 传上去了，只有最后一步
# 「把 isReady=true 的清单推回 main」失败，客户端因此一直看到「灰度中」。
#
# 用法：
#   bash .github/scripts/push-with-retry.sh <branch> [max_attempts]
#
# 退出码：0 = 推送成功；1 = 重试耗尽或 rebase 冲突（冲突时自动 abort，不留半成品）。

set -euo pipefail

branch="${1:?用法: push-with-retry.sh <branch> [max_attempts]}"
max_attempts="${2:-5}"

for attempt in $(seq 1 "$max_attempts"); do
  # 放在 if 里：失败不触发 set -e，交给下面的重试逻辑处理
  if git push origin "HEAD:${branch}"; then
    echo "已推送到 ${branch}（第 ${attempt} 次尝试）"
    exit 0
  fi

  echo "::warning::推送 ${branch} 被拒，拉取远端后重试（第 ${attempt}/${max_attempts} 次）"

  if [ "$attempt" = "$max_attempts" ]; then
    break
  fi

  git fetch origin "${branch}"

  if ! git rebase "origin/${branch}"; then
    git rebase --abort || true
    echo "::error::rebase 到 origin/${branch} 时发生冲突，已回滚。请人工解决后再重跑本流程。"
    exit 1
  fi

  # 退避一下，避开「另一端刚好也在推」的窗口
  sleep $((attempt * 2))
done

echo "::error::推送 ${branch} 失败，已重试 ${max_attempts} 次。"
exit 1

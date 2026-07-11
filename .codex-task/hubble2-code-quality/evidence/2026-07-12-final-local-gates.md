# Final Local Gates — 2026-07-12

## 边界

- 完整本地门禁覆盖的产品实现 SHA：`10c0e8fa4f18694f8f848212ed1da52ef36d80a4`
- 后续 review checkpoint：`03be1bd7b9e2cbdbac90499821bbf4ce147124ac`；相对实现 SHA 仅新增 SOT/evidence 文档，不改变产品验证面
- 分支：`hubble2`
- 工作树：验证前 clean；验证仅生成 ignored build artifacts
- Java/Maven：Java 11 + Maven 3.9.11
- GitHub quota：远端 Hubble job pending；不阻塞本地门禁与独立 review，仅延迟最终真实 CI 证据

## 新鲜最终结果

| 门禁 | 结果 | 耗时/数量 |
|---|---|---|
| FE `yarn lint` | exit 0 | 4.94s；330 files，0 warning/error |
| FE Jest | exit 0 | 6.50s；40 suites / 148 tests；console warning 0 |
| FE `CI=true yarn build` | exit 0 | 47.47s；52 条已批准第三方 source-map warning，来源预算未变化 |
| BE `clean test -P unit-test` | exit 0 | 31.40s；121 tests；checkstyle 0；JaCoCo 407 classes |
| Client + Loader linked install | exit 0 | 49.26s；reactor 3/3 success |
| Hubble package | exit 0 | 126.73s；reactor 3/3 success |
| distribution audit | exit 0 | 423 JAR；275 license；43 FE license；10 native-bearing |
| CI-equivalent sidecars | exit 0 | SHA-512 + temporary GPG signature + `--require-sidecars` |

## Warning 边界

- FE production build 的 52 条 warning 仅来自 X6 38、Dagre 10、Antd 4；这是 DEC-FE-02 明确接受的精确预算，不关闭 source map、不做过滤。
- Browserslist、bundle size 与 Yarn peer-dependency 债务已进入 FUTURE-VIS，明确不在当前 goal 实施。
- Maven 发布 POM 元数据、Loader Shade 重复类等为 Hubble 范围外既有输出；本轮没有通过忽略退出码或跳过必需测试换绿。

## 仍待外部证据

- PR #4 最终 HEAD 的 Hubble CI 与 cache hit 交叉验证；GitHub quota 恢复后补证。
- 独立只读 reviewer 及必要 re-review。

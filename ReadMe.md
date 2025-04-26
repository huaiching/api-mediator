# API Gateway 中台

## 項目概述

API Gateway 中台是一個基於 Spring Boot 的代理服務，用於統一管理後端 API 的訪問。<br/>
前端只需透過中台的統一網址（例如 `http://localhost:9000/mediator/{backendName}/{path}` ）即可呼叫所有後端服務，無需直接訪問後端實際 URL。

### 主要功能
- 透過 API 代理，根據 後端 API 文檔 動態配置 後端服務，可將多個後端服務 統一整合為 中台網域，提供前端更簡便的設定。
- **動態後端配置**：透過 `application.yml` 配置後端服務，易於新增或修改。

## 啟動
- 運行 `ApiGatewayApplication`。
  - 運行`前`，需要先啟動 `後端服務`。
- `SWAGGER UI` http://localhost:9000/swagger-ui/index.html
  - 啟動後，`SWAGGER UI` 的右上角 可切換後端服務。

## 目錄結構
```
src/main/java/com/example/api_mediator/
├── config/                # 配置類
├── controller/            # 控制器層
```

## 註冊後端服務
- 開啟配置文件 `src/main/resources/application.yml`，可進行 後端服務的設定
```yaml
server:
  port: 9000
proxy:
  apis:                             # 這裡 配置 後端 swagger api json
    - name: proxy1                    # 後端服務名稱 (要呈現於網址中，不可使用中文)
      url: http://localhost:9091      # 後端服務網址
    - name: proxy2
      url: http://localhost:9091
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui
    config-url: /mediator/swagger-config
```
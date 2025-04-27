# API Gateway 中台

## 項目概述

API Gateway 中台是一個基於 Spring Boot 的代理服務，用於統一管理後端 API 的訪問。<br/>
前端只需透過中台的統一網址（例如 `http://localhost:9000/mediator/{backendName}/{path}` ）即可呼叫所有後端服務，無需直接訪問後端實際 URL。

### 主要功能
### 1. 代理後端 API 服務，提供前端 統一的 接口
  - **URL**: http://localhost:9000/proxy/後端服務path/...
### 2. 針對其他整合性的服務，可以於中台撰寫 controller
  - **URL**: http://localhost:9000/中台服務名稱/...

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
├── client/                # Http 轉發請求 
├── service/               # 邏輯處理 
```

## 註冊後端服務
- 開啟配置文件 `src/main/resources/application.yml`，可進行 後端服務的設定
```yaml
server:
  port: 9000
proxy:
  apis:                           # 這裡 配置 後端 swagger api json
    - name: 後端服務1                 # SWAGGER 菜單的顯示名稱
      path: proxy1                   # 後端服務網域 (要呈現於網址中，不可使用中文)
      url: http://localhost:9091     # 後端服務網址
    - name: 後端服務2
      path: proxy2
      url: http://localhost:9092
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui
    config-url: /proxy/swagger-config
```
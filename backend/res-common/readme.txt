openfeign:声明式的restful客户端.
     底层采用动态代理，无需手动编写http请求代码。

     1. 注解 ->   contract合同    可以更换.
     2. 客户端:   restTemplate, OkHttpClient, OkHttp3.....
     3. 序列化与反序列化: encoder/decoder.
     4. 重试机制: retry.
     5. 超时机制: timeout.
     6. 日志: logging.
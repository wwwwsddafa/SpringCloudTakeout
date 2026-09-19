package com.example.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

/**
 * @program: cloud161
 * @description: 获取某个数据中心中该服务对应的服务实例对应的id
 * @author: zy
 * @create: 2026-08-16 11:02
 */
@Component
@Slf4j
public class WorkerIdAssigner {

    @Autowired
    private DiscoveryClient discoveryClient;// DiscoveryClient：Nacos服务发现客户端，可以根据服务名拿到所有服务实例列表
    //从yml中获取服务名
    @Value("${spring.application.name}")
    private String serviceName;

    //获取服务实例下的某台主机id
    public Long getWorkerId() {
        //获取服务实例列表
        List<ServiceInstance> serviceInstances = discoveryClient.getInstances(serviceName);
        try {// //取出本机的服务实例ip
            String currentIp = InetAddress.getLocalHost().getHostAddress();
            long index = 0;
            // 遍历Nacos实例列表，找到和本机IP相同的实例，拿到数组下标作为workerId
            for (int i = 0; i < serviceInstances.size(); i++) {
                if (serviceInstances.get(i).getHost().equals(currentIp)) {
                    index = i;
                    break;
                }
            }
            return index;
        } catch (Exception ex) {
            // 捕获全部异常，出问题兜底返回0，保证程序不崩
            log.info("获取workerId失败，异常信息：" + ex.getMessage());
            return 0L;
        }
    }
}
/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.PushService;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CatalogControllerTest {

    @Mock
    private ServiceManager serviceManager;

    @Mock
    private SubscribeManager subscribeManager;

    @Mock
    private DistroMapper distroMapper;

    @Mock
    private PushService pushService;

    @Mock
    private NacosRestTemplate NACOS_REST_TEMPLATE;

    @Spy
    private MockEnvironment environment;

    private CatalogController catalogController;

    private Service service;

    private Cluster cluster;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        ApplicationUtils.injectEnvironment(environment);
        Map<String, Service> serviceMap = new HashMap<>();
        catalogController = new CatalogController();
        Field field = catalogController.getClass().getDeclaredField("serviceManager");
        field.setAccessible(true);
        field.set(catalogController, serviceManager);
        Field field1 = catalogController.getClass().getDeclaredField("subscribeManager");
        field1.setAccessible(true);
        field1.set(catalogController, subscribeManager);
        Field field2 = catalogController.getClass().getDeclaredField("distroMapper");
        field2.setAccessible(true);
        field2.set(catalogController, distroMapper);
        Field field3 = catalogController.getClass().getDeclaredField("pushService");
        field3.setAccessible(true);
        field3.set(catalogController, pushService);
        Field field4 = catalogController.getClass().getDeclaredField("NACOS_REST_TEMPLATE");
        field4.setAccessible(true);
        field4.set(catalogController, NACOS_REST_TEMPLATE);
        service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        service.setProtectThreshold(12.34f);
        service.setGroupName(TEST_GROUP_NAME);
        service.setLastModifiedMillis(5000);
        cluster = new Cluster(TEST_CLUSTER_NAME, service);
        cluster.setDefaultPort(1);
        service.addCluster(cluster);
        serviceMap.put(Constants.DEFAULT_NAMESPACE_ID, service);
        List<Subscriber> subscribes = new ArrayList<>();
        Subscriber subscribe = new Subscriber("10.0.0.1", "Go-SDK", "crpc-demo", "10.0.0.1", Constants.DEFAULT_NAMESPACE_ID, "public@@com.alipay.testFacade:1.0.0@DEFAULT");
        subscribes.add(subscribe);
        when(serviceManager.getServiceMap(any())).thenReturn(serviceMap);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME)).thenReturn(service);
        when(subscribeManager.getSubscribersFuzzy(any(), any())).thenReturn(subscribes);
        when(distroMapper.getHealthyList()).thenReturn(Lists.newArrayList("10.0.0.11"));
        ConcurrentMap<String, ConcurrentMap<String, PushService.PushClient>> clientMap = new ConcurrentHashMap<>();
        ConcurrentMap<String, PushService.PushClient> clientsMap = new ConcurrentHashMap<>();
        clientMap.put("sofamesh##public@@com.alipay.testFacade:1.0.0@DEFAULT", clientsMap);
        when(this.pushService.getClientMap()).thenReturn(clientMap);
    }

    @Test
    public void testServiceDetail() throws Exception {
        ObjectNode result = catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
        String actual = result.toString();
        assertTrue(actual.contains("\"service\":{"));
        assertTrue(actual.contains("\"groupName\":\"test-group-name\""));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"public\""));
        assertTrue(actual.contains("\"selector\":{\"type\":\"none\"}"));
        assertTrue(actual.contains("\"protectThreshold\":12.34"));
        assertTrue(actual.contains("\"clusters\":[{"));
        assertTrue(actual.contains("\"defaultCheckPort\":80"));
        assertTrue(actual.contains("\"defaultPort\":1"));
        assertTrue(actual.contains("\"healthChecker\":{\"type\":\"TCP\"}"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"test-cluster\""));
        assertTrue(actual.contains("\"serviceName\":\"public"));
        assertTrue(actual.contains("\"useIPPort4Check\":true"));
    }

    @Test(expected = NacosException.class)
    public void testServiceDetailNotFound() throws Exception {
        catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME);
    }

    private static final String TEST_CLUSTER_NAME = "test-cluster";

    private static final String TEST_SERVICE_NAME = "public@@com.alipay.testFacade:1.0.0@DEFAULT";

    private static final String TEST_GROUP_NAME = "test-group-name";

    @Test
    public void testInstanceList() throws NacosException {
        Instance instance = new Instance("1.1.1.1", 1234, TEST_CLUSTER_NAME);
        cluster.updateIps(Collections.singletonList(instance), false);
        ObjectNode result = catalogController.instanceList(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME, TEST_CLUSTER_NAME, 1, 10);
        String actual = result.toString();
        assertTrue(actual.contains("\"count\":1"));
        assertTrue(actual.contains("\"list\":["));
        assertTrue(actual.contains("\"clusterName\":\"test-cluster\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1234"));
    }

    @Test
    public void testListServiceInfo() {
        Set<Instance> instances = new HashSet<>();
        Instance instance = new Instance();
        instance.setClusterName("clusterA");
        instance.setServiceName("public@@com.alipay.testFacade:1.0.0@DEFAULT");
        instance.setWeight(10);
        instance.setIp("10.0.0.1");
        instances.add(instance);
        cluster.setEphemeralInstances(instances);
        PageResult<ServiceInfoModel> pageResult = catalogController.listServiceInfo(0, 0, "sofamesh", 1, 10);
        assertEquals(1, pageResult.getList().size());
        ServiceInfoModel infoModel = pageResult.getList().get(0);
        assertEquals("com.alipay.testFacade:1.0.0@DEFAULT", infoModel.getServiceName());
        assertEquals(0, infoModel.getSubInfos().size());

        assertEquals(1, infoModel.getPubInfos().size());
        ServicePubInfo pubInfo = infoModel.getPubInfos().get(0);
        assertEquals("com.alipay.testFacade:1.0.0@DEFAULT", pubInfo.getDataId());
        assertEquals("10.0.0.1", pubInfo.getServiceIp());

       pageResult = catalogController.listServiceInfo(3000, 0, "sofamesh", 1, 10);
        assertEquals(1, pageResult.getList().size());
        infoModel = pageResult.getList().get(0);
        assertEquals("com.alipay.testFacade:1.0.0@DEFAULT", infoModel.getServiceName());
        assertEquals(0, infoModel.getSubInfos().size());

        assertEquals(1, infoModel.getPubInfos().size());
        pubInfo = infoModel.getPubInfos().get(0);
        assertEquals("com.alipay.testFacade:1.0.0@DEFAULT", pubInfo.getDataId());
        assertEquals("10.0.0.1", pubInfo.getServiceIp());
    }

    @Test
    public void testListDetail() {
        // TODO
    }

    @Test
    public void testRt4Service() {
        // TODO
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.kubernetes.consumer.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesServicesConsumerIT extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    @Order(1)
    public void createService() throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED");

        Exchange ex = template.request("direct:createService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "test");
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
            ServiceSpec serviceSpec = new ServiceSpec();
            List<ServicePort> lsp = new ArrayList<>();
            ServicePort sp = new ServicePort();
            sp.setPort(8080);
            sp.setTargetPort(new IntOrString(8080));
            sp.setProtocol("TCP");
            lsp.add(sp);
            serviceSpec.setPorts(lsp);
            Map<String, String> selectorMap = new HashMap<>();
            selectorMap.put("containter", "test");
            serviceSpec.setSelector(selectorMap);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_SPEC, serviceSpec);
        });

        Service serv = ex.getMessage().getBody(Service.class);

        assertEquals("test", serv.getMetadata().getName());

        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    @Order(2)
    public void deleteService() throws Exception {
        mockResultEndpoint.expectedMessageCount(2);

        Exchange ex = template.request("direct:deleteService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "test");
        });

        boolean servDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(servDeleted);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF("kubernetes-services://%s?oauthToken=%s&operation=listServices", host, authToken);
                from("direct:listByLabels").toF("kubernetes-services://%s?oauthToken=%s&operation=listServicesByLabels", host,
                        authToken);
                from("direct:getServices").toF("kubernetes-services://%s?oauthToken=%s&operation=getService", host, authToken);
                from("direct:createService").toF("kubernetes-services://%s?oauthToken=%s&operation=createService", host,
                        authToken);
                from("direct:deleteService").toF("kubernetes-services://%s?oauthToken=%s&operation=deleteService", host,
                        authToken);
                fromF("kubernetes-services://%s?oauthToken=%s&labelKey=this&labelValue=rocks", host, authToken)
                        .process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            log.info("Got event with body: " + in.getBody() + " and action "
                     + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}

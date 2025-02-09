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
package org.apache.camel.component.kubernetes.namespaces;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kubernetes.KubernetesHelper.prepareOutboundMessage;

public class KubernetesNamespacesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesNamespacesProducer.class);

    public KubernetesNamespacesProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = KubernetesHelper.extractOperation(getEndpoint(), exchange);

        switch (operation) {

            case KubernetesOperations.LIST_NAMESPACE_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_NAMESPACE_BY_LABELS_OPERATION:
                doListNamespaceByLabel(exchange);
                break;

            case KubernetesOperations.GET_NAMESPACE_OPERATION:
                doGetNamespace(exchange);
                break;

            case KubernetesOperations.CREATE_NAMESPACE_OPERATION:
                doCreateNamespace(exchange);
                break;

            case KubernetesOperations.DELETE_NAMESPACE_OPERATION:
                doDeleteNamespace(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        NamespaceList namespacesList = getEndpoint().getKubernetesClient().namespaces().list();

        prepareOutboundMessage(exchange, namespacesList.getItems());
    }

    protected void doListNamespaceByLabel(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get a specific namespace by labels require specify a labels set");
            throw new IllegalArgumentException("Get a specific namespace by labels require specify a labels set");
        }
        NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> namespaces
                = getEndpoint().getKubernetesClient().namespaces();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            namespaces.withLabel(entry.getKey(), entry.getValue());
        }
        NamespaceList namespace = namespaces.list();

        prepareOutboundMessage(exchange, namespace.getItems());
    }

    protected void doGetNamespace(Exchange exchange) {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific namespace require specify a namespace name");
            throw new IllegalArgumentException("Get a specific namespace require specify a namespace name");
        }
        Namespace namespace = getEndpoint().getKubernetesClient().namespaces().withName(namespaceName).get();

        prepareOutboundMessage(exchange, namespace);
    }

    protected void doCreateNamespace(Exchange exchange) {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific namespace require specify a namespace name");
            throw new IllegalArgumentException("Create a specific namespace require specify a namespace name");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, Map.class);
        Namespace ns
                = new NamespaceBuilder().withNewMetadata().withName(namespaceName).withLabels(labels).endMetadata().build();
        Namespace namespace = getEndpoint().getKubernetesClient().namespaces().create(ns);

        prepareOutboundMessage(exchange, namespace);
    }

    protected void doDeleteNamespace(Exchange exchange) {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific namespace require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific namespace require specify a namespace name");
        }
        Boolean namespace = getEndpoint().getKubernetesClient().namespaces().withName(namespaceName).delete();

        prepareOutboundMessage(exchange, namespace);
    }
}

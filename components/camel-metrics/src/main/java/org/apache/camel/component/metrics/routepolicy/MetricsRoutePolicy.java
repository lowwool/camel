/**
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
package org.apache.camel.component.metrics.routepolicy;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link org.apache.camel.spi.RoutePolicy} which gathers statistics and reports them using {@link com.codahale.metrics.MetricRegistry}.
 * <p/>
 * The metrics is reported in JMX by default, but this can be configured.
 */
public class MetricsRoutePolicy extends RoutePolicySupport {

    private MetricRegistry metricsRegistry;
    private MetricsRegistryService registryService;
    private boolean useJmx = true;
    private String jmxDomain = "org.apache.camel.metrics";
    private boolean prettyPrint;
    private TimeUnit rateUnit = TimeUnit.SECONDS;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private MetricsStatistics statistics;
    private Route route;

    private static final class MetricsStatistics {
        private final String routeId;
        private final Timer responses;

        private MetricsStatistics(Route route, Timer responses) {
            this.routeId = route.getId();
            this.responses = responses;
        }

        public void onExchangeBegin(Exchange exchange) {
            Timer.Context context = responses.time();
            exchange.setProperty("MetricsRoutePolicy-" + routeId, context);
        }

        public void onExchangeDone(Exchange exchange) {
            Timer.Context context = (Timer.Context) exchange.removeProperty("MetricsRoutePolicy-" + routeId);
            if (context != null) {
                context.stop();
            }
        }
    }

    public MetricRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    public void setMetricsRegistry(MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    public boolean isUseJmx() {
        return useJmx;
    }

    public void setUseJmx(boolean useJmx) {
        this.useJmx = useJmx;
    }

    public String getJmxDomain() {
        return jmxDomain;
    }

    public void setJmxDomain(String jmxDomain) {
        this.jmxDomain = jmxDomain;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public TimeUnit getRateUnit() {
        return rateUnit;
    }

    public void setRateUnit(TimeUnit rateUnit) {
        this.rateUnit = rateUnit;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        this.route = route;
        try {
            registryService = route.getRouteContext().getCamelContext().hasService(MetricsRegistryService.class);
            if (registryService == null) {
                registryService = new MetricsRegistryService();
                registryService.setMetricsRegistry(getMetricsRegistry());
                registryService.setUseJmx(isUseJmx());
                registryService.setJmxDomain(getJmxDomain());
                registryService.setPrettyPrint(isPrettyPrint());
                registryService.setRateUnit(getRateUnit());
                registryService.setDurationUnit(getDurationUnit());
                route.getRouteContext().getCamelContext().addService(registryService);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // create statistics holder
        // for know we record only all the timings of a complete exchange (responses)
        // we have in-flight / total statistics already from camel-core
        Timer responses = registryService.getMetricsRegistry().timer(createName("responses"));
        statistics = new MetricsStatistics(route, responses);
    }

    private String createName(String type) {
        CamelContext context = route.getRouteContext().getCamelContext();
        String name = context.getManagementName() != null ? context.getManagementName() : context.getName();
        // use colon to separate context from route, and dot for the type name
        return name + ":" + route.getId() + "." + type;
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeBegin(exchange);
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        if (statistics != null) {
            statistics.onExchangeDone(exchange);
        }
    }

}

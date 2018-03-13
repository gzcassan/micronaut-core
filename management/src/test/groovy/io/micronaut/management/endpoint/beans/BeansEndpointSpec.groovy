package io.micronaut.management.endpoint.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.Specification
import io.micronaut.http.client.*

class BeansEndpointSpec extends Specification {

    /**
     * Known failure of the scope. Relies on changes to the annotation
     * metadata to return the correct result.
     */
    @Ignore
    void "test beans endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/beans", Map).blockingFirst()
        Map result = response.body()
        Map<String, Map<String, Object>> beans = result.beans

        then:
        response.code() == HttpStatus.OK.code
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies[0] == "org.particleframework.context.BeanContext"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies[1] == "org.particleframework.management.endpoint.beans.BeanDefinitionDataCollector"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].scope == "singleton"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].type == "org.particleframework.management.endpoint.beans.BeansEndpoint"

        cleanup:
        embeddedServer.close()
    }
}
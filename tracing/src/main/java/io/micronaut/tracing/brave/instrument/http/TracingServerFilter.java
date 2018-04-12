/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.brave.instrument.http;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Instruments incoming HTTP requests
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter("/**")
@Requires(beans = HttpServerHandler.class)
public class TracingServerFilter extends AbstractTracingFilter implements HttpServerFilter {

    private final HttpServerHandler<HttpRequest<?>, HttpResponse<?>> serverHandler;
    private final TraceContext.Extractor<HttpHeaders> extractor;

    /**
     * @param httpTracing The {@link HttpTracing} instance
     * @param serverHandler The {@link HttpServerHandler} instance
     */
    public TracingServerFilter(
            HttpTracing httpTracing,
            HttpServerHandler<HttpRequest<?>, HttpResponse<?>> serverHandler) {
        super(httpTracing);
        this.serverHandler = serverHandler;
        this.extractor = httpTracing.tracing().propagation().extractor(ConvertibleMultiValues::get);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Flowable<? extends MutableHttpResponse<?>> requestPublisher = Flowable.fromPublisher(chain.proceed(request));
        requestPublisher = requestPublisher.doOnRequest( amount -> {
            if(amount > 0) {
                Span span = serverHandler.handleReceive(extractor, request.getHeaders(), request);
                withSpanInScope(request, span);
            }
        });
        requestPublisher = requestPublisher.doAfterTerminate(() -> afterTerminate(request));
        return requestPublisher.map(response -> {
            Optional<Span> span = configuredSpan(request, response);
            span.ifPresent(s -> {
                Throwable error = request.getAttribute(HttpAttributes.ERROR, Throwable.class).orElse(null);
                serverHandler.handleSend(response, error, s);
            });
            return response;
        });
    }

}
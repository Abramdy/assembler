/*
 * Copyright 2018 Sebastien Pelletier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.pellse.assembler.flux;

import io.github.pellse.assembler.AssemblerAdapter;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.pellse.util.ExceptionUtils.sneakyThrow;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class FluxAssemblerAdapter<ID, R> implements AssemblerAdapter<ID, R, Flux<R>> {

    private final Scheduler scheduler;

    public FluxAssemblerAdapter(Scheduler scheduler) {
        this.scheduler = requireNonNull(scheduler);
    }

    @Override
    public Flux<R> convertMapperSources(Stream<Supplier<Map<ID, ?>>> sources,
                                        Function<List<Map<ID, ?>>, Stream<R>> domainObjectStreamBuilder,
                                        Function<Throwable, RuntimeException> errorConverter) {
        List<? extends Publisher<Map<ID, ?>>> publishers = sources
                .map(supplier -> Mono.fromSupplier(supplier).subscribeOn(scheduler))
                .collect(toList());

        return Flux.zip(publishers, mapperResults -> domainObjectStreamBuilder.apply(transform(mapperResults)))
                .flatMap(Flux::fromStream)
                .doOnError(e -> sneakyThrow(errorConverter.apply(e)));
    }

    @SuppressWarnings("unchecked")
    private static <ID> List<Map<ID, ?>> transform(Object[] mapperResults) {
        return Stream.of(mapperResults)
                .map(mapResult -> (Map<ID, ?>) mapResult)
                .collect(toList());
    }

    public static <ID, R> FluxAssemblerAdapter<ID, R> fluxAssemblerAdapter() {
        return fluxAssemblerAdapter(Schedulers.parallel());
    }

    public static <ID, R> FluxAssemblerAdapter<ID, R> fluxAssemblerAdapter(Scheduler scheduler) {
        return new FluxAssemblerAdapter<>(scheduler);
    }
}

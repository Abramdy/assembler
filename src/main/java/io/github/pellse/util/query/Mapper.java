/*
 * Copyright 2017 Sebastien Pelletier
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

package io.github.pellse.util.query;

import io.github.pellse.util.function.checked.CheckedFunction1;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.pellse.util.query.QueryUtils.*;

@FunctionalInterface
public interface Mapper<ID, R, IDC extends Collection<ID>, EX extends Throwable> {

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, R, IDC, EX> oneToOneMapping(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return oneToOneMapping(queryFunction, idExtractorFromQueryResults, id -> null);
    }

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, R, IDC, EX> oneToOneMapping(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults,
            Function<ID, R> defaultResultProvider) {

        return entityIds -> queryOneToOne(entityIds, queryFunction, idExtractorFromQueryResults, defaultResultProvider);
    }

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, D, IDC,  EX> oneToManyMapping(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults,
            Supplier<D> collectionFactory) {

        return entityIds -> queryOneToMany(entityIds, queryFunction, idExtractorFromQueryResults, collectionFactory);
    }

    static <ID, IDC extends Collection<ID>, R, EX extends Throwable>
    Mapper<ID, List<R>, IDC, EX> oneToManyMappingAsList(
            CheckedFunction1<IDC, List<R>, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return entityIds -> queryOneToManyAsList(entityIds, queryFunction, idExtractorFromQueryResults);
    }

    static <ID, IDC extends Collection<ID>, R, EX extends Throwable>
    Mapper<ID, Set<R>, IDC, EX> oneToManyMappingAsSet(
            CheckedFunction1<IDC, Set<R>, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return entityIds -> queryOneToManyAsSet(entityIds, queryFunction, idExtractorFromQueryResults);
    }

    Map<ID, R> map(IDC entityIds) throws EX;
}
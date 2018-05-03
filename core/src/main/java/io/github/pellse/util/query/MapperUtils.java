package io.github.pellse.util.query;

import io.github.pellse.util.function.checked.CheckedFunction1;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.pellse.util.query.QueryUtils.*;

public interface MapperUtils {

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, R, IDC, EX> oneToOne(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return oneToOne(queryFunction, idExtractorFromQueryResults, id -> null);
    }

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, R, IDC, EX> oneToOne(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults,
            Function<ID, R> defaultResultProvider) {

        return entityIds -> queryOneToOne(entityIds, queryFunction, idExtractorFromQueryResults, defaultResultProvider);
    }

    static <ID, IDC extends Collection<ID>, R, D extends Collection<R>, EX extends Throwable>
    Mapper<ID, D, IDC, EX> oneToMany(
            CheckedFunction1<IDC, D, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults,
            Supplier<D> collectionFactory) {

        return entityIds -> queryOneToMany(entityIds, queryFunction, idExtractorFromQueryResults, collectionFactory);
    }

    static <ID, IDC extends Collection<ID>, R, EX extends Throwable>
    Mapper<ID, List<R>, IDC, EX> oneToManyAsList(
            CheckedFunction1<IDC, List<R>, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return entityIds -> queryOneToManyAsList(entityIds, queryFunction, idExtractorFromQueryResults);
    }

    static <ID, IDC extends Collection<ID>, R, EX extends Throwable>
    Mapper<ID, Set<R>, IDC, EX> oneToManyAsSet(
            CheckedFunction1<IDC, Set<R>, EX> queryFunction,
            Function<R, ID> idExtractorFromQueryResults) {

        return entityIds -> queryOneToManyAsSet(entityIds, queryFunction, idExtractorFromQueryResults);
    }
}
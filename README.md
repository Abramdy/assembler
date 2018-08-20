# Assembler
Lightweight library allowing to efficiently assemble entities from querying/merging external datasources or aggregating microservices.

More specifically it was designed as a very lightweight solution to resolve the N + 1 query problem when aggregating data, not only from database calls (e.g. Spring Data JPA, Hibernate) but from arbitrary datasources (relational databases, NoSQL, REST, local method calls, etc.).

One key feature is that the caller doesn't need to worry about the order of the data returned by the different datasources, so no need for example (in a relation database context) to modify any SQL query to add an ORDER BY clause, or (in a REST context) to modify the service implementation or manually sort results from each call before triggering the aggregation process.

Stay tuned for more complete documentation very soon in terms of more detailed explanations regarding how the library works and comparisons with other solutions, a dedicated series of blog posts is also coming on https://javatechnicalwealth.com/blog/

## Supported Technologies
Currently the following implementations are supported (with links to their respective Maven repositories):
1. [Java 8 Stream (synchronous and parallel)](https://github.com/pellse/assembler/tree/master/assembler-core)
2. [CompletableFuture](https://github.com/pellse/assembler/tree/master/assembler-core)
3. [Flux](https://github.com/pellse/assembler/tree/master/assembler-flux)
4. [RxJava](https://github.com/pellse/assembler/tree/master/assembler-rxjava)
5. [Akka Stream](https://github.com/pellse/assembler/tree/master/assembler-akka-stream)

## Use Cases

One interesting use case would be for example to build a materialized view in a microservice architecture supporting Event Sourcing and Command Query Responsibility Segregation (CQRS). In this context, if you have an incoming stream of events where each event needs to be enriched with some sort of external data before being stored (e.g. stream of GPS coordinates enriched with location service and/or weather service), it would be convenient to be able to easily batch those events instead of hitting those external services for every single event.

Another use case could be when working with JPA projections (to fetch minimal amount of data) instead of full blown JPA entities (directly with Hibernate of through Spring Data repositories). In some cases the EntityManager might not be of any help to efficiently join multiple entities together or at least it might not be trivial to cache/optimize queries to avoid the N + 1 query problem.

## Usage Examples
Assuming the following data model and api to return those entities:
```java
@Data
@AllArgsConstructor
public class Customer {
    private final Long customerId;
    private final String name;
}

@Data
@AllArgsConstructor
public class BillingInfo {
    private final Long customerId;
    private final String creditCardNumber;
}

@Data
@AllArgsConstructor
public class OrderItem {
    private final Long customerId;
    private final String orderDescription;
    private final Double price;
}

@Data
@AllArgsConstructor
public class Transaction {
    private final Customer customer;
    private final BillingInfo billingInfo;
    private final List<OrderItem> orderItems;
}

List<Customer> getCustomers(); // This could be a REST call to an already existing microservice
List<BillingInfo> getBillingInfoForCustomers(List<Long> customerIds); // This could be a call to MongoDB
List<OrderItem> getAllOrdersForCustomers(List<Long> customerIds); // This could be a call to an Oracle database
```

So if `getCustomers()` returns 50 customers, instead of having to make one additional call per *customerId* to retrieve each customer's associated `BillingInfo` (which would result in 50 additional network calls, thus the N + 1 queries issue) we can only make 1 additional call to retrieve all at once all `BillingInfo`s for all `Customer`s returned by `getCustomers()`, same for `OrderItem`s. This implies though that combining `Customer`s, `BillingInfo`s and `OrderItem`s into `Transaction`s using *customerId* as a correlation id between all those entities has to be done outside of those datasources, which is what this library was implemented for:

### [Java 8 Stream (synchronous and parallel)](https://github.com/pellse/assembler/tree/master/assembler-core)
To build the `Transaction` entity we can simply combine the invocation of the methods declared above using (from [StreamAssemblerTest](https://github.com/pellse/assembler/blob/master/assembler-core/src/test/java/io/github/pellse/assembler/stream/StreamAssemblerTest.java)):
```java
List<Transaction> transactions = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId, BillingInfo::new), // supplier of default values
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .withErrorConverter(UserDefinedRuntimeException::new) // UncheckedException by default when not specified
    .assembleUsing(streamAdapter()) // or streamAdapter(true) for parallel streams
    .collect(toList());
```
### [CompletableFuture](https://github.com/pellse/assembler/tree/master/assembler-core)
It is also possible to bind to a different execution engine (e.g. for parallel processing) just by switching to a different `AssemblerAdapter` implementation. For example, to support the aggregation process through `CompletableFuture`, just plug a `CompletableFutureAdapter` instead (from [CompletableFutureAssemblerTest](https://github.com/pellse/assembler/blob/master/assembler-core/src/test/java/io/github/pellse/assembler/future/CompletableFutureAssemblerTest.java)):
```java
CompletableFuture<List<Transaction>> transactions = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId)
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .assembleUsing(completableFutureAdapter());
```
### [Flux](https://github.com/pellse/assembler/tree/master/assembler-flux)
Reactive support is also provided through the [Spring Reactor Project](https://projectreactor.io/) to asynchronously retrieve all data to be aggregated, for example (from [FluxAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-flux/src/test/java/io/github/pellse/assembler/flux/FluxAssemblerTest.java)):
```java
Flux<Transaction> transactionFlux = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .assembleUsing(fluxAdapter(elastic()))
```
or
```java
Flux<Transaction> transactionFlux = Flux.fromIterable(getCustomers()) // or just getCustomerFlux()
    .buffer(10) // or bufferTimeout(10, ofSeconds(5)) to e.g. batch every 5 seconds or max of 10 customers
    .flatMap(customers -> assemblerOf(Transaction.class)
        .fromSource(customers, Customer::getCustomerId)
        .withAssemblerRules(
            oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
            oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
            Transaction::new)
        .assembleUsing(fluxAdapter())) // parallel scheduler used by default
```
### [RxJava](https://github.com/pellse/assembler/tree/master/assembler-rxjava)
In addition to the Flux implementation, [RxJava](https://github.com/ReactiveX/RxJava) is also supported through `Observable`s and `Flowable`s.

With Observable support (from [ObservableAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-rxjava/src/test/java/io/github/pellse/assembler/rxjava/ObservableAssemblerTest.java)):
```java
Observable<Transaction> transactionObservable = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .assembleUsing(observableAdapter())
```
With Flowable support (from [FlowableAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-rxjava/src/test/java/io/github/pellse/assembler/rxjava/FlowableAssemblerTest.java)):
```java
Flowable<Transaction> transactionFlowable = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .assembleUsing(flowableAdapter())
```
### [Akka Stream](https://github.com/pellse/assembler/tree/master/assembler-akka-stream)
By using an `AkkaSourceAdapter` we can support the [Akka Stream](https://akka.io/) framework by creating instances of Akka `Source` (from [AkkaSourceAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-akka-stream/src/test/java/io/github/pellse/assembler/akkastream/AkkaSourceAssemblerTest.java)):
```java
ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Source<Transaction, NotUsed> transactionSource = assemblerOf(Transaction.class)
    .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
        Transaction::new)
    .assembleUsing(akkaSourceAdapter())); // Sequential

transactionSource.runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
or
```java
ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Source<Transaction, NotUsed> transactionSource = Source.fromSource(getCustomers())
    .groupedWithin(3, ofSeconds(5))
    .flatMapConcat(customerList ->
        assemblerOf(Transaction.class)
            .fromSource(customerList, Customer::getCustomerId)
            .withAssemblerRules(
                oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
                oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
                Transaction::new)
            .assembleUsing(akkaSourceAdapter(true))); // Parallel

transactionSource.runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
It is also possible to create an Akka `Flow` from the Assembler DSL:
```java
ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Source<Customer, NotUsed> customerSource = Source.fromSource(getCustomers());

Flow<Customer, Transaction, NotUsed> transactionFlow = Flow.<Customer>create()
    .grouped(3)
    .flatMapConcat(customerList ->
        assemblerOf(Transaction.class)
            .fromSource(customerList, Customer::getCustomerId)
            .withAssemblerRules(
                oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId),
                oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId),
                Transaction::new)
            .assembleUsing(akkaSourceAdapter(Source::async))); // Custom underlying sources configuration
        
customerSource.via(transactionFlow)
    .runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
## Caching
In addition to providing helper functions to define mapping semantics (e.g. `oneToOne()`, `manyToOne()`), `MapperUtils` also provides a simple caching/memoization mechanism through the `cached()` wrapper method.

Below is a rewrite of the first example above but one of the `Mapper`s is cached (for the `getBillingInfoForCustomers` MongoDB call), so on multiple invocations of the defined assembler, the mapper result from the first invocation will be reused, avoiding to hit the datastore again:
```java
var billingInfoMapper = cached(oneToOne(this::getBillingInfoForCustomers, BillingInfo::getCustomerId));
var allOrdersMapper = oneToManyAsList(this::getAllOrdersForCustomers, OrderItem::getCustomerId);

var assembler = assemblerOf(Transaction.class)
        .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
        .withAssemblerRules(billingInfoMapper, allOrdersMapper, Transaction::new);

var transactionList = assembler.assembleUsing(streamAdapter())
        .collect(toList()); // Will invoke the getBillingInfoForCustomers() MongoDB remote call

var transactionsList2 = assembler.assembleUsing(streamAdapter())
        .collect(toList()); // Will reuse the results returned from
                            // the first invocation of getBillingInfoForCustomers() above
                            // if the list of Customers is the same (as defined by the list equals() method)
                            // for both invocations, no remote call here
```
This can be useful for querying static data or data we know doesn't change often (or on a predefined scheduled e.g. data that is refreshed by a batch job once a day).

Note that an override of the `cached()` method is also defined to allow plugging your own cache implementation.

## What's Next?
See the [list of issues](https://github.com/pellse/assembler/issues) for planned improvements in a near future.

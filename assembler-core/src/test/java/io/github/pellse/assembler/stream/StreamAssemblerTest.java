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

package io.github.pellse.assembler.stream;

import io.github.pellse.assembler.Assembler.AssembleUsingBuilder;
import io.github.pellse.assembler.AssemblerTestUtils;
import io.github.pellse.util.function.checked.UncheckedException;
import io.github.pellse.util.query.Mapper;
import org.junit.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static io.github.pellse.assembler.Assembler.assemblerOf;
import static io.github.pellse.assembler.AssemblerTestUtils.*;
import static io.github.pellse.assembler.stream.StreamAdapter.streamAdapter;
import static io.github.pellse.util.query.MapperUtils.*;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Sebastien Pelletier
 */
public class StreamAssemblerTest {

    private List<Customer> getCustomers() {
        return asList(customer1, customer2, customer3);
    }

    @Test
    public void testAssembleBuilder() {

        List<Transaction> transactions = assemblerOf(Transaction.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfoForCustomers, BillingInfo::getCustomerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::getAllOrdersForCustomers, OrderItem::getCustomerId),
                        Transaction::new)
                .assembleUsing(streamAdapter())
                .collect(toList());

        assertThat(transactions, equalTo(List.of(transaction1, transaction2, transaction3)));
    }

    @Test(expected = UncheckedException.class)
    public void testAssembleBuilderWithUncheckedException() {

        assemblerOf(Transaction.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::throwSQLException, BillingInfo::getCustomerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::throwSQLException, OrderItem::getCustomerId),
                        Transaction::new)
                .assembleUsing(streamAdapter());
    }

    @Test(expected = UserDefinedRuntimeException.class)
    public void testAssembleBuilderWithCustomException() {

        assemblerOf(Transaction.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::throwSQLException, BillingInfo::getCustomerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::throwSQLException, OrderItem::getCustomerId),
                        Transaction::new)
                .withErrorConverter(UserDefinedRuntimeException::new)
                .assembleUsing(streamAdapter());
    }

    @Test
    public void testAssembleBuilderWithNonListIds() {

        List<TransactionSet> transactions = assemblerOf(TransactionSet.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfoForCustomersWithSetIds, BillingInfo::getCustomerId, BillingInfo::new, HashSet::new),
                        oneToManyAsSet(AssemblerTestUtils::getAllOrdersForCustomersWithLinkedListIds, OrderItem::getCustomerId, LinkedList::new),
                        TransactionSet::new)
                .assembleUsing(streamAdapter())
                .collect(toList());

        assertThat(transactions, equalTo(List.of(transactionSet1, transactionSet2, transactionSet3)));
    }

    @Test
    public void testAssembleBuilderWithNullBillingInfo() {

        List<Transaction> transactions = assemblerOf(Transaction.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfoForCustomers, BillingInfo::getCustomerId),
                        oneToManyAsList(AssemblerTestUtils::getAllOrdersForCustomers, OrderItem::getCustomerId),
                        Transaction::new)
                .assembleUsing(streamAdapter())
                .collect(toList());

        assertThat(transactions, equalTo(List.of(transaction1, transaction2WithNullBillingInfo, transaction3)));
    }

    @Test
    public void testAssembleBuilderWithCachedMappers() {

        Mapper<Long, BillingInfo, SQLException> getBillingInfo = cached(oneToOne(AssemblerTestUtils::getBillingInfoForCustomers, BillingInfo::getCustomerId));
        Mapper<Long, List<OrderItem>, SQLException> getAllOrders = oneToManyAsList(AssemblerTestUtils::getAllOrdersForCustomers, OrderItem::getCustomerId);

        AssembleUsingBuilder<Long, Transaction> builder = assemblerOf(Transaction.class)
                .fromSourceSupplier(this::getCustomers, Customer::getCustomerId)
                .withAssemblerRules(getBillingInfo, getAllOrders, Transaction::new);

        List<Transaction> transactions1 = builder.assembleUsing(streamAdapter())
                .collect(toList());

        assertThat(transactions1, equalTo(List.of(transaction1, transaction2WithNullBillingInfo, transaction3)));
    }
}

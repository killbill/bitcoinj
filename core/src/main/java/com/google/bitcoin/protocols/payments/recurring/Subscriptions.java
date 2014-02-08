/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.bitcoin.protocols.payments.recurring;

import com.google.bitcoin.store.RecurringPaymentProtobufSerializer;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.bitcoin.protocols.payments.Protos;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

public class Subscriptions {

    private static String SUBSCRIPTIONS_FILE_NAME = "subscriptions";

    private File walletDirectory;
    private RecurringPaymentProtobufSerializer serializer;
    private List<org.bitcoin.protocols.subscriptions.Protos.Subscription> subscriptions;

    public static void storeContract(Protos.PaymentDetails paymentDetails, File walletDirectory) throws IOException {
        File f = new File(walletDirectory, SUBSCRIPTIONS_FILE_NAME);
        f.createNewFile();

        RecurringPaymentProtobufSerializer recurringPaymentProtobufSerializer = new RecurringPaymentProtobufSerializer();
        recurringPaymentProtobufSerializer.storeContract(paymentDetails, f);
    }

    public Subscriptions(File walletDirectory) {
        this.walletDirectory = walletDirectory;
        this.serializer = new RecurringPaymentProtobufSerializer();
    }

    public void load() throws IOException {
        File f = new File(walletDirectory, SUBSCRIPTIONS_FILE_NAME);
        Files.touch(f);

        InputStream in = new FileInputStream(f);
        try {
            this.subscriptions = serializer.loadSubscriptions(in);
        } finally {
            in.close();
        }
    }

    public List<Protos.PaymentDetails> getAllContracts() {
        return Lists.transform(subscriptions, new Function<org.bitcoin.protocols.subscriptions.Protos.Subscription, Protos.PaymentDetails>() {
            @Override
            public Protos.PaymentDetails apply(org.bitcoin.protocols.subscriptions.Protos.Subscription input) {
                return input.getContract();
            }
        });
    }

    public BigInteger getPaidAmountForPeriod(Protos.PaymentDetails contract) {
        final String key = serializer.getUniqueKeyForContract(contract);
        org.bitcoin.protocols.subscriptions.Protos.Subscription subscription = Iterables.tryFind(subscriptions, new Predicate<org.bitcoin.protocols.subscriptions.Protos.Subscription>() {
            @Override
            public boolean apply(org.bitcoin.protocols.subscriptions.Protos.Subscription input) {
                return key.equals(serializer.getUniqueKeyForContract(input.getContract()));
            }
        }).get();

        long paidAmountForPeriod = 0;
        for (Protos.PaymentDetails pastPaymentForPeriod : subscription.getPaymentsForPeriodList()) {
            for (Protos.Output output : pastPaymentForPeriod.getOutputsList()) {
                paidAmountForPeriod += output.getAmount();
            }
        }

        return BigInteger.valueOf(paidAmountForPeriod);
    }
}

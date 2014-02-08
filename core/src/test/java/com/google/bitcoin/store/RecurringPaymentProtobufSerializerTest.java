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

package com.google.bitcoin.store;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.params.TestNet3Params;
import com.google.bitcoin.protocols.payments.recurring.Subscriptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.bitcoin.protocols.payments.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

public class RecurringPaymentProtobufSerializerTest {

    private static final NetworkParameters params = TestNet3Params.get();
    private static final String simplePaymentUrl = "http://a.simple.url.com/";
    private static final String paymentRequestMemo = "send coinz noa plz kthx";
    private static final ByteString merchantData = ByteString.copyFromUtf8("merchant data");
    private static final long time = System.currentTimeMillis() / 1000L;
    private ECKey serverKey;
    private Transaction tx;
    private TransactionOutput outputToMe;
    BigInteger nanoCoins = Utils.toNanoCoins(1, 0);

    @Before
    public void setUp() throws Exception {
        serverKey = new ECKey();
        tx = new Transaction(params);
        outputToMe = new TransactionOutput(params, tx, nanoCoins, serverKey);
        tx.addOutput(outputToMe);
    }

    @Test
    public void testOnePaymentDetails() throws Exception {

        Protos.Output.Builder outputBuilder = Protos.Output.newBuilder()
                .setAmount(nanoCoins.longValue())
                .setScript(ByteString.copyFrom(outputToMe.getScriptBytes()));
        Protos.RecurringPaymentDetails recurringPaymentDetails = Protos.RecurringPaymentDetails.newBuilder()
                .setPollingUrl(simplePaymentUrl)
                .setMaxPaymentPerPeriod(1000)
                .setPaymentFrequencyType(Protos.PaymentFrequencyType.ANNUAL)
                .build();
        Protos.PaymentDetails paymentDetails = Protos.PaymentDetails.newBuilder()
                .setNetwork("test")
                .setTime(time - 10)
                .setExpires(time - 1)
                .setPaymentUrl(simplePaymentUrl)
                .addOutputs(outputBuilder)
                .setMemo(paymentRequestMemo)
                .setMerchantData(merchantData)
                .setSerializedRecurringPaymentDetails(recurringPaymentDetails.toByteString())
                .build();

        RecurringPaymentProtobufSerializer serializer = new RecurringPaymentProtobufSerializer();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        org.bitcoin.protocols.subscriptions.Protos.Subscription subscription = org.bitcoin.protocols.subscriptions.Protos.Subscription.newBuilder()
                .setContract(paymentDetails)
                .build();
        serializer.writePaymentDetails(ImmutableList.of(subscription), output);

        List<org.bitcoin.protocols.subscriptions.Protos.Subscription> result = serializer.loadSubscriptions(new ByteArrayInputStream(output.toByteArray()));
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getContract(), paymentDetails);
        Assert.assertEquals(0, result.get(0).getPaymentsForPeriodList().size());
    }

    @Test
    public void testMultiplePaymentDetails() throws Exception {
        List<org.bitcoin.protocols.subscriptions.Protos.Subscription> allSubscriptions = Lists.newArrayList();
        for (int i = 0 ; i < 12; i++) {
            Protos.Output.Builder outputBuilder = Protos.Output.newBuilder()
                    .setAmount(nanoCoins.longValue())
                    .setScript(ByteString.copyFrom(outputToMe.getScriptBytes()));
            Protos.RecurringPaymentDetails recurringPaymentDetails = Protos.RecurringPaymentDetails.newBuilder()
                    .setPollingUrl(simplePaymentUrl)
                    .setMaxPaymentPerPeriod(1000)
                    .setPaymentFrequencyType(Protos.PaymentFrequencyType.ANNUAL)
                    .build();
            Protos.PaymentDetails paymentDetails = Protos.PaymentDetails.newBuilder()
                    .setNetwork("test")
                    .setTime(time)
                    .setExpires(time - i)
                    .setPaymentUrl(simplePaymentUrl)
                    .addOutputs(outputBuilder)
                    .setMemo(paymentRequestMemo)
                    .setMerchantData(merchantData)
                    .setSerializedRecurringPaymentDetails(recurringPaymentDetails.toByteString())
                    .build();

            org.bitcoin.protocols.subscriptions.Protos.Subscription subscription = org.bitcoin.protocols.subscriptions.Protos.Subscription.newBuilder()
                    .setContract(paymentDetails)
                    .build();
            allSubscriptions.add(subscription);
        }

        RecurringPaymentProtobufSerializer serializer = new RecurringPaymentProtobufSerializer();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.writePaymentDetails(allSubscriptions, output);

        List<org.bitcoin.protocols.subscriptions.Protos.Subscription> result = serializer.loadSubscriptions(new ByteArrayInputStream(output.toByteArray()));
        Assert.assertEquals(12, result.size());
        for (int i = 0; i < 12; i++) {
            Assert.assertEquals(time - i, result.get(i).getContract().getExpires());
            Assert.assertEquals(0, result.get(i).getPaymentsForPeriodList().size());
        }
    }
}

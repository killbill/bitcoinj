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

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.protocols.payments.PaymentRequestException;
import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bitcoin.protocols.payments.Protos;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

public class PollingRunnable implements Runnable {

    private final File walletDirectory;
    private final PollingCallback callback;
    private final boolean verifyPki;

    public PollingRunnable(File walletDirectory, PollingCallback callback, boolean verifyPki) {
        this.walletDirectory = walletDirectory;
        this.callback = callback;
        this.verifyPki = verifyPki;
    }

    @Override
    public void run() {
        Subscriptions subscriptions = new Subscriptions(walletDirectory);
        try {
            subscriptions.load();
        } catch (Exception e) {
            callback.onException(e, null, null);
            return;
        }

        int nbPaymentsSent = 0;
        try {
            for (Protos.PaymentDetails subscriptionContracts : subscriptions.getAllContracts()) {

                Preconditions.checkState(subscriptionContracts.hasSerializedRecurringPaymentDetails());
                Protos.RecurringPaymentDetails recurringPaymentDetailsForContract = Protos.RecurringPaymentDetails.newBuilder().mergeFrom(subscriptionContracts.getSerializedRecurringPaymentDetails()).build();

                for (Protos.RecurringPaymentContract activeContract : getActiveContracts(recurringPaymentDetailsForContract)) {
                    try {
                        nbPaymentsSent += doRecurringPayment(recurringPaymentDetailsForContract.getMerchantId(), recurringPaymentDetailsForContract.getSubscriptionId(), activeContract, subscriptions, nbPaymentsSent);
                    } catch (Exception e) {
                        callback.onException(e, subscriptionContracts, activeContract);
                    }
                }
            }
        } catch (Exception e) {
            callback.onException(e, null, null);
        }

        callback.onCompletion(nbPaymentsSent);
    }

    public Collection<Protos.RecurringPaymentContract> getActiveContracts(Protos.RecurringPaymentDetails recurringPaymentDetailsForContract) throws InvalidProtocolBufferException {

        return Collections2.<Protos.RecurringPaymentContract>filter(recurringPaymentDetailsForContract.getContractsList(), new Predicate<Protos.RecurringPaymentContract>() {
            @Override
            public boolean apply(Protos.RecurringPaymentContract input) {
                long now = Utils.currentTimeMillis();
                return (input.getStarts() <= now && (!input.hasEnds() || input.getEnds() >= now));
            }
        });
    }


    private int doRecurringPayment(String merchantId, ByteString subscriptionId, Protos.RecurringPaymentContract contract, Subscriptions subscriptions, int nbPaymentsSent) throws InterruptedException, ExecutionException, PaymentRequestException, IOException {

        // Get the latest PaymentRequest from the server
        PaymentSession newSession = PaymentSession.createFromUrl(contract.getPollingUrl(), verifyPki).get();

        // Bail if there is nothing to pay or if the original contract is not respected
        Wallet.SendRequest newSendRequest = newSession.getSendRequest();
        if (validateAndPreparePaymentRequest(merchantId, subscriptionId, contract, newSession, newSendRequest, subscriptions)) {
            nbPaymentsSent++;

            // Send it
            ListenableFuture<PaymentSession.Ack> ack = newSession.sendPayment(ImmutableList.<Transaction>of(newSendRequest.tx), null, null);
            callback.onAck(newSendRequest, ack);
        }

        return nbPaymentsSent;
    }

    private boolean validateAndPreparePaymentRequest(String merchantId, ByteString subscriptionId, Protos.RecurringPaymentContract contract, PaymentSession newSession, Wallet.SendRequest sendRequest, Subscriptions subscriptions) throws InvalidProtocolBufferException {
        if (BigInteger.ZERO.compareTo(newSession.getValue()) >= 0) {
            // Nothing to pay
            return false;
        }

        // In the recurring phase, PaymentRequest shouldn't have recurring payment details
        if (newSession.getPaymentDetails().hasSerializedRecurringPaymentDetails()) {
            return false;
        }

        BigInteger curPaymentAmountPerPeriod = subscriptions.getPaidAmountForPeriod(merchantId, subscriptionId, contract.getContractId());

        // Prepare the Payment
        return callback.preparePayment(sendRequest, BigInteger.valueOf(contract.getMaxPaymentAmount()), contract.getPaymentFrequencyType(),
                BigInteger.valueOf(contract.getMaxPaymentPerPeriod()), newSession.getValue(), curPaymentAmountPerPeriod);
    }
}

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

import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoin.protocols.payments.Protos;

import javax.annotation.Nullable;
import java.math.BigInteger;

public interface PollingCallback {
    /**
     * Validate the recurring payment is within bounds of the initial contract and prepare the Payment for the merchant
     *
     * @param sendRequest               SendRequest object prepared for that Payment
     * @param maxAgreedPaymentAmount    maximum amount per payment in the initial contract
     * @param frequencyType             expected payment frequency period
     * @param maxAgreedPaymentAmountPerPeriod
     *                                  maximum amount for all payments during a period
     * @param curPaymentAmount          payment requested by the merchant
     * @param curPaymentAmountPerPeriod current amount sent to the merchant since the beginning of the current period
     * @return true if the payment should go through, false otherwise
     */
    public boolean preparePayment(Wallet.SendRequest sendRequest,
                                  BigInteger maxAgreedPaymentAmount,
                                  Protos.PaymentFrequencyType frequencyType,
                                  BigInteger maxAgreedPaymentAmountPerPeriod,
                                  BigInteger curPaymentAmount,
                                  BigInteger curPaymentAmountPerPeriod);

    /**
     * Called after sending the Payment to the merchant
     *
     * @param request SendRequest object associated with the Payment
     * @param ack     Ack from the merchant
     */
    public void onAck(Wallet.SendRequest request, @Nullable ListenableFuture<PaymentSession.Ack> ack);

    /**
     * Generic exception handler
     *
     * @param e        exception thrown
     * @param contract associated contract
     */
    public void onException(Exception e, @Nullable Protos.PaymentDetails contract);

    /**
     * Called at the end of each polling run, after merchants have been polled for each recurring payment
     *
     * @param nbPaymentsSent number of sent payments
     */
    public void onCompletion(int nbPaymentsSent);
}

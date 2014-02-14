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

import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.bitcoin.protocols.payments.Protos;

public class RecurringAck {

    private final ListenableFuture<PaymentSession.Ack> ack;
    private final String merchantId;
    private final ByteString subscriptionId;
    private final Protos.RecurringPaymentContract contract;
    private final Protos.PaymentDetails paymentDetails;

    public RecurringAck(ListenableFuture<PaymentSession.Ack> ack, String merchantId, ByteString subscriptionId, Protos.RecurringPaymentContract contract, Protos.PaymentDetails paymentDetails) {
        this.ack = ack;
        this.merchantId = merchantId;
        this.subscriptionId = subscriptionId;
        this.contract = contract;
        this.paymentDetails = paymentDetails;
    }

    public ListenableFuture<PaymentSession.Ack> getAck() {
        return ack;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public ByteString getSubscriptionId() {
        return subscriptionId;
    }

    public Protos.RecurringPaymentContract getContract() {
        return contract;
    }

    public Protos.PaymentDetails getPaymentDetails() {
        return paymentDetails;
    }
}

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

import com.google.bitcoin.utils.Threading;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import org.bitcoin.protocols.payments.Protos;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RecurringPaymentSession {

    private static ListeningScheduledExecutorService scheduledExecutor = Threading.SCHEDULED_THREAD_POOL;

    public static ScheduledFuture<?> startPollingForRecurringPayments(final File walletDirectory, final PollingCallback callback, final boolean verifyPki) throws IOException {
        return scheduledExecutor.scheduleAtFixedRate(new PollingRunnable(walletDirectory, callback, verifyPki), 0, 24, TimeUnit.HOURS);
    }

    public static void storeRecurringPaymentInfoIfRequired(Protos.PaymentDetails paymentDetails, File walletDirectory) throws IOException {
        if (paymentDetails.hasSerializedRecurringPaymentDetails()) {
            Subscriptions.storeContract(paymentDetails, walletDirectory);
        }
    }
}

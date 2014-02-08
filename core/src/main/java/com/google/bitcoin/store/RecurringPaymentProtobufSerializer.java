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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.bitcoin.protocols.subscriptions.Protos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class RecurringPaymentProtobufSerializer {

    // New subscription
    public void storeContract(org.bitcoin.protocols.payments.Protos.PaymentDetails contract, File subscriptionsFile) throws IOException {
        // We are recreating the list of recurring payments in a temp file that we will atomically move upon completion
        File temp = File.createTempFile(subscriptionsFile.getName(), ".tmp", subscriptionsFile.getAbsoluteFile().getParentFile());

        final FileInputStream input = new FileInputStream(subscriptionsFile);
        List<Protos.Subscription> allSubscriptions = loadSubscriptions(input);

        List<Protos.Subscription> allUpdatedPaymentDetails = Lists.newLinkedList();
        Protos.Subscription newSubscriptionForContract = Protos.Subscription.newBuilder().setContract(contract).build();
        allUpdatedPaymentDetails.add(newSubscriptionForContract);

        String keyForNewContract = getUniqueKeyForContract(contract);
        for (Protos.Subscription subscription : allSubscriptions) {
            String key = getUniqueKeyForContract(subscription.getContract());

            // Consider only subscriptions for contracts other than the one we're adding or updating
            if (!key.equals(keyForNewContract)) {
                allUpdatedPaymentDetails.add(subscription);
            }
        }

        final FileOutputStream output = new FileOutputStream(temp);
        try {
            writePaymentDetails(allUpdatedPaymentDetails, output);

            // Final rename
            if (!temp.renameTo(subscriptionsFile)) {
                throw new IOException("Final rename failed");
            }
        } finally {
            try {
                input.close();
            } finally {
                output.close();
            }
        }
    }

    public List<Protos.Subscription> loadSubscriptions(InputStream input) throws IOException {
        List<Protos.Subscription> result = Lists.newLinkedList();
        while (input.available() > 0) {
            result.add(Protos.Subscription.parseDelimitedFrom(input));
        }
        return result;
    }

    public String getUniqueKeyForContract(org.bitcoin.protocols.payments.Protos.PaymentDetails contract) {
        return contract.getPaymentUrl() + contract.getTime();
    }

    @VisibleForTesting
    void writePaymentDetails(List<Protos.Subscription> subscriptions, OutputStream output) throws IOException {
        for (Protos.Subscription subscription : subscriptions) {
            subscription.writeDelimitedTo(output);
        }
    }

}

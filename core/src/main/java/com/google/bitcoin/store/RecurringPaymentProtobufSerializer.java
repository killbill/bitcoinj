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

import com.google.bitcoin.core.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bitcoin.protocols.subscriptions.Protos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RecurringPaymentProtobufSerializer {

    public void storeContract(final org.bitcoin.protocols.payments.Protos.PaymentDetails paymentDetailsContract, File subscriptionsFile) throws IOException {
        org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails retrievedRecurringPaymentDetails = org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails.newBuilder().mergeFrom(paymentDetailsContract.getSerializedRecurringPaymentDetails()).build();

        // Read existing subscriptions
        final FileInputStream input = new FileInputStream(subscriptionsFile);
        List<Protos.Subscription> allSubscriptions = loadSubscriptions(input);

        boolean foundSubscription = false;
        List<Protos.Subscription> subscriptions = Lists.newLinkedList();
        for (Protos.Subscription subscription : allSubscriptions) {
            // Check if we need to update it
            if (Objects.equals(subscription.getMerchantId(), retrievedRecurringPaymentDetails.getMerchantId()) &&
                    Objects.equals(subscription.getSubscriptionId(), retrievedRecurringPaymentDetails.getSubscriptionId())) {

                foundSubscription = true;

                org.bitcoin.protocols.payments.Protos.PaymentDetails existingContractsOnDisk = subscription.getSubscriptionContracts();
                List<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> retrievedContracts = retrievedRecurringPaymentDetails.getContractsList();
                org.bitcoin.protocols.payments.Protos.PaymentDetails refreshedContracts = refreshContractsForSubscription(existingContractsOnDisk, retrievedContracts);

                // Construct the new subscription
                subscription = Protos.Subscription.newBuilder(subscription).setSubscriptionContracts(refreshedContracts).build();
            }
            subscriptions.add(subscription);
        }

        if (!foundSubscription) {
            Protos.Subscription newSubscription =  Protos.Subscription.newBuilder()
                    .setMerchantId(retrievedRecurringPaymentDetails.getMerchantId())
                    .setSubscriptionId(retrievedRecurringPaymentDetails.getSubscriptionId())
                    .setSubscriptionContracts(paymentDetailsContract)
                    .build();
            subscriptions.add(newSubscription);
        }

        // We are recreating the list of recurring payments in a temp file that we will atomically move upon completion
        File temp = File.createTempFile(subscriptionsFile.getName(), ".tmp", subscriptionsFile.getAbsoluteFile().getParentFile());
        final FileOutputStream output = new FileOutputStream(temp);
        try {
            writeSubscriptions(subscriptions, output);

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

    private org.bitcoin.protocols.payments.Protos.PaymentDetails refreshContractsForSubscription(org.bitcoin.protocols.payments.Protos.PaymentDetails existingContractsOnDisk, Iterable<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> retrievedContracts) throws InvalidProtocolBufferException {
        if (existingContractsOnDisk.hasSerializedRecurringPaymentDetails()) {
            org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails existingRecurringPaymentDetailsOnDisk = org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails.newBuilder().mergeFrom(existingContractsOnDisk.getSerializedRecurringPaymentDetails()).build();
            List<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> existingContracts = existingRecurringPaymentDetailsOnDisk.getContractsList();
            return refreshContractsForSubscription(existingContractsOnDisk, existingRecurringPaymentDetailsOnDisk, existingContracts, retrievedContracts);
        }

        return existingContractsOnDisk;
    }

    private org.bitcoin.protocols.payments.Protos.PaymentDetails refreshContractsForSubscription(org.bitcoin.protocols.payments.Protos.PaymentDetails originalPaymentDetails,
                                                                                                 org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails originalRecurringPaymentDetails,
                                                                                                 Iterable<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> existingContractsOnDisk,
                                                                                                 Iterable<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> retrievedContracts) throws InvalidProtocolBufferException {
        Map<ByteString, org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> existingContractsMap = buildContractMap(existingContractsOnDisk);
        Map<ByteString, org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> retrievedContractsMap = buildContractMap(retrievedContracts);
        Set contractIdsToUpdate = Sets.intersection(existingContractsMap.keySet(), retrievedContractsMap.keySet());

        List<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> updatedContracts = new LinkedList<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract>();

        // Add all existing except the ones which are updated
        for (org.bitcoin.protocols.payments.Protos.RecurringPaymentContract existingContract : existingContractsOnDisk) {
            if (!contractIdsToUpdate.contains(existingContract.getContractId())) {
                updatedContracts.add(existingContract);
            }
        }

        // Add new and updated contracts
        for (org.bitcoin.protocols.payments.Protos.RecurringPaymentContract retrievedContract : retrievedContracts) {
            if (!cancelled(retrievedContract)) {
                updatedContracts.add(retrievedContract);
            }
        }

        org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails updatedRecurringPaymentDetails = org.bitcoin.protocols.payments.Protos.RecurringPaymentDetails.newBuilder(originalRecurringPaymentDetails)
                .clearContracts()
                .addAllContracts(updatedContracts)
                .build();

        return org.bitcoin.protocols.payments.Protos.PaymentDetails.newBuilder(originalPaymentDetails)
                .setSerializedRecurringPaymentDetails(updatedRecurringPaymentDetails.toByteString())
                .build();
    }

    private boolean cancelled(org.bitcoin.protocols.payments.Protos.RecurringPaymentContract contract) {
        return contract.hasEnds() && contract.getEnds() <= Utils.currentTimeMillis();
    }

    private Map<ByteString, org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> buildContractMap(Iterable<org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> contracts) {
        Map<ByteString, org.bitcoin.protocols.payments.Protos.RecurringPaymentContract> contractMap = new HashMap<ByteString, org.bitcoin.protocols.payments.Protos.RecurringPaymentContract>();
        for (org.bitcoin.protocols.payments.Protos.RecurringPaymentContract contract : contracts) {
            contractMap.put(contract.getContractId(), contract);
        }
        return contractMap;
    }

    @VisibleForTesting
    void writeSubscriptions(List<Protos.Subscription> subscriptions, OutputStream output) throws IOException {
        for (Protos.Subscription subscription : subscriptions) {
            subscription.writeDelimitedTo(output);
        }
    }
}

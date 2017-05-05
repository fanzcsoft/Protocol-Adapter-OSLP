/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.application.services;

import java.net.InetAddress;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alliander.osgp.adapter.protocol.oslp.application.services.oslp.OslpDeviceSettingsService;
import com.alliander.osgp.adapter.protocol.oslp.domain.entities.OslpDevice;
import com.alliander.osgp.adapter.protocol.oslp.exceptions.ProtocolAdapterException;
import com.alliander.osgp.adapter.protocol.oslp.infra.messaging.OsgpRequestMessageSender;
import com.alliander.osgp.dto.valueobjects.DeviceRegistrationDataDto;
import com.alliander.osgp.shared.infra.jms.RequestMessage;

@Service(value = "oslpDeviceRegistrationService")
@Transactional(value = "transactionManager")
public class DeviceRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceRegistrationService.class);

    @Autowired
    private Integer sequenceNumberWindow;

    @Autowired
    private Integer sequenceNumberMaximum;

    @Autowired
    private OslpDeviceSettingsService oslpDeviceSettingsService;

    @Autowired
    private OsgpRequestMessageSender osgpRequestMessageSender;

    /**
     * Constructor
     */
    public DeviceRegistrationService() {
        // Parameterless constructor required for transactions...
    }

    public void setSequenceNumberWindow(final Integer sequenceNumberWindow) {
        this.sequenceNumberWindow = sequenceNumberWindow;
    }

    public void setSequenceNumberMaximum(final Integer sequenceNumberMaximum) {
        this.sequenceNumberMaximum = sequenceNumberMaximum;
    }

    public OslpDevice findDevice(final byte[] deviceId) throws ProtocolAdapterException {

        // Convert byte array to String.
        final String deviceUid = Base64.encodeBase64String(deviceId);

        final OslpDevice oslpDevice = this.oslpDeviceSettingsService.getDeviceByUid(deviceUid);
        if (oslpDevice == null) {
            throw new ProtocolAdapterException("Unable to find device using deviceUid: " + deviceUid);
        }

        return oslpDevice;
    }

    public void sendDeviceRegisterRequest(final InetAddress inetAddress, final String deviceType,
            final boolean hasSchedule, final String deviceIdentification) {

        final DeviceRegistrationDataDto deviceRegistrationData = new DeviceRegistrationDataDto(inetAddress
                .getHostAddress().toString(), deviceType, hasSchedule);

        final RequestMessage requestMessage = new RequestMessage("no-correlationUid", "no-organisation",
                deviceIdentification, deviceRegistrationData);

        this.osgpRequestMessageSender.send(requestMessage, "REGISTER_DEVICE");
    }

    public void confirmRegisterDevice(final byte[] deviceId, final Integer newSequenceNumber,
            final Integer randomDevice, final Integer randomPlatform) throws ProtocolAdapterException {

        this.checkDeviceRandomAndPlatformRandom(deviceId, randomDevice, randomPlatform);

        this.updateDeviceSequenceNumber(deviceId, newSequenceNumber);

        LOGGER.debug("confirmRegisterDevice successful for device with UID: {}.", deviceId);
    }

    private void checkDeviceRandomAndPlatformRandom(final byte[] deviceId, final Integer randomDevice,
            final Integer randomPlatform) throws ProtocolAdapterException {
        // Lookup device.
        final OslpDevice oslpDevice = this.findDevice(deviceId);
        // Check the random number generated by the device.
        if (randomDevice == null || oslpDevice.getRandomDevice() == null) {
            throw new ProtocolAdapterException("RandomDevice not set");
        }
        if (oslpDevice.getRandomDevice() - randomDevice != 0) {
            throw new ProtocolAdapterException("RandomDevice incorrect");
        }
        // Check the random number generated by the platform.
        if (randomPlatform == null || oslpDevice.getRandomPlatform() == null) {
            throw new ProtocolAdapterException("RandomPlatform not set");
        }
        if (oslpDevice.getRandomPlatform() - randomPlatform != 0) {
            throw new ProtocolAdapterException("RandomPlatform incorrect");
        }
    }

    public void updateDeviceSequenceNumber(final byte[] deviceId, final int newSequenceNumber)
            throws ProtocolAdapterException {

        // Lookup device.
        final OslpDevice oslpDevice = this.findDevice(deviceId);

        this.checkSequenceNumber(oslpDevice.getSequenceNumber(), newSequenceNumber);

        // Persist the new sequence number.
        oslpDevice.setSequenceNumber(newSequenceNumber);
        this.oslpDeviceSettingsService.updateDevice(oslpDevice);
    }

    public void checkSequenceNumber(final byte[] deviceId, final Integer newSequenceNumber)
            throws ProtocolAdapterException {

        // Lookup device.
        final OslpDevice oslpDevice = this.findDevice(deviceId);

        this.checkSequenceNumber(oslpDevice.getSequenceNumber(), newSequenceNumber);
    }

    public void checkSequenceNumber(final Integer currentSequenceNumber, final Integer newSequenceNumber)
            throws ProtocolAdapterException {

        int expectedSequenceNumber = currentSequenceNumber + 1;
        if (expectedSequenceNumber > this.sequenceNumberMaximum) {
            expectedSequenceNumber = 0;
        }

        if (Math.abs(expectedSequenceNumber - newSequenceNumber) <= this.sequenceNumberWindow
                || Math.abs(expectedSequenceNumber - newSequenceNumber) >= this.sequenceNumberMaximum
                        - this.sequenceNumberWindow) {
            LOGGER.debug("SequenceNumber OK");
        } else {
            LOGGER.debug("SequenceNumber NOT OK");
            throw new ProtocolAdapterException("SequenceNumber incorrect");
        }
    }
}

/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.processors;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceMessageStatus;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.DeviceResponseHandler;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.ResumeScheduleDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.requests.SetLightDeviceRequest;
import com.alliander.osgp.adapter.protocol.oslp.elster.device.responses.EmptyDeviceResponse;
import com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.DeviceRequestMessageProcessor;
import com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.DeviceRequestMessageType;
import com.alliander.osgp.adapter.protocol.oslp.elster.infra.messaging.OslpEnvelopeProcessor;
import com.alliander.osgp.dto.valueobjects.LightValueMessageDataContainerDto;
import com.alliander.osgp.dto.valueobjects.ResumeScheduleMessageDataContainerDto;
import com.alliander.osgp.oslp.OslpEnvelope;
import com.alliander.osgp.oslp.SignedOslpEnvelopeDto;
import com.alliander.osgp.oslp.UnsignedOslpEnvelopeDto;
import com.alliander.osgp.shared.infra.jms.Constants;

/**
 * Class for processing public lighting set light request messages
 */
@Component("oslpPublicLightingSetLightRequestMessageProcessor")
public class PublicLightingSetLightRequestMessageProcessor extends DeviceRequestMessageProcessor implements
        OslpEnvelopeProcessor {
    /**
     * Logger for this class
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicLightingSetLightRequestMessageProcessor.class);

    @Autowired
    private PublicLightingResumeScheduleRequestMessageProcessor publicLightingResumeScheduleRequestMessageProcessor;

    public PublicLightingSetLightRequestMessageProcessor() {
        super(DeviceRequestMessageType.SET_LIGHT);
    }

    @Override
    public void processMessage(final ObjectMessage message) {
        LOGGER.debug("Processing public lighting set light request message");

        String correlationUid = null;
        String domain = null;
        String domainVersion = null;
        String messageType = null;
        String organisationIdentification = null;
        String deviceIdentification = null;
        String ipAddress = null;
        int retryCount = 0;
        boolean isScheduled = false;

        try {
            correlationUid = message.getJMSCorrelationID();
            domain = message.getStringProperty(Constants.DOMAIN);
            domainVersion = message.getStringProperty(Constants.DOMAIN_VERSION);
            messageType = message.getJMSType();
            organisationIdentification = message.getStringProperty(Constants.ORGANISATION_IDENTIFICATION);
            deviceIdentification = message.getStringProperty(Constants.DEVICE_IDENTIFICATION);
            ipAddress = message.getStringProperty(Constants.IP_ADDRESS);
            retryCount = message.getIntProperty(Constants.RETRY_COUNT);
            isScheduled = message.propertyExists(Constants.IS_SCHEDULED) ? message
                    .getBooleanProperty(Constants.IS_SCHEDULED) : false;
        } catch (final JMSException e) {
            LOGGER.error("UNRECOVERABLE ERROR, unable to read ObjectMessage instance, giving up.", e);
            LOGGER.debug("correlationUid: {}", correlationUid);
            LOGGER.debug("domain: {}", domain);
            LOGGER.debug("domainVersion: {}", domainVersion);
            LOGGER.debug("messageType: {}", messageType);
            LOGGER.debug("organisationIdentification: {}", organisationIdentification);
            LOGGER.debug("deviceIdentification: {}", deviceIdentification);
            LOGGER.debug("ipAddress: {}", ipAddress);
            return;
        }

        try {
            final LightValueMessageDataContainerDto lightValueMessageDataContainer = (LightValueMessageDataContainerDto) message
                    .getObject();

            LOGGER.info("Calling DeviceService function: {} for domain: {} {}", messageType, domain, domainVersion);

            final SetLightDeviceRequest deviceRequest = new SetLightDeviceRequest(organisationIdentification,
                    deviceIdentification, correlationUid, lightValueMessageDataContainer, domain, domainVersion,
                    messageType, ipAddress, retryCount, isScheduled);

            this.deviceService.setLight(deviceRequest);
        } catch (final Exception e) {
            this.handleError(e, correlationUid, organisationIdentification, deviceIdentification, domain,
                    domainVersion, messageType, retryCount);
        }
    }

    @Override
    public void processSignedOslpEnvelope(final String deviceIdentification,
            final SignedOslpEnvelopeDto signedOslpEnvelopeDto) {

        final UnsignedOslpEnvelopeDto unsignedOslpEnvelopeDto = signedOslpEnvelopeDto.getUnsignedOslpEnvelopeDto();
        final OslpEnvelope oslpEnvelope = signedOslpEnvelopeDto.getOslpEnvelope();
        final String correlationUid = unsignedOslpEnvelopeDto.getCorrelationUid();
        final String organisationIdentification = unsignedOslpEnvelopeDto.getOrganisationIdentification();
        final String domain = unsignedOslpEnvelopeDto.getDomain();
        final String domainVersion = unsignedOslpEnvelopeDto.getDomainVersion();
        final String messageType = unsignedOslpEnvelopeDto.getMessageType();
        final String ipAddress = unsignedOslpEnvelopeDto.getIpAddress();
        final int retryCount = unsignedOslpEnvelopeDto.getRetryCount();
        final boolean isScheduled = unsignedOslpEnvelopeDto.isScheduled();

        final DeviceResponseHandler setLightDeviceResponseHandler = new DeviceResponseHandler() {

            @Override
            public void handleResponse(final DeviceResponse deviceResponse) {
                if (((EmptyDeviceResponse) deviceResponse).getStatus().equals(DeviceMessageStatus.OK)) {
                    // If the response is OK, just log it. The resumeSchedule()
                    // function will be called next.
                    LOGGER.info("setLight() successful for device : {}", deviceResponse.getDeviceIdentification());
                } else {
                    // If the response is not OK, send a response message to the
                    // responses queue.
                    PublicLightingSetLightRequestMessageProcessor.this.handleEmptyDeviceResponse(deviceResponse,
                            PublicLightingSetLightRequestMessageProcessor.this.responseMessageSender, domain,
                            domainVersion, messageType, retryCount);
                }
            }

            @Override
            public void handleException(final Throwable t, final DeviceResponse deviceResponse) {
                PublicLightingSetLightRequestMessageProcessor.this.handleUnableToConnectDeviceResponse(deviceResponse,
                        t, unsignedOslpEnvelopeDto.getExtraData(),
                        PublicLightingSetLightRequestMessageProcessor.this.responseMessageSender, deviceResponse,
                        domain, domainVersion, messageType, isScheduled, retryCount);
            }
        };

        final DeviceRequest setLightdeviceRequest = new DeviceRequest(organisationIdentification, deviceIdentification,
                correlationUid, domain, domainVersion, messageType, ipAddress, retryCount, isScheduled);

        // Execute a ResumeSchedule call with 'immediate = false' and 'index
        // = 0' as arguments.
        final ResumeScheduleMessageDataContainerDto resumeScheduleMessageDataContainer = new ResumeScheduleMessageDataContainerDto(
                0, false);

        final DeviceResponseHandler resumeScheduleDeviceResponseHandler = this.publicLightingResumeScheduleRequestMessageProcessor
                .createResumeScheduleDeviceResponseHandler(domain, domainVersion,
                        DeviceRequestMessageType.RESUME_SCHEDULE.name(), retryCount,
                        resumeScheduleMessageDataContainer, isScheduled);

        // The data of the setLightdeviceRequest can be reused
        final ResumeScheduleDeviceRequest resumeScheduleDeviceRequest = new ResumeScheduleDeviceRequest(
                setLightdeviceRequest.getOrganisationIdentification(), setLightdeviceRequest.getDeviceIdentification(),
                setLightdeviceRequest.getCorrelationUid(), resumeScheduleMessageDataContainer,
                setLightdeviceRequest.getDomain(), setLightdeviceRequest.getDomainVersion(),
                DeviceRequestMessageType.RESUME_SCHEDULE.name(), setLightdeviceRequest.getIpAddress(),
                setLightdeviceRequest.getRetryCount(), setLightdeviceRequest.isScheduled());

        try {
            this.deviceService.doSetLight(oslpEnvelope, setLightdeviceRequest, resumeScheduleDeviceRequest,
                    setLightDeviceResponseHandler, resumeScheduleDeviceResponseHandler, ipAddress);
        } catch (final IOException e) {
            this.handleError(e, correlationUid, organisationIdentification, deviceIdentification, domain,
                    domainVersion, messageType, retryCount);
        }
    }
}

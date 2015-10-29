package com.alliander.osgp.webdevicesimulator.application.tasks;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alliander.osgp.oslp.Oslp;
import com.alliander.osgp.webdevicesimulator.application.services.DeviceManagementService;
import com.alliander.osgp.webdevicesimulator.domain.entities.DeviceMessageStatus;
import com.alliander.osgp.webdevicesimulator.domain.valueobjects.EventNotificationToBeSent;
import com.alliander.osgp.webdevicesimulator.service.RegisterDevice;

@Component
public class EventNotificationTransition implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventNotificationTransition.class);

    @Autowired
    private DeviceManagementService deviceManagementService;

    @Autowired
    private RegisterDevice registerDevice;

    @Override
    public void run() {

        if (this.deviceManagementService.getEventNotification()) {
            // The original list with listofeventtobesent
            final List<EventNotificationToBeSent> listeventNotificationToBeSent = this.deviceManagementService
                    .getEventNotificationToBeSent();

            // The local list of events
            final List<EventNotificationToBeSent> listOfEvents = new ArrayList<>();

            // add content of the original list into the local list of events
            listOfEvents.addAll(listeventNotificationToBeSent);

            // run through the list of events and send each
            for (final EventNotificationToBeSent event : listOfEvents) {

                DeviceMessageStatus status;

                if (event.getLightOn()) {
                    // Send EventNotifications for Light Transition ON
                    LOGGER.info("Sending LIGHT_EVENTS_LIGHT_ON_VALUE event for device : {}: {} ", event.getdeviceId());
                    status = this.registerDevice.sendEventNotificationCommand(event.getdeviceId(),
                            Oslp.Event.LIGHT_EVENTS_LIGHT_ON_VALUE,
                            "LIGHT_EVENTS_LIGHT_ON_VALUE event occurred on Light Switching on ", null);

                } else {
                    // Send EventNotifications for Light Transition OFF
                    LOGGER.info("Sending LIGHT_EVENTS_LIGHT_OFF_VALUE event for device : {}: {} ", event.getdeviceId());
                    status = this.registerDevice.sendEventNotificationCommand(event.getdeviceId(),
                            Oslp.Event.LIGHT_EVENTS_LIGHT_OFF_VALUE,
                            "LIGHT_EVENTS_LIGHT_OFF_VALUE event occurred on light Switching off ", null);
                }

                // when the event notification is sent successfully. Remove the
                // event from original list. If there are multiple event for a
                // same
                // device then this doesnt work.
                if (status == DeviceMessageStatus.OK) {
                    listeventNotificationToBeSent.remove(event);
                }

            }
            // The local list of events
            listOfEvents.clear();
        }
    }
}
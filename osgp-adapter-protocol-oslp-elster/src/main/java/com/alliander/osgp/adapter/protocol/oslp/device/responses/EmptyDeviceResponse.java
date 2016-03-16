/**
 * Copyright 2015 Smart Society Services B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.alliander.osgp.adapter.protocol.oslp.device.responses;

import com.alliander.osgp.adapter.protocol.oslp.device.DeviceMessageStatus;
import com.alliander.osgp.adapter.protocol.oslp.device.DeviceResponse;

public class EmptyDeviceResponse extends DeviceResponse {

    private DeviceMessageStatus status;

    public EmptyDeviceResponse(final String organisationIdentification, final String deviceIdentification,
            final String correlationUid, final DeviceMessageStatus status) {
        super(organisationIdentification, deviceIdentification, correlationUid);
        this.status = status;
    }

    public DeviceMessageStatus getStatus() {
        return this.status;
    }
}
